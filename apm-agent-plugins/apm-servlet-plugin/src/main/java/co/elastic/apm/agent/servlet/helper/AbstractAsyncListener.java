package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.servlet.ServletTransactionHelper;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

public class AbstractAsyncListener {

    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final CommonAsyncContextAdviceHelper asyncContextAdviceHelperImpl;
    private final ServletTransactionHelper servletTransactionHelper;
    @Nullable
    private volatile Transaction transaction;
    @Nullable
    private volatile Throwable throwable;

    AbstractAsyncListener withTransaction(Transaction transaction) {
        this.transaction = transaction;
        return this;
    }

}
