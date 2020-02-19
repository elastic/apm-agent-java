package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;

/**
 * Sends a definition of all available dynamic configuration options to APM Server so that the APM Kibana app can offer those options for central configuration
 */
public class ConfigurationDefinitionReporter extends AbstractLifecycleListener {
    protected ConfigurationDefinitionReporter(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        tracer.getConfigurationRegistry();
    }
}
