package co.elastic.apm.agent.bci.bytebuddy;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import javax.annotation.Nullable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class ClassAnnotationValueOffsetMappingFactory implements Advice.OffsetMapping.Factory<ClassAnnotationValueOffsetMappingFactory.ClassAnnotationValueExtractor>{

    @Override
    public Class<ClassAnnotationValueExtractor> getAnnotationType() {
        return ClassAnnotationValueExtractor.class;
    }

    @Override
    public Advice.OffsetMapping make(ParameterDescription.InDefinedShape target, AnnotationDescription.Loadable<ClassAnnotationValueExtractor> annotation, AdviceType adviceType) {
        return new Advice.OffsetMapping() {
            @Override
            public Target resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod, Assigner assigner,
                                  Advice.ArgumentHandler argumentHandler, Sort sort) {
                return Target.ForStackManipulation.of(getAnnotationValue(instrumentedType, annotation.loadSilent()));
            }
        };
    }

    @Nullable
    private Object getAnnotationValue(TypeDescription instrumentedType, ClassAnnotationValueExtractor classAnnotationValueExtractor) {
        for (TypeDescription typeDescription : instrumentedType.getDeclaredAnnotations().asTypeList()) {
            if (named(classAnnotationValueExtractor.annotationClassName()).matches(typeDescription)) {
                for (MethodDescription.InDefinedShape annotationMethod : typeDescription.getDeclaredMethods()) {
                    if (annotationMethod.getName().equals(classAnnotationValueExtractor.method())) {
                        return instrumentedType.getDeclaredAnnotations().ofType(typeDescription).getValue(annotationMethod).resolve();
                    }
                }
            }
        }
        return null;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface ClassAnnotationValueExtractor{
        String annotationClassName();

        String method();
    }

}
