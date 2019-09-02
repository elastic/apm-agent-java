package co.elastic.apm.agent.spring.webmvc.template.freemarker;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/free-marker")
@RestController
public class FreeMarkerViewController {

    @GetMapping
    public String handleRequest (Model model) {
        model.addAttribute("msg", "Message from FreeMarkerViewController");
        return "example";
    }
}
