package co.elastic.apm.agent.spring.webmvc.template.thymeleaf;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/thymeleaf")
@Controller
public class ThymeleafController {

    @GetMapping
    public String handleRequest(Model model) {
        model.addAttribute("message", "Message 123");
        return "thymeleaf";
    }
}
