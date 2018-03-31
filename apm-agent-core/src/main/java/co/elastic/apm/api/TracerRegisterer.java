package co.elastic.apm.api;

import co.elastic.apm.impl.ElasticApmTracer;

/**
 * This class is only intended to be used by {@link ElasticApmTracer} to register itself to the public API {@link ElasticApm}
 */
public class TracerRegisterer {
    public static void register(ElasticApmTracer tracer) {
        ElasticApm.get().register(tracer);
    }

    public static void unregister() {
        ElasticApm.get().unregister();
    }
}
