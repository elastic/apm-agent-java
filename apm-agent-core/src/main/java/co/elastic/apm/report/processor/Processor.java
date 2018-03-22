package co.elastic.apm.report.processor;

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;
import org.stagemonitor.configuration.ConfigurationRegistry;

/**
 * A processor is executed right before a event (a {@link Transaction} or {@link Error}) gets reported.
 * <p>
 * You can use this for example to sanitize certain information.
 * </p>
 */
public interface Processor {

    /**
     * This method is called so that the processor can initialize configuration before the {@link #processBeforeReport} methods are called.
     *
     * @param tracer A reference to the {@link ConfigurationRegistry} which can be used to get configuration options.
     */
    void init(ConfigurationRegistry tracer);

    /**
     * This method is executed before the provided {@link Transaction} is reported.
     *
     * @param transaction The transaction to process.
     */
    void processBeforeReport(Transaction transaction);

    /**
     * This method is executed before the provided {@link ErrorCapture} is reported.
     *
     * @param error The error to process.
     */
    void processBeforeReport(ErrorCapture error);

}
