package co.elastic.apm.agent.bci;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotating a class with {@link GlobalState} excludes it from being loaded by the plugin class loader.
 * It will instead be loaded by the agent class loader, which is currently the bootstrap class loader, although that is subject to change.
 * This will make it's static variables globally available instead of being local to the plugin class loader.
 * <p>
 * Normally, all classes within an instrumentation plugin are loaded from a dedicated class loader
 * that is the child of the class loader that contains the instrumented classes.
 * If there are multiple class loaders that are instrumented with a given instrumentation plugin,
 * the instrumentation classes will also be loaded by multiple class loaders.
 * The effect of that is that state added to static variables in one class loader does not affect the static variable in other class loaders.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GlobalState {
}
