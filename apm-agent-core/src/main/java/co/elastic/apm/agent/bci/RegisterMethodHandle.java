package co.elastic.apm.agent.bci;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Static methods annotated with this will be registered in {@link co.elastic.apm.agent.bootstrap.MethodHandleDispatcher}.
 * This enables advices to call them by looking them up via {@link co.elastic.apm.agent.bootstrap.MethodHandleDispatcher#getMethodHandle(Class, String)}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RegisterMethodHandle {
}
