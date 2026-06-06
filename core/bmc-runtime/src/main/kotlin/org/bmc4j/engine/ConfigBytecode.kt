package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.TreeMap
import java.util.function.Function
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Pins `Bmc.*FromEnv("KEY")` / `Bmc.*FromProperty("KEY")` to the **real** value the
 * proof run was launched with. This pass runs in the test JVM (which has the actual environment), so
 * for each such call with a *literal* key it resolves `System.getenv`/`getProperty`
 * *now* and rewrites the call site:
 *
 * - variable set and parseable → replace the call with the concrete value (pop the key, push the
 *   constant), so the analysis sees the actual config;
 * - variable unset / unparseable → redirect to a [ConfigSupport] thrower, so the proof
 *   fails with "required config not set";
 * - non-literal key → left unchanged (falls back to the symbolic `Bmc` body).
 *
 * Both directory and jar entries are mirrored via `ClasspathMirror`, like [StringBytecode]
 *.
 */
object ConfigBytecode {

    private const val BMC = "org/bmc4j/Bmc"
    private const val SUPPORT = "org/bmc4j/engine/ConfigSupport"

    private val CACHE = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Rewrite directory AND jar entries of [classpath], memoized per classpath (race-free).
     *  The real env/properties are read once, on first call, in this (test) JVM. */
    @JvmStatic
    fun rewrite(classpath: String): String =
            CACHE.computeIfAbsent(classpath, ConfigBytecode::doRewrite)

    private fun doRewrite(classpath: String): String =
            ClasspathMirror.mirror(classpath, "config", { b ->
                ClasspathMirror.Transformed(rewriteClass(b))
            })

    /** One config reader: its Bmc method name, descriptor, and how to bake/redirect it. */
    enum class Reader(
            @JvmField val method: String,
            @JvmField val desc: String,
            @JvmField val missing: String,
            @JvmField val lookup: Function<String, String?>) {
        INT_ENV("intFromEnv", "(Ljava/lang/String;)I", "missingInt", Function { System.getenv(it) }),
        INT_PROP("intFromProperty", "(Ljava/lang/String;)I", "missingInt", Function { System.getProperty(it) }),
        LONG_ENV("longFromEnv", "(Ljava/lang/String;)J", "missingLong", Function { System.getenv(it) }),
        LONG_PROP("longFromProperty", "(Ljava/lang/String;)J", "missingLong", Function { System.getProperty(it) }),
        BOOL_ENV("boolFromEnv", "(Ljava/lang/String;)Z", "missingBool", Function { System.getenv(it) }),
        BOOL_PROP("boolFromProperty", "(Ljava/lang/String;)Z", "missingBool", Function { System.getProperty(it) }),
        DOUBLE_ENV("doubleFromEnv", "(Ljava/lang/String;)D", "missingDouble", Function { System.getenv(it) }),
        DOUBLE_PROP("doubleFromProperty", "(Ljava/lang/String;)D", "missingDouble", Function { System.getProperty(it) }),
        STRING_ENV("stringFromEnv", "(Ljava/lang/String;)Ljava/lang/String;", "missingString", Function { System.getenv(it) }),
        STRING_PROP("stringFromProperty", "(Ljava/lang/String;)Ljava/lang/String;", "missingString", Function { System.getProperty(it) });

        companion object {
            @JvmStatic
            fun of(name: String, desc: String): Reader? {
                for (r in values()) {
                    if (r.method == name && r.desc == desc) {
                        return r
                    }
                }
                return null
            }
        }
    }

    /** Sentinel for a key whose env/property is unset or unparseable — i.e. the call would be redirected
     *  to a thrower at bake time. Distinct from any real value so toggling presence also invalidates. */
    @JvmField
    val UNSET = "<unset>"

    /**
     * The value this run would bake for `Bmc.<method><desc>("key")` — the verdict-relevant config
     * input the cache must fold in. Uses the SAME reader lookup + [parse] as the rewrite so the
     * cache key and the baked bytecode never diverge: a set+parseable value yields its canonical string
     * form (the literal that gets baked), an unset/unparseable value yields [UNSET] (the call
     * redirects to the thrower). Returns `null` when [method]+[desc] isn't a config
     * reader (the caller skips it).
     */
    @JvmStatic
    fun resolvedValue(method: String, desc: String, key: String): String? {
        val r = Reader.of(method, desc) ?: return null
        val value = parse(r, r.lookup.apply(key))
        return if (value == null) UNSET else value.toString()
    }

