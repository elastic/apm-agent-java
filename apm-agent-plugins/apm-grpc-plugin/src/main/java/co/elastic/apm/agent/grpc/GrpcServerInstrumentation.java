package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;

public class GrpcServerInstrumentation extends ElasticApmInstrumentation {

    private final Collection<String> applicationPackages;

    public GrpcServerInstrumentation(ElasticApmTracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        // --> generated class, we should be able to rely on implementation details here (annotations, methods, ...)
        // public static abstract class HelloImplBase implements io.grpc.BindableService {}
        //
        // --> the class that actually implements the service (might be part of HelloImplBase hierarchy
        // HelloGrpcImpl extends HelloGrpc.HelloImplBase
        return isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>any());
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return null;
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return null;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return null;
    }
}
