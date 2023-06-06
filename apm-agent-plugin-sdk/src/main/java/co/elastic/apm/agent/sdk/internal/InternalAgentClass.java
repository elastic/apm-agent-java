package co.elastic.apm.agent.sdk.internal;

public interface InternalAgentClass {

    String INTERNAL_PLUGIN_CLASS_LOADER = "INTERNAL_PLUGIN_CLASS_LOADER";
    String CLASS_LOADER = "CLASS_LOADER";

    String getMarker();
}
