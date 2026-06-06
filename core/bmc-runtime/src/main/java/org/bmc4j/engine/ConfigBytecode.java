package org.bmc4j.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Pins {@code Bmc.*FromEnv("KEY")} / {@code Bmc.*FromProperty("KEY")} to the <b>real</b> value the
 * proof run was launched with. This pass runs in the test JVM (which has the actual environment), so
 * for each such call with a <em>literal</em> key it resolves {@code System.getenv}/{@code getProperty}
 * <em>now</em> and rewrites the call site:
 *
 * <ul>
 *   <li>variable set and parseable → replace the call with the concrete value (pop the key, push the
 *       constant), so the analysis sees the actual config;</li>
 *   <li>variable unset / unparseable → redirect to a {@link ConfigSupport} thrower, so the proof
 *       fails with "required config not set";</li>
 *   <li>non-literal key → left unchanged (falls back to the symbolic {@code Bmc} body).</li>
 * </ul>
 *
 * Both directory and jar entries are mirrored via {@code ClasspathMirror}, like {@link StringBytecode}
 *.
 */
public final class ConfigBytecode {

    private static final String BMC = "org/bmc4j/Bmc";
    private static final String SUPPORT = "org/bmc4j/engine/ConfigSupport";

    private ConfigBytecode() {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Rewrite directory AND jar entries of {@code classpath}, memoized per classpath (race-free).
     *  The real env/properties are read once, on first call, in this (test) JVM. */
    public static String rewrite(String classpath) {
        return CACHE.computeIfAbsent(classpath, ConfigBytecode::doRewrite);
    }

    private static String doRewrite(String classpath) {
        return ClasspathMirror.mirror(classpath, "config",
                b -> new ClasspathMirror.Transformed(rewriteClass(b)));
    }

    /** One config reader: its Bmc method name, descriptor, and how to bake/redirect it. */
    enum Reader {
        INT_ENV("intFromEnv", "(Ljava/lang/String;)I", "missingInt", System::getenv),
        INT_PROP("intFromProperty", "(Ljava/lang/String;)I", "missingInt", System::getProperty),
        LONG_ENV("longFromEnv", "(Ljava/lang/String;)J", "missingLong", System::getenv),
        LONG_PROP("longFromProperty", "(Ljava/lang/String;)J", "missingLong", System::getProperty),
        BOOL_ENV("boolFromEnv", "(Ljava/lang/String;)Z", "missingBool", System::getenv),
        BOOL_PROP("boolFromProperty", "(Ljava/lang/String;)Z", "missingBool", System::getProperty),
        DOUBLE_ENV("doubleFromEnv", "(Ljava/lang/String;)D", "missingDouble", System::getenv),
        DOUBLE_PROP("doubleFromProperty", "(Ljava/lang/String;)D", "missingDouble", System::getProperty),
        STRING_ENV("stringFromEnv", "(Ljava/lang/String;)Ljava/lang/String;", "missingString", System::getenv),
        STRING_PROP("stringFromProperty", "(Ljava/lang/String;)Ljava/lang/String;", "missingString", System::getProperty);

        final String method;
        final String desc;
        final String missing;
        final Function<String, String> lookup;

        Reader(String method, String desc, String missing, Function<String, String> lookup) {
            this.method = method;
            this.desc = desc;
            this.missing = missing;
            this.lookup = lookup;
        }

        static Reader of(String name, String desc) {
            for (Reader r : values()) {
                if (r.method.equals(name) && r.desc.equals(desc)) {
                    return r;
                }
            }
            return null;
        }
    }

    /** Sentinel for a key whose env/property is unset or unparseable — i.e. the call would be redirected
     *  to a thrower at bake time. Distinct from any real value so toggling presence also invalidates. */
    static final String UNSET = "<unset>";

    /**
     * The value this run would bake for {@code Bmc.<method><desc>("key")} — the verdict-relevant config
     * input the cache must fold in. Uses the SAME reader lookup + {@link #parse} as the rewrite so the
     * cache key and the baked bytecode never diverge: a set+parseable value yields its canonical string
     * form (the literal that gets baked), an unset/unparseable value yields {@link #UNSET} (the call
     * redirects to the thrower). Returns {@code null} when {@code method}+{@code desc} isn't a config
     * reader (the caller skips it).
     */
    static String resolvedValue(String method, String desc, String key) {
        Reader r = Reader.of(method, desc);
        if (r == null) {
            return null;
        }
        Object value = parse(r, r.lookup.apply(key));
        return value == null ? UNSET : String.valueOf(value);
    }

    /**
     * The verdict-relevant config inputs reachable on {@code classpath}: scan every {@code .class}
     * (directory entries recursed, jar/zip entries) for {@code INVOKESTATIC org/bmc4j/Bmc.*From*} call
     * sites with a <em>literal</em> String key — the exact call sites {@link #rewriteClass} bakes — and
     * resolve each to the value this run would bake (via {@link #resolvedValue}, the same reader logic).
     *
     * <p>Returns a deterministic, newline-joined list of {@code reader KEY=value} lines, sorted, so the
     * verdict cache can fold the resolved config into its key: change a referenced env/property value and
     * the line changes; toggle a key's presence and it flips to/from {@code <unset>}. Non-literal keys
     * (symbolic fallback in {@code Bmc}) are left out — they aren't baked, so they don't pin a value.
     *
     * <p>Fail-open per entry: an unreadable/malformed class or jar contributes nothing rather than
     * throwing, so a scan hiccup can never produce a wrong key here (the verdict cache already fails
     * open to a re-run). Empty when the classpath references no literal-keyed config readers.
     */
    static String resolvedConfig(String classpath) {
        return resolveSites(scanCallSites(classpath));
    }

    /**
     * The literal-keyed config-reader call sites reachable on {@code classpath}, each as a
     * {@code {method, desc, key}} triple, sorted by {@code "reader KEY"} and de-duplicated. This is the
     * <em>scan</em> half of {@link #resolvedConfig} — a pure function of the classpath's bytecode
     * <b>content</b> (it never reads an env var or property), so callers may memoize it against the
     * classpath and re-resolve the values cheaply per call (see {@code VerdictCache}). Fail-open per
     * entry, like the resolve: an unreadable container contributes nothing.
     */
    static List<String[]> scanCallSites(String classpath) {
        if (classpath == null || classpath.isBlank()) {
            return List.of();
        }
        // Keyed by "reader KEY" so two reader types on the same env/property key (which can parse to
        // different values) stay distinct. TreeMap => sorted, order-independent across classpath
        // entries and call-site order.
        TreeMap<String, String[]> sites = new TreeMap<>();
        String[] entries = classpath.split(java.io.File.pathSeparator);
        List<String> sorted = new ArrayList<>(List.of(entries));
        java.util.Collections.sort(sorted);
        for (String e : sorted) {
            if (e == null || e.isBlank()) {
                continue;
            }
            Path p = Path.of(e);
            try {
                if (Files.isDirectory(p)) {
                    scanDir(p, sites);
                } else if (Files.isRegularFile(p) && isClassContainer(p)) {
                    scanJar(p, sites);
                }
            } catch (IOException | RuntimeException ex) {
                // fail-open per entry: skip an unreadable container (cache fails open to a re-run anyway)
            }
        }
        return List.copyOf(sites.values());
    }

    /**
     * The <em>resolve</em> half of {@link #resolvedConfig}: each scanned call site mapped to the value
     * this run would bake (via {@link #resolvedValue}, the same reader logic as the rewrite), rendered
     * as sorted {@code reader KEY=value} lines. Reads the CURRENT env/property values — deliberately
     * not memoizable, so a config flip between calls still changes the result for unchanged bytecode.
     */
    static String resolveSites(List<String[]> sites) {
        TreeMap<String, String> resolved = new TreeMap<>();
        for (String[] s : sites) {
            String value = resolvedValue(s[0], s[1], s[2]);
            if (value != null) {
                resolved.put(s[0] + ' ' + s[2], value);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> en : resolved.entrySet()) {
            sb.append(en.getKey()).append('=').append(en.getValue()).append('\n');
        }
        return sb.toString();
    }

    private static boolean isClassContainer(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private static void scanDir(Path dir, TreeMap<String, String[]> out) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path c : (Iterable<Path>) walk::iterator) {
                if (Files.isRegularFile(c) && c.getFileName().toString().endsWith(".class")) {
                    try {
                        scanClass(Files.readAllBytes(c), out);
                    } catch (IOException | RuntimeException ignored) {
                        // fail-open: a malformed class contributes nothing
                    }
                }
            }
        }
    }

