package co.elastic.apm.agent.spring.webmvc.testapp.exception_handler;

import co.elastic.apm.agent.spring.webmvc.testapp.common.ExceptionServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/exception-handler")
public class ExceptionHandlerController {

    @Autowired
    private ExceptionServiceImpl exceptionService;

    @GetMapping("/throw-exception")
    public ResponseEntity throwException() {
        exceptionService.throwException();
        return new ResponseEntity("OK", HttpStatus.OK);
    }

    @ExceptionHandler({ Exception.class })
    public ResponseEntity handleException(Exception e) {
        // handle exception
        return new ResponseEntity("exception-handler " + e.getMessage(), HttpStatus.CONFLICT);
    }
}
