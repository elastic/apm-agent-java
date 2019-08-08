package co.elastic.apm.agent.spring.webmvc.testapp.response_status_exception;

public class ResponseStatusRuntimeException extends RuntimeException {

    public ResponseStatusRuntimeException(String msg) {
        super(msg);
    }
}
