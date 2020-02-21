package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.HttpUtils;
import co.elastic.apm.agent.util.ExecutorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.util.IOUtils;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Sends a definition of all available dynamic configuration options to APM Server so that the APM Kibana app can offer those options for central configuration
 */
public class ConfigurationDefinitionReporter implements LifecycleListener, Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationDefinitionReporter.class);
    private static final String FAILED_LOG = "Failed to send central configuration definition to APM Server with status {} and message '{}'";
    private final ApmServerClient apmServerClient;

    public ConfigurationDefinitionReporter(ElasticApmTracer tracer) {
        // TODO register ApmServerClient as lifecycle listener or bean
        this(Objects.requireNonNull(tracer.getLifecycleListener(ApmServerClient.class)));
    }

    ConfigurationDefinitionReporter(ApmServerClient apmServerClient) {
        this.apmServerClient = apmServerClient;
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        ThreadPoolExecutor pool = ExecutorUtils.createSingleThreadDeamonPool("server-config-definition-reporter", 1);
        try {
            pool.submit(this);
        } finally {
            pool.shutdown();
        }
    }

    @Override
    public void run() {
        try {
            apmServerClient.execute("/config", new ApmServerClient.ConnectionHandler<Void>() {
                @Nullable
                @Override
                public Void withConnection(HttpURLConnection connection) throws IOException {
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    IOUtils.copy(getClass().getResourceAsStream("/configuration.json"), connection.getOutputStream());
                    int status = -1;
                    try {
                        status = connection.getResponseCode();
                        InputStream inputStream = connection.getInputStream();
                        if (status >= 400) {
                            logger.info(FAILED_LOG, status, HttpUtils.readToString(inputStream));
                        }
                    } catch (FileNotFoundException ignore) {
                        // connected to an old APM Server version
                        logger.debug("This APM Server does not support the configuration definition endpoint");
                    } catch (IOException e) {
                        logger.info(FAILED_LOG, status, HttpUtils.readToString(connection.getErrorStream()));
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
    }
}
