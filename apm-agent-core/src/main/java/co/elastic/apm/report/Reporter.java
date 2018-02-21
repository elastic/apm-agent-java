package co.elastic.apm.report;

import co.elastic.apm.impl.Transaction;

import java.io.Closeable;

public interface Reporter extends Closeable {
    void report(Transaction transaction);

    int getDropped();

    void scheduleFlush();

    void close();
}
