package co.elastic.apm.agent.spring.webmvc.testapp.controller_advice;

public class ControllerAdviceRuntimeException extends RuntimeException {

    public ControllerAdviceRuntimeException(String msg) {
        super(msg);
    }
}
