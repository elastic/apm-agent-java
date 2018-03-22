package co.elastic.apm.report;

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;

import javax.annotation.Nullable;

import static co.elastic.apm.report.ReportingEvent.ReportingEventType.ERROR;
import static co.elastic.apm.report.ReportingEvent.ReportingEventType.FLUSH;
import static co.elastic.apm.report.ReportingEvent.ReportingEventType.TRANSACTION;


public class ReportingEvent {
    @Nullable
    private Transaction transaction;
    @Nullable
    private ReportingEventType type;
    @Nullable
    private ErrorCapture error;

    public void resetState() {
        this.transaction = null;
        this.type = null;
        this.error = null;
    }

    @Nullable
    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
        this.type = TRANSACTION;
    }

    public void setFlushEvent() {
        this.type = FLUSH;
    }

    @Nullable
    public ReportingEventType getType() {
        return type;
    }

    @Nullable
    public ErrorCapture getError() {
        return error;
    }

    public void setError(ErrorCapture error) {
        this.error = error;
        this.type = ERROR;
    }

    enum ReportingEventType {
        FLUSH, TRANSACTION, ERROR
    }
}
