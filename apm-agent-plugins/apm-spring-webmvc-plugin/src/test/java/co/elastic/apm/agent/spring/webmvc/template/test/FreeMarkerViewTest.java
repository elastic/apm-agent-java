package co.elastic.apm.agent.spring.webmvc.template.test;


import co.elastic.apm.agent.AbstractInstrumentationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;
import org.springframework.web.servlet.view.freemarker.FreeMarkerViewResolver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class FreeMarkerViewTest extends AbstractInstrumentationTest {

    private static MockMvc mockMvc;

    @BeforeAll
    public static void setUpAll() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new MessageController())
            .setViewResolvers(freemarkerViewResolver())
            .build();
    }

    private static FreeMarkerViewResolver freemarkerViewResolver() {
        FreeMarkerViewResolver resolver = new FreeMarkerViewResolver();
        resolver.setCache(true);
        resolver.setPrefix("/freemarker/");
        resolver.setSuffix(".ftl");
        return resolver;
    }

    @Controller
    public static class MessageController {
        @GetMapping("/free-marker")
        public String handleRequest(ModelMap model) {
            model.addAttribute("msg", "Message from FreeMarkerViewController");
            return "example";
        }

        @Bean
        public FreeMarkerConfigurer freemarkerConfig() {
            FreeMarkerConfigurer freeMarkerConfigurer = new FreeMarkerConfigurer();
//            freeMarkerConfigurer.setTemplateLoaderPath("");
            return freeMarkerConfigurer;
        }
    }

    @Test
    public void testExceptionCapture() throws Exception {
        reporter.reset();
        mockMvc.perform(get("/free-marker"));
    }
}
