package co.elastic.apm.agent.error.logging;

import co.elastic.apm.agent.error.logging.controller.JavaUtilLoggingController;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = {JavaUtilLoggingController.class})
public class JavaUtilLoggingInstrumentationTest extends AbstractLoggingInstrumentationTest {

    @Test
    public void test() throws Exception {

        ResultActions resultActions = mockMvc.perform(get("/java-util-logging/error"));
        MockHttpServletResponse response = resultActions.andReturn().getResponse();
        int status = response.getStatus();
        assertEquals(1, reporter.getTransactions().size());
        assertEquals(0, reporter.getSpans().size());
        assertEquals(1, reporter.getErrors().size());
    }
}
