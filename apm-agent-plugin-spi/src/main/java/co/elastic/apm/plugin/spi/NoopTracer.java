package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

class NoopTracer implements Tracer {

    static final Tracer INSTANCE = new NoopTracer();

    private NoopTracer() {
    }

    @Nullable
    @Override
    public Transaction<?> startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public Transaction<?> startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro) {
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros) {
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public Transaction<?> currentTransaction() {
        return null;
    }

    @Nullable
    @Override
    public AbstractSpan<?> getActive() {
        return null;
    }

    @Nullable
    @Override
    public Span<?> getActiveSpan() {
        return null;
    }

    @Override
    public void captureAndReportException(@Nullable Throwable e, ClassLoader initiatingClassLoader) {
    }

    @Nullable
    @Override
    public String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent) {
        return null;
    }

    @Nullable
    @Override
    public Span<?> getActiveExitSpan() {
        return null;
    }

    @Nullable
    @Override
    public Span<?> createExitChildSpan() {
        return null;
    }

    @Override
    public void endSpan(Span<?> span) {
    }

    @Override
    public void endTransaction(Transaction<?> transaction) {
    }

    @Override
    public <T> T getConfig(Class<T> configuration) {
        return null;
    }

    @Override
    public ObjectPoolFactory getObjectPoolFactory() {
        return null;
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void setServiceInfoForClassLoader(ClassLoader classLoader, ServiceInfo serviceInfo) {
    }

    @Override
    public ServiceInfo getServiceInfoForClassLoader(ClassLoader classLoader) {
        return null;
    }

    @Override
    public ServiceInfo autoDetectedServiceName() {
        return null;
    }

    @Nullable
    @Override
    public <T extends Tracer> T probe(Class<T> type) {
        return null;
    }

    @Override
    public <T extends Tracer> T require(Class<T> type) {
        return null;
    }
}
