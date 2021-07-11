package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.servlet.ServletApiAdvice;

public final class AsyncConstants {

    private AsyncConstants() {}

    protected static final String ASYNC_LISTENER_ADDED = ServletApiAdvice.class.getName() + ".asyncListenerAdded";
    protected static final int MAX_POOLED_ELEMENTS = 256;

}
