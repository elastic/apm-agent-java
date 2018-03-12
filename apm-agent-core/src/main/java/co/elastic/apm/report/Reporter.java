package co.elastic.apm.report;

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;

import java.io.Closeable;
import java.util.concurrent.Future;

public interface Reporter extends Closeable {
    void report(Transaction transaction);

    int getDropped();

    Future<Void> flush();

    @Override
    void close();

    void report(ErrorCapture error);
}
