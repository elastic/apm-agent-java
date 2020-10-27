package co.elastic.apm.agent.mdc;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class MdcClearInstrumentation extends ElasticApmInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(MdcClearInstrumentation.class);

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org!slf4j!MDC".replace('!', '.'))
            .or(named("org.apache.log4j.MDC"))
            .or(named("org.apache.logging.log4j.ThreadContext"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("clear")
            .or(named("clearMap"))
            .or(named("clearStack"))
            .or(named("remove"))
            .or(named("removeAll"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("debug-mdc");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Origin String origin, @Advice.AllArguments Object[] args) {
        log.debug("MDC clear/remove operation called {} args = {}", origin, Arrays.toString(args), new Throwable("debug exception to get caller stack trace"));
    }

}