    /**
     * The verdict-relevant config inputs reachable on [classpath]: scan every `.class`
     * (directory entries recursed, jar/zip entries) for `INVOKESTATIC org/bmc4j/Bmc.*From*` call
     * sites with a *literal* String key — the exact call sites [rewriteClass] bakes — and
     * resolve each to the value this run would bake (via [resolvedValue], the same reader logic).
     *
     * Returns a deterministic, newline-joined list of `reader KEY=value` lines, sorted, so the
     * verdict cache can fold the resolved config into its key: change a referenced env/property value and
     * the line changes; toggle a key's presence and it flips to/from `<unset>`. Non-literal keys
     * (symbolic fallback in `Bmc`) are left out — they aren't baked, so they don't pin a value.
     *
     * Fail-open per entry: an unreadable/malformed class or jar contributes nothing rather than
     * throwing, so a scan hiccup can never produce a wrong key here (the verdict cache already fails
     * open to a re-run). Empty when the classpath references no literal-keyed config readers.
     */
    @JvmStatic
    fun resolvedConfig(classpath: String): String =
            resolveSites(scanCallSites(classpath))

    /**
     * The literal-keyed config-reader call sites reachable on [classpath], each as a
     * `{method, desc, key}` triple, sorted by `"reader KEY"` and de-duplicated. This is the
     * *scan* half of [resolvedConfig] — a pure function of the classpath's bytecode
     * **content** (it never reads an env var or property), so callers may memoize it against the
     * classpath and re-resolve the values cheaply per call (see `VerdictCache`). Fail-open per
     * entry, like the resolve: an unreadable container contributes nothing.
     */
    @JvmStatic
    fun scanCallSites(classpath: String?): List<Array<String>> {
        if (classpath == null || classpath.isBlank()) {
            return listOf()
        }
        // Keyed by "reader KEY" so two reader types on the same env/property key (which can parse to
        // different values) stay distinct. TreeMap => sorted, order-independent across classpath
        // entries and call-site order.
        val sites = TreeMap<String, Array<String>>()
        val entries = classpath.split(java.io.File.pathSeparator)
        val sorted = ArrayList(entries)
        java.util.Collections.sort(sorted)
        for (e in sorted) {
            if (e == null || e.isBlank()) {
                continue
            }
            val p = Path.of(e)
            try {
                if (Files.isDirectory(p)) {
                    scanDir(p, sites)
                } else if (Files.isRegularFile(p) && isClassContainer(p)) {
                    scanJar(p, sites)
                }
            } catch (ex: IOException) {
                // fail-open per entry: skip an unreadable container (cache fails open to a re-run anyway)
            } catch (ex: RuntimeException) {
                // fail-open per entry: skip an unreadable container (cache fails open to a re-run anyway)
            }
        }
        return java.util.List.copyOf(sites.values)
    }

    /**
     * The *resolve* half of [resolvedConfig]: each scanned call site mapped to the value
     * this run would bake (via [resolvedValue], the same reader logic as the rewrite), rendered
     * as sorted `reader KEY=value` lines. Reads the CURRENT env/property values — deliberately
     * not memoizable, so a config flip between calls still changes the result for unchanged bytecode.
     */
    @JvmStatic
    fun resolveSites(sites: List<Array<String>>): String {
        val resolved = TreeMap<String, String>()
        for (s in sites) {
            val value = resolvedValue(s[0], s[1], s[2])
            if (value != null) {
                resolved[s[0] + ' ' + s[2]] = value
            }
        }
        val sb = StringBuilder()
        for (en in resolved.entries) {
            sb.append(en.key).append('=').append(en.value).append('\n')
        }
        return sb.toString()
    }

    private fun isClassContainer(p: Path): Boolean {
        val name = p.fileName.toString().lowercase(Locale.ROOT)
        return name.endsWith(".jar") || name.endsWith(".zip")
    }

