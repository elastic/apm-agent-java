package co.elastic.apm.report;

import co.elastic.apm.impl.ErrorCapture;
import co.elastic.apm.impl.Transaction;

import java.io.Closeable;
import java.util.concurrent.Future;

public interface Reporter extends Closeable {
    void report(Transaction transaction);

    int getDropped();

    Future<Void> flush();

    void close();

    void report(ErrorCapture error);
}
