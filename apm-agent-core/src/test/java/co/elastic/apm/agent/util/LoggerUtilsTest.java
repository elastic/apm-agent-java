package co.elastic.apm.agent.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class LoggerUtilsTest {

    @Test
    void testLogOnce() {
        Logger mock = mock(Logger.class);
        Logger logger = LoggerUtils.logOnce(mock);

        logger.info("once");
        logger.warn("twice");

        verify(mock).info("once");
        verifyNoMoreInteractions(mock);
    }
}
