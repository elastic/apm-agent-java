package co.elastic.apm.agent.httpclient.v5;

import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public abstract class BaseApacheHttp5EntityInstrumentation extends BaseApacheHttpClient5Instrumentation {
    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("org.apache.hc.core5.http.HttpEntity"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Entity").and(
            nameStartsWith("org.apache.hc")
                .or(nameStartsWith("org.springframework.http")) //spring implements its own HttpEntities
        );
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.hc.core5.http.HttpEntity"));
    }
}
