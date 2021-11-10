package co.elastic.apm.agent.jsf;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class AbstractJsfLifecycleRenderInstrumentation extends AbstractJsfLifecycleInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("render")
            .and(takesArguments(1))
            .and(takesArgument(0, named(facesContextClassName())));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        List<String> ret = new ArrayList<>(super.getInstrumentationGroupNames());
        ret.add("render");
        return ret;
    }

    abstract String facesContextClassName();
}
