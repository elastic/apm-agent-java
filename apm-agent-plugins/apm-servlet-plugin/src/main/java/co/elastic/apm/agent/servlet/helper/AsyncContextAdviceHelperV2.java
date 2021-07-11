package co.elastic.apm.agent.servlet.helper;

public interface AsyncContextAdviceHelperV2<T> {

    void onExitStartAsync(T asyncContext);
}
