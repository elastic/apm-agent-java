package co.elastic.apm.agent.servlet.helper;

public interface AsyncContextAdviceHelper<T> {

    void onExitStartAsync(T asyncContext);
}
