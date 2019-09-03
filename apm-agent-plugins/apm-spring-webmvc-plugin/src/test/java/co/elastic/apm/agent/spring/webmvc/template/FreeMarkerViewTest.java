package co.elastic.apm.agent.spring.webmvc.template;


import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.spring.webmvc.template.freemarker.FreeMarkerViewController;
import co.elastic.apm.agent.spring.webmvc.template.freemarker.FreeMarkerViewConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = { FreeMarkerViewController.class, FreeMarkerViewConfiguration.class })
class FreeMarkerViewTest extends AbstractViewRenderingInstrumentationTest {

    @Test
    void testExceptionCapture() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/free-marker"));
        MvcResult mvcResult = resultActions.andReturn();
        String responseString = mvcResult.getResponse().getContentAsString();
        assertEquals("FreeMarker Template example: Message 123", responseString.trim());
        assertEquals(1, reporter.getSpans().size());
        Span firstSpan = reporter.getSpans().get(0);
        assertEquals("template", firstSpan.getType());
        assertEquals("FreeMarker", firstSpan.getSubtype());
        assertEquals("render", firstSpan.getAction());
        assertEquals("DispatcherServlet#render example", firstSpan.getNameAsString());
    }
}
