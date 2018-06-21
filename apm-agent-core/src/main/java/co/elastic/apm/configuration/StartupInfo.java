package co.elastic.apm.configuration;

import co.elastic.apm.context.LifecycleListener;
import co.elastic.apm.impl.ElasticApmTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;

/**
 * Logs system information and configuration on startup.
 * <p>
 * Based on {@code org.stagemonitor.core.Stagemonitor} and {@code org.stagemonitor.core.configuration.ConfigurationLogger},
 * under Apache license 2.0.
 * </p>
 */
public class StartupInfo implements LifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(StartupInfo.class);

    private static String getJvmAndOsVersionString() {
        return "Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ") " +
            System.getProperty("os.name") + " " + System.getProperty("os.version");
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        logger.info("Starting Elastic APM on {}", getJvmAndOsVersionString());
        for (ConfigurationOption<?> option : tracer.getConfigurationRegistry().getConfigurationOptionsByKey().values()) {
            if (!option.isDefault()) {
                logger.debug("{}: '{}' (source: {})", option.getKey(),
                    option.isSensitive() ? "XXXX" : option.getValueAsSafeString(),
                    option.getNameOfCurrentConfigurationSource());
            }
        }
    }

    @Override
    public void stop() throws Exception {
        // noop
    }
}
