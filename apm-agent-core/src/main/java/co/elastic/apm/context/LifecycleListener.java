package co.elastic.apm.context;

import co.elastic.apm.impl.ElasticApmTracer;

/**
 * A {@link LifecycleListener} notifies about the start and stop event of the {@link ElasticApmTracer}.
 * <p>
 * Implement this interface and register it as a {@linkplain java.util.ServiceLoader service} under
 * <code>src/main/resources/META-INF/services/co.elastic.apm.context.LifecycleListener</code>.
 * </p>
 */
public interface LifecycleListener {

    /**
     * Callback for when the {@link ElasticApmTracer} starts.
     *
     * @param tracer The tracer.
     */
    void start(ElasticApmTracer tracer);

    /**
     * Callback for when {@link ElasticApmTracer#stop()} has been called.
     * <p>
     * Typically, this method is used to clean up resources like thread pools
     * so that there are no class loader leaks when a webapp is redeployed in an application server.
     * </p>
     *
     * @throws Exception When something goes wrong performing the cleanup.
     */
    void stop() throws Exception;
}