    private static void scanJar(Path jar, TreeMap<String, String[]> out) throws IOException {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze.isDirectory() || !ze.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream is = zf.getInputStream(ze)) {
                    scanClass(is.readAllBytes(), out);
                } catch (IOException | RuntimeException ignored) {
                    // fail-open: a malformed entry contributes nothing
                }
            }
        }
    }

    /** Visit one class's methods, recording each literal-keyed {@code Bmc.*From*} call site into
     *  {@code out} as a {@code {method, desc, key}} triple (keyed by "reader KEY"). Mirrors
     *  {@link ConfigMethodVisitor}'s literal-key tracking so the scan sees exactly the call sites the
     *  rewrite bakes. Records the SITE only — values are resolved later by {@link #resolveSites}. */
    private static void scanClass(byte[] bytes, TreeMap<String, String[]> out) {
        ClassReader cr = new ClassReader(bytes);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private String lastKey;

                    @Override
                    public void visitLdcInsn(Object value) {
                        lastKey = (value instanceof String) ? (String) value : null;
                    }

                    @Override
                    public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        String key = lastKey;
                        lastKey = null;
                        if (op == Opcodes.INVOKESTATIC && BMC.equals(owner) && key != null
                                && Reader.of(name, desc) != null) {
                            out.put(name + ' ' + key, new String[]{name, desc, key});
                        }
                    }

                    // Any other instruction clears the literal-key marker (same as the rewrite visitor).
                    @Override public void visitInsn(int o) { lastKey = null; }
                    @Override public void visitIntInsn(int o, int x) { lastKey = null; }
                    @Override public void visitVarInsn(int o, int x) { lastKey = null; }
                    @Override public void visitTypeInsn(int o, String t) { lastKey = null; }
                    @Override public void visitFieldInsn(int o, String w, String n2, String d2) { lastKey = null; }
                    @Override public void visitJumpInsn(int o, Label l) { lastKey = null; }
                    @Override public void visitIincInsn(int v, int i) { lastKey = null; }
                    @Override public void visitInvokeDynamicInsn(String n2, String d2, Handle h, Object... a2) { lastKey = null; }
                    @Override public void visitTableSwitchInsn(int mn, int mx, Label d2, Label... ls) { lastKey = null; }
                    @Override public void visitLookupSwitchInsn(Label d2, int[] k, Label[] ls) { lastKey = null; }
                    @Override public void visitMultiANewArrayInsn(String d2, int n2) { lastKey = null; }
                };
            }
        }, 0);
    }

    static byte[] rewriteClass(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                MethodVisitor mv = super.visitMethod(a, n, d, s, ex);
                return new ConfigMethodVisitor(mv);
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    /** Tracks whether the immediately-preceding instruction was an {@code ldc} of a String. */
    private static final class ConfigMethodVisitor extends MethodVisitor {
        private String lastKey;

        ConfigMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            Reader r = (op == Opcodes.INVOKESTATIC && BMC.equals(owner)) ? Reader.of(name, desc) : null;
            String key = lastKey;
            lastKey = null;
            if (r == null) {
                super.visitMethodInsn(op, owner, name, desc, itf);
                return;
            }
            if (key == null) {
                // non-literal key: leave the call (symbolic fallback in Bmc).
                super.visitMethodInsn(op, owner, name, desc, itf);
                return;
            }
            String raw = r.lookup.apply(key);
            Object value = parse(r, raw);
            if (value == null) {
                // unset/unparseable -> redirect to the thrower (key stays on the stack).
                super.visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT, r.missing, r.desc, false);
                return;
            }
            super.visitInsn(Opcodes.POP); // drop the key string
            pushConstant(r, value);
        }

        private void pushConstant(Reader r, Object value) {
            switch (r) {
                case BOOL_ENV:
                case BOOL_PROP:
                    super.visitInsn(((Boolean) value) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                    break;
                default:
                    super.visitLdcInsn(value); // Integer/Long/Double/String handled by ASM
            }
        }

        // ldc of a String marks a candidate literal key; anything else clears the marker.
        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(value);
            lastKey = (value instanceof String) ? (String) value : null;
        }

        @Override public void visitInsn(int o) { lastKey = null; super.visitInsn(o); }
        @Override public void visitIntInsn(int o, int x) { lastKey = null; super.visitIntInsn(o, x); }
        @Override public void visitVarInsn(int o, int x) { lastKey = null; super.visitVarInsn(o, x); }
        @Override public void visitTypeInsn(int o, String t) { lastKey = null; super.visitTypeInsn(o, t); }
        @Override public void visitFieldInsn(int o, String w, String n, String d) { lastKey = null; super.visitFieldInsn(o, w, n, d); }
        @Override public void visitJumpInsn(int o, Label l) { lastKey = null; super.visitJumpInsn(o, l); }
        @Override public void visitIincInsn(int v, int i) { lastKey = null; super.visitIincInsn(v, i); }
        @Override public void visitInvokeDynamicInsn(String n, String d, Handle h, Object... a) { lastKey = null; super.visitInvokeDynamicInsn(n, d, h, a); }
        @Override public void visitTableSwitchInsn(int mn, int mx, Label d, Label... ls) { lastKey = null; super.visitTableSwitchInsn(mn, mx, d, ls); }
        @Override public void visitLookupSwitchInsn(Label d, int[] k, Label[] ls) { lastKey = null; super.visitLookupSwitchInsn(d, k, ls); }
        @Override public void visitMultiANewArrayInsn(String d, int n) { lastKey = null; super.visitMultiANewArrayInsn(d, n); }
    }

    /** Parse the raw value for the reader's type; null means unset or not parseable. */
    private static Object parse(Reader r, String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        try {
            switch (r) {
                case INT_ENV:
                case INT_PROP:
                    return Integer.valueOf(Integer.parseInt(v));
                case LONG_ENV:
                case LONG_PROP:
                    return Long.valueOf(Long.parseLong(v));
                case DOUBLE_ENV:
                case DOUBLE_PROP:
                    return Double.valueOf(Double.parseDouble(v));
                case BOOL_ENV:
                case BOOL_PROP:
                    // Strict: only "true"/"false" (case-insensitive). Boolean.parseBoolean would
                    // silently turn anything else ("1", "yes", a typo) into false — the proof would
                    // then verify the WRONG config. Malformed must fail loudly like the numeric readers.
                    if (v.equalsIgnoreCase("true")) {
                        return Boolean.TRUE;
                    }
                    if (v.equalsIgnoreCase("false")) {
                        return Boolean.FALSE;
                    }
                    return null;
                default:
                    return raw; // STRING: exact value (not trimmed)
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
