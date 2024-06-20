package co.elastic.apm.agent.httpclient.v4;

import co.elastic.apm.agent.httpclient.RequestBodyRecordingInputStream;
import co.elastic.apm.agent.httpclient.RequestBodyRecordingOutputStream;
import co.elastic.apm.agent.httpclient.v4.helper.RequestBodyCaptureRegistry;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpEntity;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApacheHttpEntityGetContentInstrumentation extends BaseApacheHttpClientInstrumentation {

    public static class ApacheHttpEntityGetContentAdvice {

        private static final Logger logger = LoggerFactory.getLogger(ApacheHttpEntityGetContentAdvice.class);

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToReturned
        public static InputStream onExit(@Advice.This HttpEntity thiz, @Advice.Return InputStream content) throws URISyntaxException {
            Span<?> clientSpan = RequestBodyCaptureRegistry.removeSpanFor(thiz);
            if (clientSpan != null) {
                logger.debug("Wrapping input stream for request body capture for HttpEntity {} ({}) for span {}", thiz.getClass().getName(), System.identityHashCode(thiz), clientSpan);
                return new RequestBodyRecordingInputStream(content, clientSpan);
            }
            return content;
        }
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.httpclient.v4.ApacheHttpEntityGetContentInstrumentation$ApacheHttpEntityGetContentAdvice";
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.apache.http.HttpEntity"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("org.apache.http").and(nameContains("Entity"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.http.HttpEntity"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getContent")
            .and(takesArguments(0));
    }

}
