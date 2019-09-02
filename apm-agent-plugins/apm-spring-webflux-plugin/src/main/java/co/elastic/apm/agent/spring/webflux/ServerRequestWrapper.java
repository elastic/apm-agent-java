package co.elastic.apm.agent.spring.webflux;

import co.elastic.apm.agent.impl.transaction.Transaction;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpCookie;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ServerRequestWrapper implements ServerRequest {

    private final ServerRequest serverRequest;

    private Transaction transaction;

    public ServerRequestWrapper(ServerRequest serverRequest) {
        this.serverRequest = serverRequest;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    @Override
    public String methodName() {
        return serverRequest.methodName();
    }

    @Override
    public URI uri() {
        return serverRequest.uri();
    }

    @Override
    public UriBuilder uriBuilder() {
        return serverRequest.uriBuilder();
    }

    @Override
    public Headers headers() {
        return serverRequest.headers();
    }

    @Override
    public MultiValueMap<String, HttpCookie> cookies() {
        return serverRequest.cookies();
    }

    @Override
    public Optional<InetSocketAddress> remoteAddress() {
        return serverRequest.remoteAddress();
    }

    @Override
    public List<HttpMessageReader<?>> messageReaders() {
        return serverRequest.messageReaders();
    }

    @Override
    public <T> T body(BodyExtractor<T, ? super ServerHttpRequest> extractor) {
        return serverRequest.body(extractor);
    }

    @Override
    public <T> T body(BodyExtractor<T, ? super ServerHttpRequest> extractor, Map<String, Object> hints) {
        return serverRequest.body(extractor, hints);
    }

    @Override
    public <T> Mono<T> bodyToMono(Class<? extends T> elementClass) {
        return serverRequest.bodyToMono(elementClass);
    }

    @Override
    public <T> Mono<T> bodyToMono(ParameterizedTypeReference<T> typeReference) {
        return serverRequest.bodyToMono(typeReference);
    }

    @Override
    public <T> Flux<T> bodyToFlux(Class<? extends T> elementClass) {
        return serverRequest.bodyToFlux(elementClass);
    }

    @Override
    public <T> Flux<T> bodyToFlux(ParameterizedTypeReference<T> typeReference) {
        return serverRequest.bodyToFlux(typeReference);
    }

    @Override
    public Map<String, Object> attributes() {
        return serverRequest.attributes();
    }

    @Override
    public MultiValueMap<String, String> queryParams() {
        return serverRequest.queryParams();
    }

    @Override
    public Map<String, String> pathVariables() {
        return serverRequest.pathVariables();
    }

    @Override
    public Mono<WebSession> session() {
        return serverRequest.session();
    }

    @Override
    public Mono<? extends Principal> principal() {
        return serverRequest.principal();
    }

    @Override
    public Mono<MultiValueMap<String, String>> formData() {
        return serverRequest.formData();
    }

    @Override
    public Mono<MultiValueMap<String, Part>> multipartData() {
        return serverRequest.multipartData();
    }

    @Override
    public ServerWebExchange exchange() {
        return serverRequest.exchange();
    }
}
