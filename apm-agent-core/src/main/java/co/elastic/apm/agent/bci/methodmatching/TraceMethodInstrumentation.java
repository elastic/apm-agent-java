package co.elastic.apm.agent.bci.methodmatching;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.matches;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class TraceMethodInstrumentation extends ElasticApmInstrumentation {

    protected final MethodMatcher methodMatcher;

    public TraceMethodInstrumentation(MethodMatcher methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onMethodEnter(@SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
                                     @Advice.Local("span") AbstractSpan<?> span) {
        if (tracer != null) {
            final AbstractSpan<?> parent = tracer.activeSpan();
            if (parent == null) {
                span = tracer.startTransaction()
                    .withName(signature)
                    .activate();
            } else {
                span = parent.createSpan()
                    .withName(signature)
                    .activate();
            }
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onMethodExit(@Nullable @Advice.Local("span") AbstractSpan<?> span,
                                    @Advice.Thrown Throwable t) {
        if (span != null) {
            span.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return matches(methodMatcher.getClassMatcher())
            .and(declaresMethod(matches(methodMatcher.getMethodMatcher())));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        ElementMatcher.Junction<? super MethodDescription> matcher = matches(methodMatcher.getMethodMatcher());
        if (methodMatcher.getModifier() != null) {
            switch (methodMatcher.getModifier()) {
                case Modifier.PUBLIC:
                    matcher = matcher.and(ElementMatchers.isPublic());
                    break;
                case Modifier.PROTECTED:
                    matcher = matcher.and(ElementMatchers.isProtected());
                    break;
                case Modifier.PRIVATE:
                    matcher = matcher.and(ElementMatchers.isPrivate());
                    break;
            }
        }
        if (methodMatcher.getArgumentMatchers() != null) {
            matcher = matcher.and(takesArguments(methodMatcher.getArgumentMatchers().size()));
            List<WildcardMatcher> argumentMatchers = methodMatcher.getArgumentMatchers();
            for (int i = 0; i < argumentMatchers.size(); i++) {
                matcher = matcher.and(takesArgument(i, matches(argumentMatchers.get(i))));
            }
        }
        return matcher;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("method-matching");
    }
}
