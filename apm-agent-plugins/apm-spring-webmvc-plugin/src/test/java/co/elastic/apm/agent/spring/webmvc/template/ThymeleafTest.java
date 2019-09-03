package co.elastic.apm.agent.spring.webmvc.template;


import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.spring.webmvc.template.freemarker.FreeMarkerViewConfiguration;
import co.elastic.apm.agent.spring.webmvc.template.freemarker.FreeMarkerViewController;
import co.elastic.apm.agent.spring.webmvc.template.thymeleaf.ThymeleafConfiguration;
import co.elastic.apm.agent.spring.webmvc.template.thymeleaf.ThymeleafController;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = { ThymeleafConfiguration.class, ThymeleafController.class })
class ThymeleafTest extends AbstractViewRenderingInstrumentationTest {

    @Test
    void testExceptionCapture() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/thymeleaf"));
        MvcResult mvcResult = resultActions.andReturn();
        String responseString = mvcResult.getResponse().getContentAsString();
        assertEquals("<span>Message 123</span>", responseString.trim());
        assertEquals(1, reporter.getSpans().size());
        Span firstSpan = reporter.getSpans().get(0);
        assertEquals("template", firstSpan.getType());
        assertEquals("Thymeleaf", firstSpan.getSubtype());
        assertEquals("render", firstSpan.getAction());
        assertEquals("DispatcherServlet#render thymeleaf", firstSpan.getNameAsString());
    }
}
