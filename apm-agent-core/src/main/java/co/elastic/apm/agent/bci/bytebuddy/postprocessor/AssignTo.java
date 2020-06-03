package co.elastic.apm.agent.bci.bytebuddy.postprocessor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AssignTo {
    AssignToArgument[] arguments() default {};
    AssignToField[] fields() default {};
    AssignToReturn[] returns() default {};
}
