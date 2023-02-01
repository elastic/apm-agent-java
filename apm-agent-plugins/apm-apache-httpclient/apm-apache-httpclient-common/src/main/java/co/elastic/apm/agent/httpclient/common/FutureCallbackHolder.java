package co.elastic.apm.agent.httpclient.common;

public class FutureCallbackHolder<FutureCallback> {

    private FutureCallback delegate;

    public FutureCallback getDelegate() {
        return delegate;
    }

    public void setDelegate(FutureCallback delegate) {
        this.delegate = delegate;
    }
}
