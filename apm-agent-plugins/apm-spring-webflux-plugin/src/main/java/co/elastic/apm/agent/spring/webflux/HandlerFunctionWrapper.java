package co.elastic.apm.agent.spring.webflux;

import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public class HandlerFunctionWrapper<T extends ServerResponse> implements HandlerFunction<T> {

    private final HandlerFunction handlerFunction;

    public HandlerFunctionWrapper(HandlerFunction handlerFunction) {
        this.handlerFunction = handlerFunction;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<T> handle(ServerRequest request) {
        return (Mono<T>) handlerFunction.handle(request);
    }
}
