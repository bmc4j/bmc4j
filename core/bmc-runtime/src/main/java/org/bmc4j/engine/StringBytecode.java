package org.bmc4j.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Redirects JVM constructs that JBMC models unsoundly to sound stand-ins, by rewriting their
 * sites in the analysed bytecode. Two transforms today:
 *
 * <ul>
 *   <li><b>String content ops</b> — {@code String.equals/startsWith/endsWith/contains} →
 *       {@link BmcStrings} (the receiver becomes the first argument, so the operand stack is
 *       unchanged). JBMC's own {@code String.equals} is unsound (it can't even prove
 *       {@code "x".equals("x")}); {@link BmcStrings} rebuilds these from {@code length()} +
 *       {@code charAt} which JBMC <em>does</em> model soundly. <b>Object-typed {@code equals}</b>
 *       call sites — {@code INVOKEVIRTUAL java/lang/Object.equals} and {@code INVOKEINTERFACE
 *       .../equals}, which the collection models emit for {@code key.equals(...)} /
 *       {@code o.equals(...)} (static type {@code Object}) — are redirected to
 *       {@link BmcStrings#objEquals(Object, Object)} (issue #18), which routes the String/String case
 *       through the sound shim and otherwise delegates to the receiver's real {@code equals}, so
 *       String-keyed collection lookups become sound without changing any non-String behaviour.</li>
 *   <li><b>String concatenation</b> — the {@code invokedynamic} produced by {@code +} / Kotlin
 *       string templates (bootstrap {@code StringConcatFactory.makeConcat[WithConstants]}) is a
 *       blindspot: JBMC links it to an unconstrained result. We desugar each such site back to the
 *       pre-Java-9 {@code StringBuilder.append(...).toString()} form — which JBMC's string library
 *       <em>does</em> handle soundly — by replacing the {@code invokedynamic} with an
 *       {@code invokestatic} to a synthesized per-class helper of the same descriptor.</li>
 * </ul>
 *
 * <p>Both directory and jar classpath entries are mirrored (with sites rewritten) via
 * {@code ClasspathMirror} — a published consumer gets {@code bmc-models} and third-party libs as
 * jars, and those need the same desugaring as the in-repo class dirs.
 */
public final class StringBytecode {

    private static final String STRING = "java/lang/String";
    private static final String OBJECT = "java/lang/Object";
    private static final String BMC_STRINGS = "org/bmc4j/engine/BmcStrings";

    /** {@code "name desc"} of the {@code Object.equals} call site we redirect to
     *  {@link BmcStrings#objEquals(Object, Object)}. */
    private static final String OBJECT_EQUALS = "equals (Ljava/lang/Object;)Z";
    private static final String STRING_BUILDER = "java/lang/StringBuilder";
    private static final String CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";
    private static final String OBJECT_METHODS = "java/lang/runtime/ObjectMethods";

    // StringConcatFactory recipe tags (see java.lang.invoke.StringConcatFactory). Written as
    // Unicode escapes, not raw control characters, so the source stays plain ASCII (the
    // same hygiene family as the ContractRewriter NUL-byte fix; a raw control char is easy to mangle).
    private static final char TAG_ARG = '\u0001';
    private static final char TAG_CONST = '\u0002';

    /** {@code "name desc"} of the String methods we redirect to {@link BmcStrings}. Each has a
     *  matching {@code BmcStrings} method taking the receiver as an extra first {@code String} arg. */
    private static final java.util.Set<String> REDIRECTS = java.util.Set.of(
            "equals (Ljava/lang/Object;)Z",
            "startsWith (Ljava/lang/String;)Z",
            "endsWith (Ljava/lang/String;)Z",
            "contains (Ljava/lang/CharSequence;)Z");

    private StringBytecode() {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Rewrite directory AND jar entries of {@code classpath}, returning the new classpath. Memoized
     *  per classpath — computed once per worker, which also makes concurrent proofs race-free. */
    public static String rewrite(String classpath) {
        return CACHE.computeIfAbsent(classpath, StringBytecode::doRewrite);
    }

    private static String doRewrite(String classpath) {
        return ClasspathMirror.mirror(classpath, "strings",
                b -> new ClasspathMirror.Transformed(rewriteClass(b)));
    }

    /**
     * True for a call site that dispatches {@code equals(Object)} through a static type whose own
     * {@code equals} JBMC won't intercept as String — i.e. {@code INVOKEVIRTUAL java/lang/Object.equals}
     * (receiver statically typed {@code Object}, as the generic collection models emit) or any
     * {@code INVOKEINTERFACE .../equals(Object)Z} (interfaces that redeclare equals, e.g.
     * {@code java/util/List}/{@code Set}/{@code Map}). {@code String}'s own virtual {@code equals} is
     * handled by the owner-matched String redirect, so it is deliberately excluded here. A virtual
     * call on a concrete non-Object class (e.g. {@code java/lang/Integer.equals}) is left alone: its
     * receiver is never a String, and its modeled {@code equals} is already sound.
     */
    private static boolean isObjectEqualsCallSite(int op, String mOwner, String name, String desc) {
        if (!OBJECT_EQUALS.equals(name + " " + desc)) {
            return false;
        }
        if (op == Opcodes.INVOKEVIRTUAL) {
            return OBJECT.equals(mOwner);
        }
        return op == Opcodes.INVOKEINTERFACE;
    }

    /** A concat {@code invokedynamic} site we replaced with a call to a generated helper. */
    private static final class ConcatHelper {
        final String name;          // generated method name
        final String desc;          // same descriptor as the indy (args...)Ljava/lang/String;
        final String recipe;        // makeConcatWithConstants recipe, or null for makeConcat
        final Object[] constants;   // bootstrap constants consumed by TAG_CONST (may be empty)

        ConcatHelper(String name, String desc, String recipe, Object[] constants) {
            this.name = name;
            this.desc = desc;
            this.recipe = recipe;
            this.constants = constants;
        }
    }

    /** A record-{@code equals} {@code invokedynamic} site we replaced with a generated helper. */
    private static final class RecordEqHelper {
        final String name;          // generated method name
        final String desc;          // same descriptor as the indy: (LRecord;Ljava/lang/Object;)Z
        final List<Handle> getters; // one accessor MethodHandle per record component

        RecordEqHelper(String name, String desc, List<Handle> getters) {
            this.name = name;
            this.desc = desc;
            this.getters = getters;
        }
    }

    /** A record-{@code hashCode} {@code invokedynamic} site we replaced with a generated helper. */
    private static final class RecordHashHelper {
        final String name;          // generated method name
        final String desc;          // same descriptor as the indy: (LRecord;)I
        final List<Handle> getters; // one accessor MethodHandle per record component

        RecordHashHelper(String name, String desc, List<Handle> getters) {
            this.name = name;
            this.desc = desc;
            this.getters = getters;
        }
    }

    /** A record-{@code toString} {@code invokedynamic} site we replaced with a generated helper. */
    private static final class RecordStrHelper {
        final String name;          // generated method name
        final String desc;          // same descriptor as the indy: (LRecord;)Ljava/lang/String;
        final String simpleName;    // record's simple class name, the "Point" in "Point[x=.., y=..]"
        final List<String> compNames; // component names in declaration order
        final List<Handle> getters;   // one accessor MethodHandle per record component

        RecordStrHelper(String name, String desc, String simpleName,
                        List<String> compNames, List<Handle> getters) {
            this.name = name;
            this.desc = desc;
            this.simpleName = simpleName;
            this.compNames = compNames;
            this.getters = getters;
        }
    }

    /** Pure transform: redirect String ops to {@link BmcStrings} and desugar concat indy sites.
     *  Package-private for tests. */
    static byte[] rewriteClass(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        List<ConcatHelper> helpers = new ArrayList<>();
        List<RecordEqHelper> recordEqHelpers = new ArrayList<>();
        List<RecordHashHelper> recordHashHelpers = new ArrayList<>();
        List<RecordStrHelper> recordStrHelpers = new ArrayList<>();
        int[] counter = {0};
        String[] owner = new String[1];
        boolean[] isInterface = {false};

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visit(int version, int access, String name, String sig, String superName, String[] ifs) {
                owner[0] = name;
                isInterface[0] = (access & Opcodes.ACC_INTERFACE) != 0;
                super.visit(version, access, name, sig, superName, ifs);
            }

            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                MethodVisitor mv = super.visitMethod(a, n, d, s, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int op, String mOwner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKEVIRTUAL && STRING.equals(mOwner)
                                && REDIRECTS.contains(name + " " + desc)) {
                            // The receiver becomes the first arg, so the operand stack is unchanged:
                            // desc "(P...)R" -> "(Ljava/lang/String;P...)R".
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, name,
                                    "(Ljava/lang/String;" + desc.substring(1), false);
                        } else if (isObjectEqualsCallSite(op, mOwner, name, desc)
                                && !BMC_STRINGS.equals(owner[0])) {
                            // Soundness hole (issue #18): a call site whose static receiver type is
                            // Object (or an interface that declares equals, e.g. java/util/List) emits
                            // INVOKEVIRTUAL java/lang/Object.equals / INVOKEINTERFACE .../equals, which
                            // String.equals's owner-matched redirect above never touches. The collection
                            // models compare keys/elements exactly this way (HashMap.indexOfKey,
                            // HashSet.indexOf, ArrayList.indexOf use `key.equals(keys[i])` with key typed
                            // Object), so String-keyed lookups dispatched into JBMC's unsound native
                            // String.equals. Redirect to BmcStrings.objEquals, whose descriptor is
                            // exactly (Object,Object)Z once the receiver is prepended: it routes the
                            // String/String case through the sound shim and delegates to the receiver's
                            // own equals for everything else (boxed primitives, user classes), so
                            // non-String semantics are preserved. The BmcStrings guard above prevents
                            // rewriting objEquals's own `a.equals(b)` fallback into infinite recursion.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "objEquals",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                        } else {
                            super.visitMethodInsn(op, mOwner, name, desc, itf);
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
                        if (CONCAT_FACTORY.equals(bsm.getOwner())
                                && (name.equals("makeConcat") || name.equals("makeConcatWithConstants"))) {
                            // Replace with invokestatic to a fresh same-descriptor helper; the dynamic
                            // args already on the stack become the helper's arguments unchanged.
                            String hName = "bmc$concat$" + (counter[0]++);
                            String recipe = name.equals("makeConcatWithConstants") ? (String) bsmArgs[0] : null;
                            Object[] consts = name.equals("makeConcatWithConstants")
                                    ? java.util.Arrays.copyOfRange(bsmArgs, 1, bsmArgs.length)
                                    : new Object[0];
                            helpers.add(new ConcatHelper(hName, desc, recipe, consts));
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], hName, desc, isInterface[0]);
                        } else if (OBJECT_METHODS.equals(bsm.getOwner()) && name.equals("equals")) {
                            // Record equals(): replace the ObjectMethods bootstrap with a synthesized
                            // field-by-field comparison. bsmArgs = [recordClass, names, getter MHs...].
                            String hName = "bmc$recordEquals$" + (counter[0]++);
                            List<Handle> getters = new ArrayList<>();
                            for (int i = 2; i < bsmArgs.length; i++) {
                                if (bsmArgs[i] instanceof Handle) {
                                    getters.add((Handle) bsmArgs[i]);
                                }
                            }
                            recordEqHelpers.add(new RecordEqHelper(hName, desc, getters));
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], hName, desc, isInterface[0]);
                        } else if (OBJECT_METHODS.equals(bsm.getOwner()) && name.equals("hashCode")) {
                            // Record hashCode(): replace the ObjectMethods bootstrap with a synthesized
                            // deterministic fold over the components. JBMC otherwise links the indy to an
                            // unconstrained int. bsmArgs = [recordClass, names, getter MHs...].
                            String hName = "bmc$recordHashCode$" + (counter[0]++);
                            List<Handle> getters = new ArrayList<>();
                            for (int i = 2; i < bsmArgs.length; i++) {
                                if (bsmArgs[i] instanceof Handle) {
                                    getters.add((Handle) bsmArgs[i]);
                                }
                            }
                            recordHashHelpers.add(new RecordHashHelper(hName, desc, getters));
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], hName, desc, isInterface[0]);
                        } else if (OBJECT_METHODS.equals(bsm.getOwner()) && name.equals("toString")) {
                            // Record toString(): build the canonical "Name[c1=v1, c2=v2]" with the same
                            // sound StringBuilder machinery the concat desugar uses. bsmArgs =
                            // [recordClass, ";"-joined names, getter MHs...]. We only desugar when EVERY
                            // component renders soundly (primitive or String); otherwise String.valueOf
                            // of a reference component is JBMC-nondet, so we leave the indy untouched
                            // (no silently-wrong desugar) rather than emit an unsound stand-in.
                            List<Handle> getters = new ArrayList<>();
                            for (int i = 2; i < bsmArgs.length; i++) {
                                if (bsmArgs[i] instanceof Handle) {
                                    getters.add((Handle) bsmArgs[i]);
                                }
                            }
                            String namesJoined = bsmArgs.length > 1 ? String.valueOf(bsmArgs[1]) : "";
                            List<String> compNames = namesJoined.isEmpty()
                                    ? java.util.List.of()
                                    : java.util.Arrays.asList(namesJoined.split(";", -1));
                            String simple = simpleName(((Type) bsmArgs[0]).getInternalName());
                            if (allComponentsRenderSoundly(getters)
                                    && compNames.size() == getters.size()) {
                                String hName = "bmc$recordToString$" + (counter[0]++);
                                recordStrHelpers.add(new RecordStrHelper(
                                        hName, desc, simple, compNames, getters));
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], hName, desc, isInterface[0]);
                            } else {
                                super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
                            }
                        } else {
                            super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
                        }
                    }
                };
            }

            @Override
            public void visitEnd() {
                for (ConcatHelper h : helpers) {
                    emitConcatHelper(cw, owner[0], isInterface[0], h);
                }
                for (RecordEqHelper h : recordEqHelpers) {
                    emitRecordEqualsHelper(cw, isInterface[0], h);
                }
                for (RecordHashHelper h : recordHashHelpers) {
                    emitRecordHashCodeHelper(cw, isInterface[0], h);
                }
                for (RecordStrHelper h : recordStrHelpers) {
                    emitRecordToStringHelper(cw, isInterface[0], h);
                }
                super.visitEnd();
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    /**
     * Synthesize {@code static String <name>(<args>)} that builds the concatenation with
     * {@code StringBuilder.append(...)} (sound in JBMC) instead of the {@code invokedynamic}.
     * Recipe literal chars and {@code TAG_CONST} constants collapse into literal append(String)
     * chunks; each {@code TAG_ARG} appends the next parameter via its typed overload.
     */
    private static void emitConcatHelper(ClassWriter cw, String owner, boolean isInterface, ConcatHelper h) {
        int access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
        if (isInterface) {
            access |= Opcodes.ACC_PUBLIC; // private static interface methods are fine, but keep callable
            access &= ~Opcodes.ACC_PRIVATE;
        }
        MethodVisitor mv = cw.visitMethod(access, h.name, h.desc, null, null);
        mv.visitCode();

        Type[] params = Type.getArgumentTypes(h.desc);
        int[] slot = new int[params.length];
        int running = 0;
        boolean wide = false;
        for (int i = 0; i < params.length; i++) {
            slot[i] = running;
            running += params[i].getSize();
            if (params[i].getSize() == 2) {
                wide = true;
            }
        }

        mv.visitTypeInsn(Opcodes.NEW, STRING_BUILDER);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, STRING_BUILDER, "<init>", "()V", false);

        StringBuilder lit = new StringBuilder();
        int argIdx = 0;
        int constIdx = 0;
        if (h.recipe == null) {
            // makeConcat: every parameter, in order, no literals.
            for (int i = 0; i < params.length; i++) {
                appendArg(mv, params[i], slot[i]);
            }
        } else {
            for (int i = 0; i < h.recipe.length(); i++) {
                char c = h.recipe.charAt(i);
                if (c == TAG_ARG) {
                    flushLiteral(mv, lit);
                    appendArg(mv, params[argIdx], slot[argIdx]);
                    argIdx++;
                } else if (c == TAG_CONST) {
                    lit.append(String.valueOf(h.constants[constIdx++]));
                } else {
                    lit.append(c);
                }
            }
            flushLiteral(mv, lit);
        }

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "toString", "()Ljava/lang/String;", false);
        mv.visitInsn(Opcodes.ARETURN);
        // SB ref (1) on the stack while pushing the widest arg (1 or 2); new/dup peak is 2.
        mv.visitMaxs(wide ? 3 : 2, Math.max(running, 1));
        mv.visitEnd();
    }

    /**
     * Synthesize {@code static boolean <name>(Record this, Object o)} that compares a record
     * field-by-field, replacing the {@code ObjectMethods} bootstrap that JBMC links to an
     * unconstrained result. Mirrors the generated record {@code equals}: {@code o} must be the same
     * record type, then every component must match (primitives by value/{@code compare},
     * references via {@link BmcStrings#objEquals} so String components stay sound). Has a single
     * branch target ({@code FALSE}) reached with an empty stack, so one explicit frame suffices.
     */
    private static void emitRecordEqualsHelper(ClassWriter cw, boolean isInterface, RecordEqHelper h) {
        int access = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC
                | (isInterface ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE);
        MethodVisitor mv = cw.visitMethod(access, h.name, h.desc, null, null);
        mv.visitCode();

        String recordType = Type.getArgumentTypes(h.desc)[0].getInternalName(); // slot 0 = this
        org.objectweb.asm.Label falseLabel = new org.objectweb.asm.Label();
        boolean wide = false;

        // if (!(o instanceof Record)) return false;
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.INSTANCEOF, recordType);
        mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);

        for (Handle g : h.getters) {
            // this.<component>
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            Type rt = loadComponent(mv, g);
            // ((Record) o).<component>
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.CHECKCAST, recordType);
            loadComponent(mv, g);
            switch (rt.getSort()) {
                case Type.BOOLEAN: case Type.CHAR: case Type.BYTE: case Type.SHORT: case Type.INT:
                    mv.visitJumpInsn(Opcodes.IF_ICMPNE, falseLabel);
                    break;
                case Type.LONG:
                    wide = true;
                    mv.visitInsn(Opcodes.LCMP);
                    mv.visitJumpInsn(Opcodes.IFNE, falseLabel);
                    break;
                case Type.FLOAT:
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "compare", "(FF)I", false);
                    mv.visitJumpInsn(Opcodes.IFNE, falseLabel);
                    break;
                case Type.DOUBLE:
                    wide = true;
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "compare", "(DD)I", false);
                    mv.visitJumpInsn(Opcodes.IFNE, falseLabel);
                    break;
                default: // reference / array
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "objEquals",
                            "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                    mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
            }
        }

        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(falseLabel);
        mv.visitFrame(Opcodes.F_NEW, 2, new Object[]{recordType, "java/lang/Object"}, 0, new Object[]{});
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        // Peak stack = two operands of the widest component (2+2 for long/double), else 2.
        mv.visitMaxs(wide ? 4 : 2, 2);
        mv.visitEnd();
    }

    /**
     * Synthesize {@code static int <name>(Record this)} that folds the components into a hash,
     * replacing the {@code ObjectMethods} bootstrap that JBMC links to an unconstrained int.
     *
     * <p><b>Soundness contract.</b> The JDK deliberately leaves a record's exact hashCode value
     * <em>unspecified</em> ("derived from the components"), so asserting a specific magic constant
     * would be wrong. What the JDK <em>does</em> guarantee — and what we make true and visible to
     * JBMC — is that hashCode is a <em>pure, deterministic function of the components</em>: it reads
     * only the components, with no nondet, so equal records (equal components) get equal hashCode and
     * repeated calls agree. We emit the classic {@code result = 31*result + componentHash} fold (the
     * same shape {@code java.util.Objects.hash} / {@code Arrays.hashCode} use), with each
     * component's hash computed the canonical way: booleans→1231/1237, long/double folded high^low,
     * float via {@code floatToIntBits}, and reference components via {@link BmcStrings#objHashCode}
     * (null→0, String content-hashed soundly, other refs delegated). This is a real consistent value,
     * not nondet — which is exactly the property the conformance proofs check.
     */
    private static void emitRecordHashCodeHelper(ClassWriter cw, boolean isInterface, RecordHashHelper h) {
        int access = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC
                | (isInterface ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE);
        MethodVisitor mv = cw.visitMethod(access, h.name, h.desc, null, null);
        mv.visitCode();
        boolean wide = false;

        // int result = 0;
        mv.visitInsn(Opcodes.ICONST_0);
        boolean hasBoolean = false;
        for (Handle g : h.getters) {
            // result = result * 31 + componentHash(this.<component>);
            mv.visitIntInsn(Opcodes.BIPUSH, 31);
            mv.visitInsn(Opcodes.IMUL);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            Type rt = loadComponent(mv, g); // pushes the component value
            if (componentHashIsWide(rt)) {
                wide = true;
            }
            if (rt.getSort() == Type.BOOLEAN) {
                hasBoolean = true;
            }
            emitComponentHash(mv, rt); // consumes the value, pushes its int hash
            mv.visitInsn(Opcodes.IADD);
        }
        mv.visitInsn(Opcodes.IRETURN);
        // Stack peak with the running result int (1 slot) underneath: long/double folding spikes to 6
        // (result + a long DUP2'd = 1+2+2 then +1 for the shift count), a boolean to 3, else 2.
        mv.visitMaxs(wide ? 6 : (hasBoolean ? 3 : 2), 1);
        mv.visitEnd();
    }

    /** True if hashing this component type transiently puts a category-2 value on the stack. */
    private static boolean componentHashIsWide(Type t) {
        return t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE;
    }

    /**
     * Given a component value of type {@code t} on top of the stack, replace it with its {@code int}
     * hash, using the canonical per-type recipe (matches {@code Boolean/Integer/Long/Float/Double
     * .hashCode} and {@link java.util.Objects#hashCode}). All paths are pure functions of the value,
     * so equal components hash equal — the only property we rely on for soundness.
     */
    private static void emitComponentHash(MethodVisitor mv, Type t) {
        switch (t.getSort()) {
            case Type.BOOLEAN:
                // Boolean.hashCode: b ? 1231 : 1237, computed branchlessly as 1237 - 6*b (b in {0,1})
                // so no stack-map frame is needed (the running result int stays on the stack untouched).
                mv.visitIntInsn(Opcodes.BIPUSH, 6);
                mv.visitInsn(Opcodes.IMUL);
                mv.visitLdcInsn(1237);
                mv.visitInsn(Opcodes.SWAP);
                mv.visitInsn(Opcodes.ISUB);
                return;
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
                return; // already an int whose hashCode is itself
            case Type.LONG:
                // (int)(v ^ (v >>> 32))
                mv.visitInsn(Opcodes.DUP2);
                mv.visitIntInsn(Opcodes.BIPUSH, 32);
                mv.visitInsn(Opcodes.LUSHR);
                mv.visitInsn(Opcodes.LXOR);
                mv.visitInsn(Opcodes.L2I);
                return;
            case Type.FLOAT:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "floatToIntBits",
                        "(F)I", false);
                return;
            case Type.DOUBLE:
                // long bits = doubleToLongBits(v); (int)(bits ^ (bits >>> 32))
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "doubleToLongBits",
                        "(D)J", false);
                mv.visitInsn(Opcodes.DUP2);
                mv.visitIntInsn(Opcodes.BIPUSH, 32);
                mv.visitInsn(Opcodes.LUSHR);
                mv.visitInsn(Opcodes.LXOR);
                mv.visitInsn(Opcodes.L2I);
                return;
            default: // reference / array
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "objHashCode",
                        "(Ljava/lang/Object;)I", false);
        }
    }

    /** Component types whose {@code String.valueOf} we can render soundly in {@link #appendValue}:
     *  the primitives (numerics routed through {@code Integer/Long.toString}, the rest via the sound
     *  {@code StringBuilder} overloads) and {@code String} (appended directly). Other reference
     *  components would need {@code o.toString()}, which JBMC links to nondet — so a record with one
     *  is left with its original {@code toString} indy rather than desugared unsoundly. */
    private static boolean allComponentsRenderSoundly(List<Handle> getters) {
        for (Handle g : getters) {
            Type t = g.getTag() == Opcodes.H_GETFIELD
                    ? Type.getType(g.getDesc())
                    : Type.getReturnType(g.getDesc());
            if (t.getSort() == Type.OBJECT && !t.getInternalName().equals(STRING)) {
                return false;
            }
            if (t.getSort() == Type.ARRAY) {
                return false;
            }
        }
        return true;
    }

    /** "a/b/Point" -> "Point"; "a/b/Outer$Inner" -> "Inner" (record toString uses the simple name). */
    private static String simpleName(String internalName) {
        int slash = internalName.lastIndexOf('/');
        String s = slash < 0 ? internalName : internalName.substring(slash + 1);
        int dollar = s.lastIndexOf('$');
        return dollar < 0 ? s : s.substring(dollar + 1);
    }

    /**
     * Synthesize {@code static String <name>(Record this)} that builds the canonical record
     * {@code "Name[c1=v1, c2=v2]"} with {@code StringBuilder} (sound in JBMC) instead of the
     * {@code ObjectMethods} bootstrap that JBMC links to an unconstrained String. Only reached when
     * every component renders soundly (primitive or String — see {@link #allComponentsRenderSoundly}),
     * so each value is appended via the same sound path the concat desugar uses: numerics through
     * {@code Integer/Long.toString}, {@code String} directly, the remaining primitives via the typed
     * {@code StringBuilder} overloads. The literal scaffolding ({@code "Name["}, {@code "=" }, {@code
     * ", "}, {@code "]"}) is exact, so the result's content is a sound function of the components.
     */
    private static void emitRecordToStringHelper(ClassWriter cw, boolean isInterface, RecordStrHelper h) {
        int access = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC
                | (isInterface ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE);
        MethodVisitor mv = cw.visitMethod(access, h.name, h.desc, null, null);
        mv.visitCode();

        mv.visitTypeInsn(Opcodes.NEW, STRING_BUILDER);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, STRING_BUILDER, "<init>", "()V", false);

        boolean wide = false;
        appendLiteral(mv, h.simpleName + "[");
        for (int i = 0; i < h.getters.size(); i++) {
            if (i > 0) {
                appendLiteral(mv, ", ");
            }
            appendLiteral(mv, h.compNames.get(i) + "=");
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            Type rt = loadComponent(mv, h.getters.get(i));
            if (rt.getSize() == 2) {
                wide = true;
            }
            // Reuse the concat desugar's sound per-type append (int/long -> Integer/Long.toString,
            // String/others via the appropriate StringBuilder overload).
            appendValue(mv, rt);
        }
        appendLiteral(mv, "]");

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "toString", "()Ljava/lang/String;", false);
        mv.visitInsn(Opcodes.ARETURN);
        // SB ref (1) under the receiver/loaded component; a wide (long/double) component peaks at 3.
        mv.visitMaxs(wide ? 3 : 2, 1);
        mv.visitEnd();
    }

    /** Append a constant String to the StringBuilder already on the stack (ref left on the stack). */
    private static void appendLiteral(MethodVisitor mv, String s) {
        mv.visitLdcInsn(s);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
    }

    /** Append a component value (already on the stack) to the StringBuilder, soundly: int/long go
     *  through {@code Integer/Long.toString} (append(int/long) is unsound in JBMC), the rest use the
     *  typed overload. Shares the recipe with {@link #appendArg} but the value is already loaded. */
    private static void appendValue(MethodVisitor mv, Type t) {
        int sort = t.getSort();
        if (sort == Type.BYTE || sort == Type.SHORT || sort == Type.INT) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "toString",
                    "(I)Ljava/lang/String;", false);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            return;
        }
        if (sort == Type.LONG) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "toString",
                    "(J)Ljava/lang/String;", false);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            return;
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append", appendDesc(t), false);
    }

    /** Emit the access for one record component (the receiver is already on the stack) and return
     *  its type. Records pass components as field getters (H_GETFIELD) or accessor handles. */
    private static Type loadComponent(MethodVisitor mv, Handle g) {
        if (g.getTag() == Opcodes.H_GETFIELD) {
            mv.visitFieldInsn(Opcodes.GETFIELD, g.getOwner(), g.getName(), g.getDesc());
            return Type.getType(g.getDesc());
        }
        boolean itf = g.getTag() == Opcodes.H_INVOKEINTERFACE;
        int op = g.getTag() == Opcodes.H_INVOKESTATIC ? Opcodes.INVOKESTATIC
                : itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
        mv.visitMethodInsn(op, g.getOwner(), g.getName(), g.getDesc(), itf);
        return Type.getReturnType(g.getDesc());
    }

    private static void flushLiteral(MethodVisitor mv, StringBuilder lit) {
        if (lit.length() == 0) {
            return;
        }
        mv.visitLdcInsn(lit.toString());
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        lit.setLength(0);
    }

    private static void appendArg(MethodVisitor mv, Type t, int slot) {
        mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot);
        // int/long → string via Integer/Long.toString (JBMC models those soundly), then append the
        // String — StringBuilder.append(int) itself is unsound. So "x" + anInt verifies. char stays
        // a char append (routing it through toString would print the code point, not the character).
        int sort = t.getSort();
        if (sort == Type.BYTE || sort == Type.SHORT || sort == Type.INT) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "toString",
                    "(I)Ljava/lang/String;", false);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            return;
        }
        if (sort == Type.LONG) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "toString",
                    "(J)Ljava/lang/String;", false);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            return;
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append", appendDesc(t), false);
    }

    /** StringBuilder.append overload descriptor for an argument type (arrays/objects via Object). */
    private static String appendDesc(Type t) {
        switch (t.getSort()) {
            case Type.BOOLEAN: return "(Z)Ljava/lang/StringBuilder;";
            case Type.CHAR:    return "(C)Ljava/lang/StringBuilder;";
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:     return "(I)Ljava/lang/StringBuilder;";
            case Type.LONG:    return "(J)Ljava/lang/StringBuilder;";
            case Type.FLOAT:   return "(F)Ljava/lang/StringBuilder;";
            case Type.DOUBLE:  return "(D)Ljava/lang/StringBuilder;";
            default:
                // String gets the String overload; everything else (incl. char[]) via Object,
                // matching StringConcat semantics (arrays concat as their Object.toString()).
                return t.getInternalName().equals(STRING)
                        ? "(Ljava/lang/String;)Ljava/lang/StringBuilder;"
                        : "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
        }
    }
}