    private fun scanDir(dir: Path, out: TreeMap<String, Array<String>>) {
        Files.walk(dir).use { walk ->
            for (c in Iterable { walk.iterator() }) {
                if (Files.isRegularFile(c) && c.fileName.toString().endsWith(".class")) {
                    try {
                        scanClass(Files.readAllBytes(c), out)
                    } catch (ignored: IOException) {
                        // fail-open: a malformed class contributes nothing
                    } catch (ignored: RuntimeException) {
                        // fail-open: a malformed class contributes nothing
                    }
                }
            }
        }
    }

    private fun scanJar(jar: Path, out: TreeMap<String, Array<String>>) {
        ZipFile(jar.toFile()).use { zf ->
            val en = zf.entries()
            while (en.hasMoreElements()) {
                val ze = en.nextElement()
                if (ze.isDirectory || !ze.name.endsWith(".class")) {
                    continue
                }
                try {
                    zf.getInputStream(ze).use { input ->
                        scanClass(input.readAllBytes(), out)
                    }
                } catch (ignored: IOException) {
                    // fail-open: a malformed entry contributes nothing
                } catch (ignored: RuntimeException) {
                    // fail-open: a malformed entry contributes nothing
                }
            }
        }
    }

    /** Visit one class's methods, recording each literal-keyed `Bmc.*From*` call site into
     *  [out] as a `{method, desc, key}` triple (keyed by "reader KEY"). Mirrors
     *  [ConfigMethodVisitor]'s literal-key tracking so the scan sees exactly the call sites the
     *  rewrite bakes. Records the SITE only — values are resolved later by [resolveSites]. */
    private fun scanClass(bytes: ByteArray, out: TreeMap<String, Array<String>>) {
        val cr = ClassReader(bytes)
        cr.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                return object : MethodVisitor(Opcodes.ASM9) {
                    private var lastKey: String? = null

                    override fun visitLdcInsn(value: Any?) {
                        lastKey = if (value is String) value else null
                    }

                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        val key = lastKey
                        lastKey = null
                        if (op == Opcodes.INVOKESTATIC && BMC == owner && key != null
                                && Reader.of(name!!, desc!!) != null) {
                            out[name + ' ' + key] = arrayOf(name, desc, key)
                        }
                    }

                    // Any other instruction clears the literal-key marker (same as the rewrite visitor).
                    override fun visitInsn(o: Int) { lastKey = null }
                    override fun visitIntInsn(o: Int, x: Int) { lastKey = null }
                    override fun visitVarInsn(o: Int, x: Int) { lastKey = null }
                    override fun visitTypeInsn(o: Int, t: String?) { lastKey = null }
                    override fun visitFieldInsn(o: Int, w: String?, n2: String?, d2: String?) { lastKey = null }
                    override fun visitJumpInsn(o: Int, l: Label?) { lastKey = null }
                    override fun visitIincInsn(v: Int, i: Int) { lastKey = null }
                    override fun visitInvokeDynamicInsn(n2: String?, d2: String?, h: Handle?, vararg a2: Any?) { lastKey = null }
                    override fun visitTableSwitchInsn(mn: Int, mx: Int, d2: Label?, vararg ls: Label?) { lastKey = null }
                    override fun visitLookupSwitchInsn(d2: Label?, k: IntArray?, ls: Array<Label>?) { lastKey = null }
                    override fun visitMultiANewArrayInsn(d2: String?, n2: Int) { lastKey = null }
                }
            }
        }, 0)
    }

    @JvmStatic
    @JvmName("rewriteClass") // package-private in Java; Java tests call it
    internal fun rewriteClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(0)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                return ConfigMethodVisitor(mv)
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /** Tracks whether the immediately-preceding instruction was an `ldc` of a String. */
    private class ConfigMethodVisitor(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM9, mv) {
        private var lastKey: String? = null

        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                     desc: String?, itf: Boolean) {
            val r = if (op == Opcodes.INVOKESTATIC && BMC == owner) Reader.of(name!!, desc!!) else null
            val key = lastKey
            lastKey = null
            if (r == null) {
                super.visitMethodInsn(op, owner, name, desc, itf)
                return
            }
            if (key == null) {
                // non-literal key: leave the call (symbolic fallback in Bmc).
                super.visitMethodInsn(op, owner, name, desc, itf)
                return
            }
            val raw = r.lookup.apply(key)
            val value = parse(r, raw)
            if (value == null) {
                // unset/unparseable -> redirect to the thrower (key stays on the stack).
                super.visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT, r.missing, r.desc, false)
                return
            }
            super.visitInsn(Opcodes.POP) // drop the key string
            pushConstant(r, value)
        }

        private fun pushConstant(r: Reader, value: Any) {
            when (r) {
                Reader.BOOL_ENV, Reader.BOOL_PROP ->
                    super.visitInsn(if (value as Boolean) Opcodes.ICONST_1 else Opcodes.ICONST_0)
                else ->
                    super.visitLdcInsn(value) // Integer/Long/Double/String handled by ASM
            }
        }

        // ldc of a String marks a candidate literal key; anything else clears the marker.
        override fun visitLdcInsn(value: Any?) {
            super.visitLdcInsn(value)
            lastKey = if (value is String) value else null
        }

        override fun visitInsn(o: Int) { lastKey = null; super.visitInsn(o) }
        override fun visitIntInsn(o: Int, x: Int) { lastKey = null; super.visitIntInsn(o, x) }
        override fun visitVarInsn(o: Int, x: Int) { lastKey = null; super.visitVarInsn(o, x) }
        override fun visitTypeInsn(o: Int, t: String?) { lastKey = null; super.visitTypeInsn(o, t) }
        override fun visitFieldInsn(o: Int, w: String?, n: String?, d: String?) { lastKey = null; super.visitFieldInsn(o, w, n, d) }
        override fun visitJumpInsn(o: Int, l: Label?) { lastKey = null; super.visitJumpInsn(o, l) }
        override fun visitIincInsn(v: Int, i: Int) { lastKey = null; super.visitIincInsn(v, i) }
        override fun visitInvokeDynamicInsn(n: String?, d: String?, h: Handle?, vararg a: Any?) { lastKey = null; super.visitInvokeDynamicInsn(n, d, h, *a) }
        override fun visitTableSwitchInsn(mn: Int, mx: Int, d: Label?, vararg ls: Label?) { lastKey = null; super.visitTableSwitchInsn(mn, mx, d, *ls) }
        override fun visitLookupSwitchInsn(d: Label?, k: IntArray?, ls: Array<Label>?) { lastKey = null; super.visitLookupSwitchInsn(d, k, ls) }
        override fun visitMultiANewArrayInsn(d: String?, n: Int) { lastKey = null; super.visitMultiANewArrayInsn(d, n) }
    }

    /** Parse the raw value for the reader's type; null means unset or not parseable. */
    private fun parse(r: Reader, raw: String?): Any? {
        if (raw == null) {
            return null
        }
        val v = raw.trim()
        try {
            return when (r) {
                Reader.INT_ENV, Reader.INT_PROP ->
                    Integer.valueOf(Integer.parseInt(v))
                Reader.LONG_ENV, Reader.LONG_PROP ->
                    java.lang.Long.valueOf(java.lang.Long.parseLong(v))
                Reader.DOUBLE_ENV, Reader.DOUBLE_PROP ->
                    java.lang.Double.valueOf(java.lang.Double.parseDouble(v))
                Reader.BOOL_ENV, Reader.BOOL_PROP -> {
                    // Strict: only "true"/"false" (case-insensitive). Boolean.parseBoolean would
                    // silently turn anything else ("1", "yes", a typo) into false — the proof would
                    // then verify the WRONG config. Malformed must fail loudly like the numeric readers.
                    if (v.equals("true", ignoreCase = true)) {
                        return java.lang.Boolean.TRUE
                    }
                    if (v.equals("false", ignoreCase = true)) {
                        return java.lang.Boolean.FALSE
                    }
                    null
                }
                else ->
                    raw // STRING: exact value (not trimmed)
            }
        } catch (e: NumberFormatException) {
            return null
        }
    }
}
