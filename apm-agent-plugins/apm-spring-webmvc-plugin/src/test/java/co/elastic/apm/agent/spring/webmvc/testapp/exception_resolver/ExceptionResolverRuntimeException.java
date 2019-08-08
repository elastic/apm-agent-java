package co.elastic.apm.agent.spring.webmvc.testapp.exception_resolver;

public class ExceptionResolverRuntimeException extends RuntimeException {

    public ExceptionResolverRuntimeException(String msg) {
        super(msg);
    }
}
