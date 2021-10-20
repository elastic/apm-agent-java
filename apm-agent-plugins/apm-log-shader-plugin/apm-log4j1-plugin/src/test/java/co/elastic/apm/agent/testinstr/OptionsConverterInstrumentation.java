package co.elastic.apm.agent.testinstr;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link org.apache.log4j.helpers.OptionConverter} to make {@link org.apache.log4j.helpers.Loader} work as
 * expected on Java 17+. As log4j1 is now EOL http://logging.apache.org/log4j/1.2/ it's the best way to keep our tests
 * active and relevant on this feature.
 */
public class OptionsConverterInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.log4j.helpers.OptionConverter");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getSystemProperty");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.emptyList();
    }

    public static class AdviceClass {

        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static String onExit(@Advice.Return @Nullable String returnValue) {
            if (returnValue == null) {
                return null;
            }

            if (returnValue.indexOf('.') < 0) {
                return returnValue + ".0";
            } else {
                return returnValue;
            }
        }
    }
}
