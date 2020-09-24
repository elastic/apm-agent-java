package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments
 * <ul>
 *     <li>{@link Connection#createChannel}</li>
 * </ul>
 */
public class ConnectionInstrumentation extends BaseInstrumentation {

    private static final List<Class<? extends ElasticApmInstrumentation>> CHANNEL_INSTRUMENTATIONS = Arrays.asList(
        ChannelInstrumentation.BasicPublish.class,
        ChannelInstrumentation.BasicConsume.class
    );

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        // this fast heuristic works for all implementations within driver
        return nameEndsWith("Connection")
            .and(nameStartsWith("com.rabbitmq.client.impl"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // fine to use super type matching thanks to restricting with pre-filter
        return hasSuperType(named("com.rabbitmq.client.Connection"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("createChannel");
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Nullable @Advice.Thrown Throwable thrown,
                              @Advice.Return @Nullable Channel channel) {

        if (thrown != null || channel == null) {
            return;
        }
        DynamicTransformer.Accessor.get().ensureInstrumented(channel.getClass(), CHANNEL_INSTRUMENTATIONS);
    }

}
