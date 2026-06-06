package org.bmc4j.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Desugars pattern-matching {@code switch} {@code invokedynamic} (bootstraps
 * {@code java.lang.runtime.SwitchBootstraps.typeSwitch} and {@code .enumSwitch}) into an ordinary
 * {@code instanceof}/{@code equals}/{@code ==} chain, because JBMC links an indy call site to an
 * <em>unconstrained</em> result — which is unsound for a symbolic-typed subject (the selected branch
 * is decoupled from the subject's real type). The others (string concat, lambdas, record
 * {@code equals}/{@code hashCode}/{@code toString}) are already desugared the same way in
 * {@link StringBytecode} / {@link LambdaBytecode}; whatever no pass recognises is surfaced by
 * {@link ResidualIndyBytecode} rather than silently trusted.
 *
 * <p>{@code enumSwitch} (a pattern switch over an enum subject, e.g. one with a {@code case null}
 * arm) shares the typeSwitch contract below; its labels are mostly plain Strings naming constants of
 * the <em>selector's</em> enum type (recovered from the indy descriptor), matched by identity —
 * enum constants are singletons, the same reason javac's indy-free {@code $SwitchMap} form is
 * already sound under JBMC.
 *
 * <p><b>The contract we reproduce, exactly.</b> A {@code typeSwitch(Object target, int restartIndex)}
 * carries an ordered label list in its bootstrap arguments and returns:
 * <ul>
 *   <li>{@code -1} if {@code target == null};</li>
 *   <li>otherwise the index {@code i} of the <em>first</em> label with {@code i >= restartIndex} that
 *       the target matches — a <b>type label</b> (a {@code Class}) matches when
 *       {@code label.isInstance(target)}; a <b>constant label</b> ({@code Integer}/{@code String}/...
 *       constant) matches when {@code label.equals(target)}; an <b>enum-constant label</b> (an
 *       {@code Enum$EnumDesc} dynamic constant) matches when {@code target == ThatEnumConstant};</li>
 *   <li>otherwise {@code labels.length} (no match — the {@code default}/{@code MatchException} arm).
 * </ul>
 * The {@code restartIndex} parameter is how guards re-enter: when a guarded case's {@code when}
 * clause fails, javac re-invokes the indy with {@code restartIndex} set to the next case, so the same
 * subject resumes matching <em>after</em> the failed case. Honouring {@code restartIndex} verbatim is
 * what makes guarded switches sound.
 *
 * <p>We replace each such site with an {@code invokestatic} to a synthesized per-class static helper
 * of the <em>same descriptor</em> ({@code (Ljava/lang/Object;I)I}); the dynamic args already on the
 * stack (subject, restartIndex) become the helper's arguments unchanged. The helper is a linear
 * {@code i >= restartIndex && <match>} chain — all of {@code instanceof}, reference {@code ==}, and
 * {@code Integer/String.equals} (via {@link BmcStrings#objEquals}) are modelled soundly by JBMC over
 * a symbolic-typed subject, so the selected branch is now provably tied to the subject's real type.
 *
 * <p><b>Soundness guard.</b> If a label is a shape we don't recognise (e.g. a future constant kind),
 * we leave the whole indy <em>untouched</em> rather than emit a stand-in that might silently diverge
 * from the JDK contract — never trade a known-unsound site for an unknown-unsound one.
 */
public final class SwitchBytecode {

    private static final String SWITCH_BOOTSTRAPS = "java/lang/runtime/SwitchBootstraps";
    private static final String BMC_STRINGS = "org/bmc4j/engine/BmcStrings";

    private SwitchBytecode() {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Rewrite directory AND jar entries of {@code classpath}, memoized per classpath (race-free). */
    public static String rewrite(String classpath) {
        return CACHE.computeIfAbsent(classpath, SwitchBytecode::doRewrite);
    }

    private static String doRewrite(String classpath) {
        return ClasspathMirror.mirror(classpath, "switches",
                b -> new ClasspathMirror.Transformed(rewriteClass(b)));
    }

    /** One {@code typeSwitch} site we replaced with a call to a generated helper. */
    private static final class SwitchHelper {
        final String name;        // generated method name
        final String desc;        // same descriptor as the indy: (Ljava/lang/Object;I)I
        final List<LabelMatch> labels;

        SwitchHelper(String name, String desc, List<LabelMatch> labels) {
            this.name = name;
            this.desc = desc;
            this.labels = labels;
        }
    }

    /** A single switch label, decoded into how it matches a subject. */
    private abstract static class LabelMatch {
    }

    /** Type pattern: matches when {@code internalName.isInstance(target)}. */
    private static final class TypeLabel extends LabelMatch {
        final String internalName;

        TypeLabel(String internalName) {
            this.internalName = internalName;
        }
    }

    /** {@code String} constant label: matches when {@code value.equals(target)} (sound via BmcStrings). */
    private static final class StringLabel extends LabelMatch {
        final String value;

        StringLabel(String value) {
            this.value = value;
        }
    }

    /** Boxed-numeric / Character / Boolean constant label: matches when {@code box(value).equals(target)}. */
    private static final class BoxedConstLabel extends LabelMatch {
        final Object value;   // Integer / Long / Float / Double / Character / Boolean / Byte / Short

        BoxedConstLabel(Object value) {
            this.value = value;
        }
    }

    /** Enum-constant label: matches when {@code target == EnumClass.CONSTANT} (reference identity). */
    private static final class EnumLabel extends LabelMatch {
        final String enumInternalName; // e.g. "probe/Color"
        final String constantName;     // e.g. "RED"

        EnumLabel(String enumInternalName, String constantName) {
            this.enumInternalName = enumInternalName;
            this.constantName = constantName;
        }
    }

    /** Pure transform: desugar {@code SwitchBootstraps.typeSwitch} sites. Package-private for tests. */
    static byte[] rewriteClass(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        List<SwitchHelper> helpers = new ArrayList<>();
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
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
                        if (SWITCH_BOOTSTRAPS.equals(bsm.getOwner())
                                && (name.equals("typeSwitch") || name.equals("enumSwitch"))) {
                            // Same contract, different label encoding: typeSwitch labels are
                            // Class/constant/EnumDesc; enumSwitch labels are mostly plain Strings
                            // NAMING constants of the selector's enum type (taken from the indy
                            // descriptor), matched by identity — not by string equality.
                            List<LabelMatch> labels = name.equals("typeSwitch")
                                    ? decodeLabels(bsmArgs)
                                    : decodeEnumSwitchLabels(desc, bsmArgs);
                            if (labels != null) {
                                String hName = "bmc$" + name + "$" + (counter[0]++);
                                helpers.add(new SwitchHelper(hName, desc, labels));
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], hName, desc, isInterface[0]);
                                return;
                            }
                        }
                        // Any unrecognised bootstrap / label shape: leave untouched rather than risk an
                        // unsound stand-in — the ResidualIndyBytecode pass then surfaces it as a
                        // visible nondet stub (footnote / strictStubs / REFUTED-to-UNKNOWN demotion).
                        super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
                    }
                };
            }

            @Override
            public void visitEnd() {
                for (SwitchHelper h : helpers) {
                    emitSwitchHelper(cw, isInterface[0], h);
                }
                super.visitEnd();
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    /**
     * Decode the {@code typeSwitch} bootstrap arguments into our match list, or return {@code null} to
     * signal "leave this site alone" if any label is a shape we don't soundly handle.
     */
    private static List<LabelMatch> decodeLabels(Object[] bsmArgs) {
        List<LabelMatch> labels = new ArrayList<>(bsmArgs.length);
        for (Object arg : bsmArgs) {
            if (arg instanceof Type t) {
                if (t.getSort() != Type.OBJECT && t.getSort() != Type.ARRAY) {
                    return null; // a primitive type label is not a thing typeSwitch emits
                }
                labels.add(new TypeLabel(t.getInternalName()));
            } else if (arg instanceof String s) {
                labels.add(new StringLabel(s));
            } else if (arg instanceof Integer || arg instanceof Long || arg instanceof Float
                    || arg instanceof Double || arg instanceof Character || arg instanceof Boolean
                    || arg instanceof Byte || arg instanceof Short) {
                labels.add(new BoxedConstLabel(arg));
            } else if (arg instanceof ConstantDynamic cd) {
                EnumLabel e = decodeEnumLabel(cd);
                if (e == null) {
                    return null; // unknown dynamic-constant label
                }
                labels.add(e);
            } else {
                return null; // unknown label kind
            }
        }
        return labels;
    }

    /**
     * Decode {@code enumSwitch} bootstrap arguments. The selector's enum type comes from the indy
     * descriptor ({@code (L<EnumType>;I)I}); a plain {@code String} label names a constant of that
     * type and matches by <b>identity</b> ({@code target == EnumType.NAME} — enum constants are
     * singletons), NOT by string equality; a {@code Class} label is an ordinary type pattern; a
     * qualified {@code Enum$EnumDesc} dynamic constant decodes like typeSwitch's. {@code null} =
     * "leave this site alone" (an unknown label shape falls through to the residual-indy surfacing).
     */
    private static List<LabelMatch> decodeEnumSwitchLabels(String desc, Object[] bsmArgs) {
        Type[] args = Type.getArgumentTypes(desc);
        if (args.length != 2 || args[0].getSort() != Type.OBJECT) {
            return null; // not the (EnumType, int) shape we know
        }
        String enumInternalName = args[0].getInternalName();
        List<LabelMatch> labels = new ArrayList<>(bsmArgs.length);
        for (Object arg : bsmArgs) {
            if (arg instanceof String constantName) {
                labels.add(new EnumLabel(enumInternalName, constantName));
            } else if (arg instanceof Type t) {
                if (t.getSort() != Type.OBJECT && t.getSort() != Type.ARRAY) {
                    return null;
                }
                labels.add(new TypeLabel(t.getInternalName()));
            } else if (arg instanceof ConstantDynamic cd) {
                EnumLabel e = decodeEnumLabel(cd);
                if (e == null) {
                    return null;
                }
                labels.add(e);
            } else {
                return null; // unknown label kind
            }
        }
        return labels;
    }

    /**
     * Decode an {@code Enum$EnumDesc} dynamic constant (as javac emits for an enum-constant label in a
     * mixed pattern switch). Shape:
     * {@code ConstantBootstraps.invoke(EnumDesc.of, <ClassDesc dynamic>, "CONSTANT")} where the inner
     * {@code ClassDesc} dynamic carries the enum's binary class name as a plain String argument.
     * Returns {@code null} if it isn't that exact shape.
     */
    private static EnumLabel decodeEnumLabel(ConstantDynamic cd) {
        if (!"java/lang/Enum$EnumDesc".equals(Type.getType(cd.getDescriptor()).getInternalName())) {
            return null;
        }
        if (cd.getBootstrapMethodArgumentCount() != 3) {
            return null;
        }
        Object classDescArg = cd.getBootstrapMethodArgument(1);
        Object nameArg = cd.getBootstrapMethodArgument(2);
        if (!(nameArg instanceof String constantName) || !(classDescArg instanceof ConstantDynamic classDesc)) {
            return null;
        }
        // The inner ClassDesc dynamic's last arg is the binary class name, e.g. "probe.Color".
        if (classDesc.getBootstrapMethodArgumentCount() < 1) {
            return null;
        }
        Object binName = classDesc.getBootstrapMethodArgument(classDesc.getBootstrapMethodArgumentCount() - 1);
        if (!(binName instanceof String binary)) {
            return null;
        }
        return new EnumLabel(binary.replace('.', '/'), constantName);
    }

    /**
     * Synthesize {@code static int <name>(Object target, int restartIndex)} computing the {@code
     * typeSwitch} contract with a linear {@code instanceof}/{@code equals}/{@code ==} chain. Every
     * branch target is reached with an empty operand stack, so explicit {@code F_NEW} frames (locals =
     * {@code [Object, int]}, no stack) suffice and we avoid {@code COMPUTE_FRAMES}.
     */
    private static void emitSwitchHelper(ClassWriter cw, boolean isInterface, SwitchHelper h) {
        int access = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC
                | (isInterface ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE);
        MethodVisitor mv = cw.visitMethod(access, h.name, h.desc, null, null);
        mv.visitCode();

        // if (target == null) return -1;
        Label notNull = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
        mv.visitInsn(Opcodes.ICONST_M1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(notNull);
        frame(mv);

        boolean wide = false;
        for (int i = 0; i < h.labels.size(); i++) {
            LabelMatch lm = h.labels.get(i);
            Label next = new Label();
            // if (restartIndex > i) skip this label.
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            pushInt(mv, i);
            mv.visitJumpInsn(Opcodes.IF_ICMPGT, next);

            // if (<matches>) return i;
            if (lm instanceof TypeLabel tl) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.INSTANCEOF, tl.internalName);
                mv.visitJumpInsn(Opcodes.IFEQ, next);
            } else if (lm instanceof StringLabel sl) {
                mv.visitLdcInsn(sl.value);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "objEquals",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(Opcodes.IFEQ, next);
            } else if (lm instanceof BoxedConstLabel bc) {
                if (bc.value instanceof Long || bc.value instanceof Double) {
                    wide = true;
                }
                pushBoxed(mv, bc.value);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "objEquals",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(Opcodes.IFEQ, next);
            } else {
                EnumLabel el = (EnumLabel) lm;
                // target == EnumClass.CONSTANT  (enum constants are singletons => reference identity)
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitFieldInsn(Opcodes.GETSTATIC, el.enumInternalName, el.constantName,
                        "L" + el.enumInternalName + ";");
                mv.visitJumpInsn(Opcodes.IF_ACMPNE, next);
            }
            pushInt(mv, i);
            mv.visitInsn(Opcodes.IRETURN);

            mv.visitLabel(next);
            frame(mv);
        }

        // no label matched -> labels.length (the default / MatchException arm)
        pushInt(mv, h.labels.size());
        mv.visitInsn(Opcodes.IRETURN);

        // Peak stack: a String/boxed compare pushes 2 refs; a long/double box pushes a category-2
        // value (constant) + target ref before boxing => up to 3 slots.
        mv.visitMaxs(wide ? 3 : 2, 2);
        mv.visitEnd();
    }

    /** Stack-map frame at a branch target: locals {@code [Object, int]}, empty stack. */
    private static void frame(MethodVisitor mv) {
        mv.visitFrame(Opcodes.F_NEW, 2, new Object[]{"java/lang/Object", Opcodes.INTEGER}, 0, new Object[]{});
    }

    /** Push an {@code int} constant with the most compact opcode. */
    private static void pushInt(MethodVisitor mv, int v) {
        if (v >= -1 && v <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + v); // ICONST_M1..ICONST_5 are contiguous around ICONST_0
        } else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, v);
        } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, v);
        } else {
            mv.visitLdcInsn(v);
        }
    }

    /** Push a boxed wrapper for a numeric/char/boolean constant label (so {@code objEquals} sees it). */
    private static void pushBoxed(MethodVisitor mv, Object value) {
        if (value instanceof Integer i) {
            pushInt(mv, i);
            box(mv, "java/lang/Integer", "(I)Ljava/lang/Integer;");
        } else if (value instanceof Long l) {
            mv.visitLdcInsn(l);
            box(mv, "java/lang/Long", "(J)Ljava/lang/Long;");
        } else if (value instanceof Float f) {
            mv.visitLdcInsn(f);
            box(mv, "java/lang/Float", "(F)Ljava/lang/Float;");
        } else if (value instanceof Double d) {
            mv.visitLdcInsn(d);
            box(mv, "java/lang/Double", "(D)Ljava/lang/Double;");
        } else if (value instanceof Character c) {
            pushInt(mv, c);
            box(mv, "java/lang/Character", "(C)Ljava/lang/Character;");
        } else if (value instanceof Boolean b) {
            mv.visitInsn(b ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            box(mv, "java/lang/Boolean", "(Z)Ljava/lang/Boolean;");
        } else if (value instanceof Byte b) {
            pushInt(mv, b);
            box(mv, "java/lang/Byte", "(B)Ljava/lang/Byte;");
        } else if (value instanceof Short s) {
            pushInt(mv, s);
            box(mv, "java/lang/Short", "(S)Ljava/lang/Short;");
        } else {
            throw new IllegalArgumentException("not a boxable constant: " + value);
        }
    }

    private static void box(MethodVisitor mv, String wrapper, String desc) {
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper, "valueOf", desc, false);
    }
}
