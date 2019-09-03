package co.elastic.apm.agent.spring.webmvc.template;


import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.spring.webmvc.template.freemarker.FreeMarkerViewConfiguration;
import co.elastic.apm.agent.spring.webmvc.template.freemarker.FreeMarkerViewController;
import co.elastic.apm.agent.spring.webmvc.template.jackson2json.Jackson2JsonViewConfiguration;
import co.elastic.apm.agent.spring.webmvc.template.jackson2json.Jackson2JsonViewController;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = { Jackson2JsonViewController.class, Jackson2JsonViewConfiguration.class })
class Jackson2JsonViewTest extends AbstractViewRenderingInstrumentationTest {

    @Test
    void testExceptionCapture() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/jackson"));
        MvcResult mvcResult = resultActions.andReturn();
        String responseString = mvcResult.getResponse().getContentAsString();
        assertEquals("{\n  \"message\" : \"Message 123\"\n}", responseString.trim());
        assertEquals(1, reporter.getSpans().size());
        Span firstSpan = reporter.getSpans().get(0);
        assertEquals("template", firstSpan.getType());
        assertEquals("MappingJackson2Json", firstSpan.getSubtype());
        assertEquals("render", firstSpan.getAction());
        assertEquals("DispatcherServlet#render jsonTemplate", firstSpan.getNameAsString());
    }
}
