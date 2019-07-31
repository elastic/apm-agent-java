package co.elastic.apm.agent.spring.webmvc.testapp.exception_resolver;

import co.elastic.apm.agent.spring.webmvc.testapp.common.ExceptionServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/exception-resolver")
public class ExceptionResolverController {

    @Autowired
    private ExceptionServiceImpl testAppExceptionService;

    @GetMapping
    public void handleRequest () {
        //just for testing
        testAppExceptionService.throwException();
    }
}
