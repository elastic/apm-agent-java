package co.elastic.apm.agent.error.logging;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jLoggingInstrumentationTest extends AbstractErrorLoggingInstrumentationTest {

    private static final Logger logger = LoggerFactory.getLogger(Slf4jLoggingInstrumentationTest.class);

    @Test
    public void captureException() {
        try {
            throw new RuntimeException("some business exception");
        } catch (Exception e) {
            logger.error("exception captured", e);
        }
        verifyThatExceptionCaptured(1, "some business exception", RuntimeException.class);
    }

}
