package co.elastic.apm.bci.bytebuddy;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * Enables using {@link SimpleMethodSignature} in {@link net.bytebuddy.asm.Advice.OnMethodEnter} and
 * {@link net.bytebuddy.asm.Advice.OnMethodExit} methods.
 */
public class SimpleMethodSignatureOffsetMappingFactory implements Advice.OffsetMapping.Factory<SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature> {

    @Override
    public Class<SimpleMethodSignature> getAnnotationType() {
        return SimpleMethodSignature.class;
    }

    @Override
    public Advice.OffsetMapping make(ParameterDescription.InDefinedShape target,
                                     AnnotationDescription.Loadable<SimpleMethodSignature> annotation,
                                     AdviceType adviceType) {
        return new Advice.OffsetMapping() {
            @Override
            public Target resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod, Assigner assigner,
                                  Advice.ArgumentHandler argumentHandler, Sort sort) {
                final String className = instrumentedMethod.getDeclaringType().getTypeName();
                final String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
                final String signature = String.format("%s#%s", simpleClassName, instrumentedMethod.getName());
                return Target.ForStackManipulation.of(signature);
            }
        };
    }

    /**
     * Indicates that the annotated parameter should be mapped to a string representation of the instrumented method,
     * a constant representing {@link Class#getSimpleName()}{@code #}{@link Method#getName()},
     * for example {@code FooClass#barMethod}
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface SimpleMethodSignature {
    }

}
