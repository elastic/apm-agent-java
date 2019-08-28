package co.elastic.apm.agent.error.logging;

import co.elastic.apm.agent.error.logging.controller.Slf4jController;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = {Slf4jController.class})
public class Slf4jLoggingInstrumentationTest extends AbstractLoggingInstrumentationTest {

    @Test
    public void test() throws Exception {

        ResultActions resultActions = mockMvc.perform(get("/slf4j/error"));
        MockHttpServletResponse response = resultActions.andReturn().getResponse();
        int status = response.getStatus();
        assertEquals(1, reporter.getTransactions().size());
        assertEquals(0, reporter.getSpans().size());
        assertEquals(1, reporter.getErrors().size());
        assertEquals(200, status);
        Throwable exception = reporter.getErrors().get(0).getException();
        assertEquals("some business exception", exception.getMessage());
        assertEquals(RuntimeException.class, exception.getClass());
    }

}
