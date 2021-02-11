package co.elastic.apm.agent.reactor;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TracedSubscriberTest extends AbstractInstrumentationTest {

    @Nullable
    private Transaction transaction;

    private Scheduler scheduler;

    @BeforeEach
    void before() {
        scheduler = Schedulers.newParallel("test-scheduler", 16);

        TracedSubscriber.registerHooks(tracer);
    }

    @AfterEach
    void after() {
        if (transaction != null) {
            transaction.deactivate().end();
        }

        TracedSubscriber.unregisterHooks();
    }


    // TODO
    // activate context within onError, onNext, onSubscribe

    @Test
    void contextPropagation_Flux_Map_Zip() {

        startAndActivateRootTransaction();

        Function<Integer, Integer> mapFunction = i -> {
            checkActiveContext(transaction);
            slowOperation();
            return i + 1;
        };

        Flux<Integer> flux = Flux.just(1, 2, 3)
            .delayElements(Duration.ofMillis(1))
            .zipWith(Flux.range(1, Integer.MAX_VALUE), (a, b) -> {
                checkActiveContext(transaction);
                return a + b;
            })
            .map(mapFunction);

        StepVerifier.create(parallelize(flux))
            .expectNext(3)
            .expectNext(5)
            .expectNext(7)
            .verifyComplete();

    }

    @Test
    void contextPropagation_Flux_error() {
        Throwable error = new RuntimeException("hello");

        startAndActivateRootTransaction();

        Flux.error(error).subscribe(new BaseSubscriber<>() {

            @Override
            protected void hookOnError(Throwable throwable) {
                assertThat(throwable).isSameAs(error);
                checkActiveContext(transaction);
            }
        });
    }

    @Test
    void ignoreScalar() {
        throw new IllegalStateException("TODO");
    }

    @Test
    void ignoreNoActiveContext() {

        // will throw an NPE due to trying to activate/deactive a null context
        Flux.just(1, 2, 3)
            .subscribe();
    }

    private void startAndActivateRootTransaction() {
        transaction = tracer.startRootTransaction(null);
        assertThat(transaction).isNotNull();
        transaction.activate();
    }

    private static void checkActiveContext(AbstractSpan<?> expectedActive) {
        assertThat(tracer.getActive())
            .describedAs("active context not available")
            .isNotNull()
            .describedAs("active context is not the one we expect")
            .isEqualTo(expectedActive);
    }

    /**
     * Make flux execution more parallel to encourage concurrency/threading bugs
     *
     * @param flux flux to wrap
     * @param <T>  flux content type
     * @return parallel flux
     */
    private <T> ParallelFlux<T> parallelize(Flux<T> flux) {
        return flux
            .parallel(5)
            .runOn(scheduler);
    }

    /**
     * Emulate a rather slow operation to encourage more concurrency/threading bugs
     */
    private static void slowOperation() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {

        }
    }

}
