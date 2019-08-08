package co.elastic.apm.agent.spring.webmvc.testapp.exception_handler;

public class ExceptionHandlerRuntimeException extends RuntimeException {

    public ExceptionHandlerRuntimeException(String msg) {
        super(msg);
    }
}
