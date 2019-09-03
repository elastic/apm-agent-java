package co.elastic.apm.agent.spring.webmvc.template.jackson2json;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/jackson")
@Controller
public class Jackson2JsonViewController {

    @GetMapping
    public String handleRequest(Model model) {
        model.addAttribute("message", "Message 123");
        return "jsonTemplate";
    }
}
