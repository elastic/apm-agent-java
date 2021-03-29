package co.elastic.apm.agent.reactor;

import co.elastic.apm.agent.impl.transaction.Transaction;
import org.reactivestreams.Subscriber;
import reactor.core.CoreSubscriber;

import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class SubscriberWrappingTest {

    public static void testLifecycle(Transaction context, boolean isActive, boolean endOnComplete, boolean keepActive, Function<CoreSubscriber<?>, CoreSubscriber<?>> wrapper) {
        assertThat(context.getReferenceCount())
            .describedAs("provided context should not be active")
            .isEqualTo(1);

        if (isActive) {
            context.activate();
        }

        testLifecycle(isActive, true, context, wrapper, endOnComplete, keepActive);
        testLifecycle(isActive, false, context, wrapper, endOnComplete, keepActive);
        testDiscardOnFirstException(isActive, context, wrapper);

        assertThat(context.getReferenceCount())
            .describedAs("context should be active at end of lifecycle test")
            .isEqualTo(isActive ? 2 : 1);

        if (isActive) {
            context.deactivate();
        }

    }

    private static void testDiscardOnFirstException(boolean isActive, Transaction context, Function<CoreSubscriber<?>, CoreSubscriber<?>> wrapper) {
        for (SubscriberMethod method : SubscriberMethod.values()) {
            CoreSubscriber<?> subscriber = mock(CoreSubscriber.class);

            checkReferenceCount(context, isActive ? 2 : 1, "unexpected reference count");

            CoreSubscriber<?> wrapped = wrapper.apply(subscriber);

            checkReferenceCount(context, isActive ? 3 : 2, "after wrapping");

            Throwable expectedException = new RuntimeException("boom");

            method.invoke(doThrow(expectedException).when(subscriber));

            // execute twice to check what happens once it's discarded
            for (int i = 0; i < 2; i++) {
                Throwable thrown = null;
                try {
                    method.invoke(wrapped);
                } catch (Throwable e) {
                    thrown = e;
                }
                assertThat(thrown).isSameAs(expectedException);
            }

            checkReferenceCount(context, isActive ? 2 : 1, "should discard and decrement references when %s throws exception", method);
        }
    }

    private enum SubscriberMethod {
        onSubscribe((s) -> s.onSubscribe(null)),
        onNext((s) -> s.onNext(null)),
        onError((s) -> s.onError(null)),
        onComplete(Subscriber::onComplete);

        private final Consumer<CoreSubscriber<?>> methodCall;

        SubscriberMethod(Consumer<CoreSubscriber<?>> methodCall) {
            this.methodCall = methodCall;
        }

        void invoke(CoreSubscriber<?> subscriber) {
            methodCall.accept(subscriber);
        }

    }

    private static void testLifecycle(boolean isActive, boolean endError, Transaction context, Function<CoreSubscriber<?>, CoreSubscriber<?>> wrapper, boolean endOnComplete, boolean keepActive) {

        int initialCount = isActive ? 2 : 1;
        int afterWrapping = initialCount + 1;
        int inFlight;
        if (isActive) {
            // no extra reference if it was already active
            inFlight = afterWrapping;
        } else if (keepActive) {
            // keeping active when not already active adds an extra reference
            // due to the activation not being terminated.
            // When already active, keeping it active does not add any extra
            inFlight = afterWrapping + 1;
        } else {
            // otherwise we expect to only have wrapping
            inFlight = afterWrapping;
        }

        checkReferenceCount(context, initialCount, "reference count before wrapping");

        CoreSubscriber<?> wrapped = wrapper.apply(mock(CoreSubscriber.class));

        checkReferenceCount(context, afterWrapping, "reference count after wrapping");

        wrapped.onSubscribe(null);

        checkReferenceCount(context, inFlight, "reference count after onSubscribe");

        wrapped.onNext(null);

        checkReferenceCount(context, inFlight, "reference count after onNext");

        if (endError) {
            wrapped.onError(null);
            checkReferenceCount(context, initialCount, "reference count after onError");
        } else {
            wrapped.onComplete();
            checkReferenceCount(context, initialCount, "reference count after onComplete");
        }
    }

    private static void checkReferenceCount(Transaction t, int count, String desc, Object... descArgs) {
        assertThat(t.getReferenceCount())
            .describedAs(desc, descArgs)
            .isEqualTo(count);
    }

}
