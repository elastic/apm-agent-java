package co.elastic.apm.agent.spring.webmvc.template;


import co.elastic.apm.agent.spring.webmvc.template.freemarker.FreeMarkerViewController;
import co.elastic.apm.agent.spring.webmvc.template.freemarker.SpringWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = {
    FreeMarkerViewController.class, SpringWebConfig.class})
public class FreeMarkerViewTest extends AbstractViewRenderingInstrumentationTest {

    @Test
    public void testExceptionCapture() throws Exception {
        reporter.reset();
        ResultActions resultActions = mockMvc.perform(get("/free-marker"));
        MvcResult mvcResult = resultActions.andReturn();
        String responseString = mvcResult.getResponse().getContentAsString();
        assertEquals("FreeMarker Template example: Message 123", responseString.trim());
        assertEquals(1, 1);
        assertEquals(1, reporter.getSpans().size());
    }
}
