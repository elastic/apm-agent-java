package co.elastic.apm.agent.universalprofiling;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.UniversalProfilingConfiguration;
import co.elastic.apm.agent.impl.ActivationListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.metadata.SystemInfo;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.ElasticContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.otel.JvmtiAccess;
import co.elastic.otel.UniversalProfilingCorrelation;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class UniversalProfilingIntegration {

    private static final Logger log = LoggerFactory.getLogger(UniversalProfilingIntegration.class);

    private volatile ElasticApmTracer tracer;

    // Visible for testing
    volatile boolean isActive = false;

    private ActivationListener activationListener = new ActivationListener() {

        @Override
        public void beforeActivate(AbstractSpan<?> span) {
            ProfilerSharedMemoryWriter.updateThreadCorrelationStorage(span);
        }

        @Override
        public void afterDeactivate(@Nullable AbstractSpan<?> deactivatedSpan) {
            ElasticContext<?> currentContext = tracer.currentContext();
            ProfilerSharedMemoryWriter.updateThreadCorrelationStorage(currentContext.getSpan());
        }
    };

    public void start(ElasticApmTracer tracer) {
        this.tracer = tracer;
        UniversalProfilingConfiguration config = tracer.getConfig(UniversalProfilingConfiguration.class);
        if (!config.isEnabled()) {
            return;
        }
        if (SystemInfo.isWindows(System.getProperty("os.name"))) {
            log.warn("Universal profiling integration cannot be enabled on windows");
            return;
        }
        try {
            log.debug("Starting universal profiling correlation");

            CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);
            ByteBuffer processCorrelationStorage = ProfilerSharedMemoryWriter.generateProcessCorrelationStorage(
                coreConfig.getServiceName(), coreConfig.getEnvironment(), "");
            UniversalProfilingCorrelation.setProcessStorage(processCorrelationStorage);
        } catch (Exception e) {
            log.error("Failed to start universal profiling integration", e);
        }
        isActive = true;
        tracer.registerSpanListener(activationListener);
    }

    public void stop() {
        try {
            if (isActive) {
                JvmtiAccess.destroy();
            }
        } catch (Exception e) {
            log.error("Failed to stop universal profiling integration", e);
        }
    }

    public void afterTransactionStart(Transaction startedTransactions) {
        //TODO: store the transaction in a map for correlating with profiling data
    }

    public void correlateAndReport(Transaction endedTransaction) {
        //TODO: perform correlation and report after buffering for a certain delay
        tracer.getReporter().report(endedTransaction);
    }

    public void drop(Transaction endedTransaction) {
        //TODO: remove dropped transactions from correlation storage without reporting
    }
}
