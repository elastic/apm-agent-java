package co.elastic.apm.agent.spring.webmvc.testapp.response_status_exception;

import co.elastic.apm.agent.spring.webmvc.testapp.common.ExceptionServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/response-status-exception")
public class ResponseStatusExceptionController {

    @Autowired
    private ExceptionServiceImpl exceptionService;

    @GetMapping
    public ResponseEntity request() {
        try {
            exceptionService.throwException();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "responseStatusException", e);
        }
        return null;
    }
}
