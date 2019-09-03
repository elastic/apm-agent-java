package co.elastic.apm.agent.spring.webmvc.template.freemarker;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/free-marker")
@Controller
public class FreeMarkerViewController {

    @GetMapping
    public String handleRequest(Model model) {
        model.addAttribute("message", "Message 123");
        return "example";
    }
}
