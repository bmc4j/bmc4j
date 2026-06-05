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
 * Desugars lambda / method-reference {@code invokedynamic} (bootstrap
 * {@code java.lang.invoke.LambdaMetafactory}) into ordinary classes, because JBMC cannot construct
 * the hidden class the JVM spins at runtime for an indy lambda. This is the classic
 * "delambdafication" the JDK does at runtime (and javac did pre-8): for each lambda site we
 * generate a class implementing the functional interface, holding the captured values in fields and
 * delegating the SAM method to the implementation handle, and replace the indy with an
 * {@code invokestatic} to a generated factory of the same descriptor (so the captured args already
 * on the stack become the factory's arguments unchanged).
 *
 * <p>The generated classes only ever go on JBMC's analysis classpath — never a real classloader —
 * so lambda-body access (private synthetic {@code lambda$*} methods) is handled by bumping those to
 * public rather than by nestmate attributes.
 *
 * <p>v1 covers capturing/non-capturing lambdas and static/instance/constructor method references
 * over a single-abstract-method interface, with primitive box/unbox + cast adaptation. Deferred:
 * serializable lambdas (the serialization side of {@code altMetafactory}), marker/intersection SAMs.
 */
public final class LambdaBytecode {

    private static final String METAFACTORY = "java/lang/invoke/LambdaMetafactory";

    private LambdaBytecode() {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Rewrite directory AND jar entries of {@code classpath}, memoized per classpath (race-free).
     *  Each lambda site spins an extra generated class, emitted alongside the rewritten owner — in the
     *  mirror dir or, for a jar entry, as a new jar entry. */
    public static String rewrite(String classpath) {
        return CACHE.computeIfAbsent(classpath, LambdaBytecode::doRewrite);
    }

    private static String doRewrite(String classpath) {
        return ClasspathMirror.mirror(classpath, "lambdas", bytes -> {
            Result r = transform(bytes);
            if (r.extra.isEmpty()) {
                return new ClasspathMirror.Transformed(r.main);
            }
            java.util.Map<String, byte[]> extra = new java.util.LinkedHashMap<>();
            for (GeneratedClass g : r.extra) {
                extra.put(g.internalName, g.bytes);
            }
            return new ClasspathMirror.Transformed(r.main, extra);
        });
    }

    static final class GeneratedClass {
        final String internalName;
        final byte[] bytes;

        GeneratedClass(String internalName, byte[] bytes) {
            this.internalName = internalName;
            this.bytes = bytes;
        }
    }

    static final class Result {
        final byte[] main;
        final List<GeneratedClass> extra;

        Result(byte[] main, List<GeneratedClass> extra) {
            this.main = main;
            this.extra = extra;
        }
    }

    /** One lambda indy site we replaced with a call to a generated factory + class. */
    private static final class LambdaSite {
        final String factoryName;   // generated static factory in the owner
        final String genName;       // generated class implementing the functional interface
        final String indyDesc;      // (captures...)FunctionalInterface
        final String samName;       // functional-interface method name
        final Type samType;         // erased SAM descriptor (bsmArg 0)
        final Handle impl;          // implementation method handle (bsmArg 1)

        LambdaSite(String factoryName, String genName, String indyDesc, String samName,
                   Type samType, Handle impl) {
            this.factoryName = factoryName;
            this.genName = genName;
            this.indyDesc = indyDesc;
            this.samName = samName;
            this.samType = samType;
            this.impl = impl;
        }
    }

    /** Package-private for tests. */
    static Result transform(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        List<LambdaSite> sites = new ArrayList<>();
        List<GeneratedClass> extra = new ArrayList<>();
        int[] counter = {0};
        String[] owner = new String[1];
        int[] version = new int[1];

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visit(int ver, int access, String name, String sig, String sup, String[] ifs) {
                owner[0] = name;
                version[0] = ver;
                super.visit(ver, access, name, sig, sup, ifs);
            }

            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                // Lambda bodies are private synthetic; the generated class must call them, so make
                // them public (analysis classpath only — never loaded by a real JVM).
                int access = a;
                if (n.startsWith("lambda$") && (a & Opcodes.ACC_PRIVATE) != 0) {
                    access = (a & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
                }
                MethodVisitor mv = super.visitMethod(access, n, d, s, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
                        if (METAFACTORY.equals(bsm.getOwner())
                                && (bsm.getName().equals("metafactory") || bsm.getName().equals("altMetafactory"))
                                && bsmArgs.length >= 2 && bsmArgs[0] instanceof Type && bsmArgs[1] instanceof Handle) {
                            int n = counter[0]++;
                            String genName = owner[0] + "$$Lambda$" + n;
                            String factoryName = "bmc$lambda$" + n;
                            sites.add(new LambdaSite(factoryName, genName, desc, name,
                                    (Type) bsmArgs[0], (Handle) bsmArgs[1]));
                            // captured args on the stack -> factory args (same descriptor).
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], factoryName, desc, false);
                        } else {
                            super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
                        }
                    }
                };
            }

            @Override
            public void visitEnd() {
                for (LambdaSite s : sites) {
                    emitFactory(cw, owner[0], s);
                    extra.add(new GeneratedClass(s.genName, generateLambdaClass(version[0], owner[0], s)));
                }
                super.visitEnd();
            }
        };
        cr.accept(cv, 0);
        return new Result(cw.toByteArray(), extra);
    }

    /** {@code static <FI> bmc$lambda$N(<captures>) { return new Owner$$Lambda$N(captures); }} */
    private static void emitFactory(ClassWriter cw, String owner, LambdaSite s) {
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                s.factoryName, s.indyDesc, null, null);
        mv.visitCode();
        Type[] caps = Type.getArgumentTypes(s.indyDesc);
        mv.visitTypeInsn(Opcodes.NEW, s.genName);
        mv.visitInsn(Opcodes.DUP);
        int slot = 0;
        int capSizes = 0;
        for (Type c : caps) {
            mv.visitVarInsn(c.getOpcode(Opcodes.ILOAD), slot);
            slot += c.getSize();
            capSizes += c.getSize();
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, s.genName, "<init>",
                "(" + argDesc(caps) + ")V", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(capSizes + 2, Math.max(capSizes, 1));
        mv.visitEnd();
    }

    private static byte[] generateLambdaClass(int version, String owner, LambdaSite s) {
        Type[] caps = Type.getArgumentTypes(s.indyDesc);
        String fi = Type.getReturnType(s.indyDesc).getInternalName();

        ClassWriter cw = new ClassWriter(0);
        cw.visit(Math.max(version, Opcodes.V1_8), Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL,
                s.genName, null, "java/lang/Object", new String[]{fi});

        for (int i = 0; i < caps.length; i++) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "f" + i, caps[i].getDescriptor(), null, null)
                    .visitEnd();
        }

        // constructor: store captures into fields
        MethodVisitor ctor = cw.visitMethod(0, "<init>", "(" + argDesc(caps) + ")V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        int slot = 1;
        int capSizes = 0;
        for (int i = 0; i < caps.length; i++) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitVarInsn(caps[i].getOpcode(Opcodes.ILOAD), slot);
            ctor.visitFieldInsn(Opcodes.PUTFIELD, s.genName, "f" + i, caps[i].getDescriptor());
            slot += caps[i].getSize();
            capSizes += caps[i].getSize();
        }
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(3, 1 + capSizes);
        ctor.visitEnd();

        emitSamBody(cw, caps, s);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** The SAM method: load captures + SAM args, adapt, invoke the impl handle, adapt the result. */
    private static void emitSamBody(ClassWriter cw, Type[] caps, LambdaSite s) {
        Type[] samArgs = Type.getArgumentTypes(s.samType.getDescriptor());
        Type samRet = Type.getReturnType(s.samType.getDescriptor());
        Type[] implArgs = Type.getArgumentTypes(s.impl.getDesc());
        int tag = s.impl.getTag();
        boolean ctorRef = tag == Opcodes.H_NEWINVOKESPECIAL;
        boolean instanceCall = tag == Opcodes.H_INVOKEVIRTUAL || tag == Opcodes.H_INVOKEINTERFACE
                || tag == Opcodes.H_INVOKESPECIAL;

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, s.samName, s.samType.getDescriptor(), null, null);
        mv.visitCode();

        if (ctorRef) {
            mv.visitTypeInsn(Opcodes.NEW, s.impl.getOwner());
            mv.visitInsn(Opcodes.DUP);
        }

        // Provided values, in order: captures (from fields) then SAM args (from params).
        // For an instance call the first provided value is the receiver.
        int provided = caps.length + samArgs.length;
        int implIdx = 0; // index into implArgs for the next non-receiver value
        int samSlotBase = 1; // SAM params start at local 1
        int[] samSlot = new int[samArgs.length];
        int sp = samSlotBase;
        for (int i = 0; i < samArgs.length; i++) {
            samSlot[i] = sp;
            sp += samArgs[i].getSize();
        }

        for (int pos = 0; pos < provided; pos++) {
            boolean isCapture = pos < caps.length;
            Type srcType = isCapture ? caps[pos] : samArgs[pos - caps.length];
            // load the value
            if (isCapture) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitFieldInsn(Opcodes.GETFIELD, s.genName, "f" + pos, srcType.getDescriptor());
            } else {
                mv.visitVarInsn(srcType.getOpcode(Opcodes.ILOAD), samSlot[pos - caps.length]);
            }
            // adapt to the impl's expected type
            Type dstType;
            if (instanceCall && pos == 0) {
                dstType = Type.getObjectType(s.impl.getOwner()); // receiver
            } else {
                dstType = implArgs[implIdx++];
            }
            coerce(mv, srcType, dstType);
        }

        int op = ctorRef ? Opcodes.INVOKESPECIAL
                : tag == Opcodes.H_INVOKESTATIC ? Opcodes.INVOKESTATIC
                : tag == Opcodes.H_INVOKEINTERFACE ? Opcodes.INVOKEINTERFACE
                : tag == Opcodes.H_INVOKESPECIAL ? Opcodes.INVOKESPECIAL
                : Opcodes.INVOKEVIRTUAL;
        boolean itf = tag == Opcodes.H_INVOKEINTERFACE;
        mv.visitMethodInsn(op, s.impl.getOwner(), s.impl.getName(), s.impl.getDesc(), itf);

        // result type produced by the impl, then adapt to the SAM return type
        Type implRet = ctorRef ? Type.getObjectType(s.impl.getOwner()) : Type.getReturnType(s.impl.getDesc());
        if (samRet.getSort() == Type.VOID) {
            if (implRet.getSort() != Type.VOID) {
                mv.visitInsn(implRet.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP);
            }
            mv.visitInsn(Opcodes.RETURN);
        } else {
            coerce(mv, implRet, samRet);
            mv.visitInsn(samRet.getOpcode(Opcodes.IRETURN));
        }

        int providedSizes = 0;
        for (Type c : caps) {
            providedSizes += c.getSize();
        }
        for (Type a : samArgs) {
            providedSizes += a.getSize();
        }
        mv.visitMaxs(providedSizes + 4, 1 + (sp - samSlotBase));
        mv.visitEnd();
    }

    /** Coerce the value on top of the stack from {@code src} to {@code dst} (box/unbox/cast/widen). */
    private static void coerce(MethodVisitor mv, Type src, Type dst) {
        if (src.getDescriptor().equals(dst.getDescriptor())) {
            return;
        }
        boolean srcPrim = isPrimitive(src);
        boolean dstPrim = isPrimitive(dst);

        if (srcPrim && dstPrim) {
            widen(mv, src, dst);
            return;
        }
        if (srcPrim) { // primitive -> reference: box
            String wrapper = wrapperOf(src);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper, "valueOf",
                    "(" + src.getDescriptor() + ")L" + wrapper + ";", false);
            return;
        }
        if (dstPrim) { // reference -> primitive: unbox
            String wrapper = wrapperOf(dst);
            mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapper, unboxMethod(dst),
                    "()" + dst.getDescriptor(), false);
            return;
        }
        // reference -> reference
        if (!dst.getInternalName().equals("java/lang/Object")) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, dst.getInternalName());
        }
    }

    private static void widen(MethodVisitor mv, Type src, Type dst) {
        int s = src.getSort();
        int d = dst.getSort();
        if (s == d) {
            return;
        }
        // int/short/byte/char are I on the stack; widen I -> long/float/double when needed.
        if (s <= Type.INT) {
            switch (d) {
                case Type.LONG: mv.visitInsn(Opcodes.I2L); return;
                case Type.FLOAT: mv.visitInsn(Opcodes.I2F); return;
                case Type.DOUBLE: mv.visitInsn(Opcodes.I2D); return;
                default: return;
            }
        }
        if (s == Type.LONG) {
            if (d == Type.FLOAT) { mv.visitInsn(Opcodes.L2F); return; }
            if (d == Type.DOUBLE) { mv.visitInsn(Opcodes.L2D); return; }
        }
        if (s == Type.FLOAT && d == Type.DOUBLE) {
            mv.visitInsn(Opcodes.F2D);
        }
        // other narrowings are not expected from a well-typed lambda site
    }

    private static boolean isPrimitive(Type t) {
        return t.getSort() >= Type.BOOLEAN && t.getSort() <= Type.DOUBLE;
    }

    private static String wrapperOf(Type t) {
        switch (t.getSort()) {
            case Type.BOOLEAN: return "java/lang/Boolean";
            case Type.CHAR: return "java/lang/Character";
            case Type.BYTE: return "java/lang/Byte";
            case Type.SHORT: return "java/lang/Short";
            case Type.INT: return "java/lang/Integer";
            case Type.LONG: return "java/lang/Long";
            case Type.FLOAT: return "java/lang/Float";
            case Type.DOUBLE: return "java/lang/Double";
            default: throw new IllegalArgumentException("not a primitive: " + t);
        }
    }

    private static String unboxMethod(Type t) {
        switch (t.getSort()) {
            case Type.BOOLEAN: return "booleanValue";
            case Type.CHAR: return "charValue";
            case Type.BYTE: return "byteValue";
            case Type.SHORT: return "shortValue";
            case Type.INT: return "intValue";
            case Type.LONG: return "longValue";
            case Type.FLOAT: return "floatValue";
            case Type.DOUBLE: return "doubleValue";
            default: throw new IllegalArgumentException("not a primitive: " + t);
        }
    }

    private static String argDesc(Type[] types) {
        StringBuilder sb = new StringBuilder();
        for (Type t : types) {
            sb.append(t.getDescriptor());
        }
        return sb.toString();
    }
}
