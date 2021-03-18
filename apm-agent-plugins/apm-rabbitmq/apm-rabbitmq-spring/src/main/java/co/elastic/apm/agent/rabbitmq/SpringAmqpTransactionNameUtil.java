package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;

@GlobalState
public class SpringAmqpTransactionNameUtil {

    private static final WeakConcurrentSet<Object> rabbitListeners = WeakMapSupplier.createSet();

    public static String getTransactionNamePrefix(Object listener) {
        return rabbitListeners.contains(listener) ? "RabbitMQ" : AmqpConstants.SPRING_AMQP_TRANSACTION_PREFIX;
    }

    public static void register(Object listener) {
        rabbitListeners.add(listener);
    }
}
