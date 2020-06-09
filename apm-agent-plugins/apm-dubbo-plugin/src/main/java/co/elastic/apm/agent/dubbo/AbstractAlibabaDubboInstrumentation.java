package co.elastic.apm.agent.dubbo;

import net.bytebuddy.matcher.ElementMatcher;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;

public abstract class AbstractAlibabaDubboInstrumentation extends AbstractDubboInstrumentation {
    // these type is available as of dubbo 2.5.0
    private static final ElementMatcher.Junction<ClassLoader> CAN_LOAD_FUTURE_FILTER = classLoaderCanLoadClass("com.alibaba.dubbo.rpc.protocol.dubbo.filter.FutureFilter");

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return CAN_LOAD_FUTURE_FILTER;
    }
}
