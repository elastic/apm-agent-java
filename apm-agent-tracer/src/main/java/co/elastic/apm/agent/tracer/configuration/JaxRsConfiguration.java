package co.elastic.apm.agent.tracer.configuration;

public interface JaxRsConfiguration {

    /**
     * @return if true, the jax-rs plugin must scan for @Path annotations in the class hierarchy of classes.
     * if false, only @Path annotations on implementation classes are considered.
     */
    boolean isEnableJaxrsAnnotationInheritance();

    boolean isUseJaxRsPathForTransactionName();
}
