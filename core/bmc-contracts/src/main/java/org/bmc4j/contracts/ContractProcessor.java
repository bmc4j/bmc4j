package org.bmc4j.contracts;

import org.bmc4j.BmcContractsFor;
import org.bmc4j.Ensures;
import org.bmc4j.Requires;
import org.bmc4j.engine.ContractEnforceProofGenerator;
import org.bmc4j.engine.ContractManifest;
import org.bmc4j.engine.ContractStubGenerator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a test-side {@link BmcContractsFor} type into the artifacts the runtime needs for
 * modular (assume-guarantee) proofs, keeping {@code @Requires}/{@code @Ensures}
 * and their predicates <b>out of production code</b>. The annotated type mirrors a production
 * class's methods by signature and holds the predicates; for each contract this generates:
 *
 * <ul>
 *   <li>a <b>{@code <Contract>__BmcStubs}</b> class — the replace-direction summary
 *       ({@code assert requires; nondet; assume ensures; return}) the call-site rewriter
 *       redirects the <em>target</em> method to;</li>
 *   <li>a <b>{@code <Contract>__BmcEnforce}</b> class — one {@code @BmcProof} per contract that
 *       calls the <em>real</em> target method and asserts {@code @Ensures}, so a false contract
 *       turns the build red ("annotate != proven" is structural);</li>
 *   <li>lines in <b>{@value org.bmc4j.engine.ContractManifest#RESOURCE}</b> mapping the target
 *       method (owner/name/descriptor) to the stub, and naming each enforce class.</li>
 * </ul>
 *
 * <p>v1 targets {@code static}, value-returning methods; predicates are non-private static
 * {@code boolean} methods on the contract type.
 */
@SupportedAnnotationTypes("org.bmc4j.BmcContractsFor")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ContractProcessor extends AbstractProcessor {

    private final List<String> manifestLines = new ArrayList<>();
    private final Set<String> generated = new LinkedHashSet<>();
    private boolean manifestWritten;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (round.processingOver()) {
            writeManifest();
            return false;
        }
        for (Element e : round.getElementsAnnotatedWith(BmcContractsFor.class)) {
            if (e instanceof TypeElement) {
                generateFor((TypeElement) e);
            }
        }
        return false;
    }

    private void generateFor(TypeElement contractType) {
        String contractFqn = contractType.getQualifiedName().toString();
        if (!generated.add(contractFqn)) {
            return;
        }
        TypeElement target = targetOf(contractType);
        if (target == null) {
            return; // error already reported
        }
        String targetFqn = target.getQualifiedName().toString();
        String targetInternal = internalName(target);
        String packageName = processingEnv.getElementUtils().getPackageOf(contractType)
                .getQualifiedName().toString();

        String stubSimple = contractType.getSimpleName() + "__BmcStubs";
        String enforceSimple = contractType.getSimpleName() + "__BmcEnforce";
        String stubInternal = qualify(packageName, stubSimple).replace('.', '/');

        List<ContractStubGenerator.Contract> contracts = new ArrayList<>();
        List<String> contractRecords = new ArrayList<>();

        for (Element member : contractType.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement mirror = (ExecutableElement) member;
            Requires requires = mirror.getAnnotation(Requires.class);
            Ensures ensures = mirror.getAnnotation(Ensures.class);
            if (requires == null && ensures == null) {
                continue; // a predicate method (or other), not a contract mirror
            }
            String name = mirror.getSimpleName().toString();
            List<Map.Entry<String, String>> params = new ArrayList<>();
            for (VariableElement p : mirror.getParameters()) {
                params.add(new AbstractMap.SimpleImmutableEntry<>(
                        typeSource(p.asType()), p.getSimpleName().toString()));
            }
            // Per-method @ExpectEnforce wins over the type-level expectEnforce default, so one
            // contract type can mix a deliberately-false demo mirror with genuine contracts.
            org.bmc4j.ExpectEnforce methodExpect = mirror.getAnnotation(org.bmc4j.ExpectEnforce.class);
            String expectEnforce = methodExpect != null
                    ? methodExpect.value().name()
                    : contractType.getAnnotation(BmcContractsFor.class).expectEnforce().name();
            contracts.add(new ContractStubGenerator.Contract(targetFqn, contractFqn, name,
                    typeSource(mirror.getReturnType()), params,
                    requires == null ? null : requires.value(),
                    ensures == null ? null : ensures.value(),
                    expectEnforce));
            contractRecords.add(ContractManifest.contractLine(
                    targetInternal, name, descriptor(mirror), stubInternal, name + "__stub"));
        }
        if (contracts.isEmpty()) {
            warn(contractType, "@BmcContractsFor type has no @Requires/@Ensures mirror methods");
            return;
        }

        write(qualify(packageName, stubSimple), contractType,
                ContractStubGenerator.generate(packageName, stubSimple, contracts));
        write(qualify(packageName, enforceSimple), contractType,
                ContractEnforceProofGenerator.generate(packageName, enforceSimple, contracts));
        manifestLines.addAll(contractRecords);
        manifestLines.add(ContractManifest.enforceLine(qualify(packageName, enforceSimple).replace('.', '/')));
    }

    /** The production class named by {@code @BmcContractsFor(value)}. */
    private TypeElement targetOf(TypeElement contractType) {
        try {
            contractType.getAnnotation(BmcContractsFor.class).value(); // throws — value is a Class
            return null;
        } catch (MirroredTypeException mte) {
            TypeMirror m = mte.getTypeMirror();
            if (m instanceof DeclaredType) {
                return (TypeElement) ((DeclaredType) m).asElement();
            }
            warn(contractType, "@BmcContractsFor value must be a class");
            return null;
        }
    }

    private void writeManifest() {
        if (manifestWritten || manifestLines.isEmpty()) {
            return;
        }
        manifestWritten = true;
        try (Writer w = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", ContractManifest.RESOURCE).openWriter()) {
            for (String line : manifestLines) {
                w.write(line);
                w.write('\n');
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "bmc-contracts: failed to write " + ContractManifest.RESOURCE + ": " + e.getMessage());
        }
    }

    private void write(String generatedFqn, TypeElement origin, String source) {
        try (Writer w = processingEnv.getFiler().createSourceFile(generatedFqn, origin).openWriter()) {
            w.write(source);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "bmc-contracts: failed to write " + generatedFqn + ": " + e.getMessage(), origin);
        }
    }

    private void warn(Element at, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "bmc-contracts: " + message, at);
    }

    private static String qualify(String packageName, String simpleName) {
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    private String internalName(TypeElement type) {
        return processingEnv.getElementUtils().getBinaryName(type).toString().replace('.', '/');
    }

    /** Source-usable type name (canonical, generics erased) for codegen. */
    private String typeSource(TypeMirror t) {
        switch (t.getKind()) {
            case ARRAY:
                return typeSource(((ArrayType) t).getComponentType()) + "[]";
            case DECLARED:
                return ((TypeElement) ((DeclaredType) t).asElement()).getQualifiedName().toString();
            default:
                return t.toString(); // primitives, void
        }
    }

    /** JVM method descriptor, e.g. {@code (ILjava/lang/String;)I}. */
    private String descriptor(ExecutableElement method) {
        StringBuilder sb = new StringBuilder("(");
        for (VariableElement p : method.getParameters()) {
            sb.append(typeDescriptor(p.asType()));
        }
        return sb.append(')').append(typeDescriptor(method.getReturnType())).toString();
    }

    private String typeDescriptor(TypeMirror t) {
        switch (t.getKind()) {
            case BOOLEAN: return "Z";
            case BYTE:    return "B";
            case CHAR:    return "C";
            case SHORT:   return "S";
            case INT:     return "I";
            case LONG:    return "J";
            case FLOAT:   return "F";
            case DOUBLE:  return "D";
            case VOID:    return "V";
            case ARRAY:   return "[" + typeDescriptor(((ArrayType) t).getComponentType());
            case DECLARED:
                return "L" + internalName((TypeElement) ((DeclaredType) t).asElement()) + ";";
            default:
                throw new IllegalArgumentException("unsupported type in contract descriptor: " + t);
        }
    }
}
