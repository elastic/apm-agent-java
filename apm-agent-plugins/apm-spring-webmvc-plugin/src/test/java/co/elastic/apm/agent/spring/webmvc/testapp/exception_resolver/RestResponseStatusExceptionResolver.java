package co.elastic.apm.agent.spring.webmvc.testapp.exception_resolver;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class RestResponseStatusExceptionResolver extends AbstractHandlerExceptionResolver {

    @Override
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception e) {
        ModelAndView model = new ModelAndView("error-page");
        model.addObject("message", "AbstractHandlerExceptionResolver error handler");
        return model;
    }
}
