/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.logging;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.sdk.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ResourceBundle;
import java.util.logging.LogRecord;

import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class JulBridgeLoggerTest extends AbstractInstrumentationTest {

    private JulBridgeLogger julLogger;
    private Exception e;
    private Logger agentFacadeLogger;

    @BeforeEach
    void setUp() {
        agentFacadeLogger = mock(Logger.class);
        julLogger = new JulBridgeLogger(agentFacadeLogger);
        e = new Exception("This exception is used to test exception logging");
    }

    @Test
    void testLogException() {
        julLogger.log(SEVERE, "test", e);
        verify(agentFacadeLogger).error("test", e);

        julLogger.log(WARNING, "test", e);
        verify(agentFacadeLogger).warn("test", e);

        julLogger.log(INFO, "test", e);
        julLogger.log(CONFIG, "test", e);
        verify(agentFacadeLogger, times(2)).info("test", e);

        julLogger.log(FINE, "test", e);
        julLogger.log(FINER, "test", e);
        verify(agentFacadeLogger, times(2)).debug("test", e);

        julLogger.log(FINEST, "test", e);
        verify(agentFacadeLogger).trace("test", e);
    }

    @Test
    void testLog() {
        julLogger.log(SEVERE, "test");
        verify(agentFacadeLogger).error("test");

        julLogger.log(WARNING, "test");
        verify(agentFacadeLogger).warn("test");

        julLogger.log(INFO, "test");
        julLogger.log(CONFIG, "test");
        verify(agentFacadeLogger, times(2)).info("test");

        julLogger.log(FINE, "test");
        julLogger.log(FINER, "test");
        verify(agentFacadeLogger, times(2)).debug("test");

        julLogger.log(FINEST, "test");
        verify(agentFacadeLogger).trace("test");
    }

    @Test
    void testLog2() {
        julLogger.severe("test");
        verify(agentFacadeLogger).error("test");

        julLogger.warning("test");
        verify(agentFacadeLogger).warn("test");

        julLogger.info("test");
        julLogger.config("test");
        verify(agentFacadeLogger, times(2)).info("test");

        julLogger.fine("test");
        julLogger.finer("test");
        verify(agentFacadeLogger, times(2)).debug("test");

        julLogger.finest("test");
        verify(agentFacadeLogger).trace("test");
    }

    // The bridge does not support parameter placeholders,
    // but who is perfect?
    @Test
    void testLogWithParameter() {
        julLogger.log(SEVERE, "test {0}", new Object());
        verify(agentFacadeLogger).error("test {0}");

        julLogger.log(WARNING, "test {0}", new Object());
        verify(agentFacadeLogger).warn("test {0}");

        julLogger.log(INFO, "test {0}", new Object());
        julLogger.log(CONFIG, "test {0}", new Object());
        verify(agentFacadeLogger, times(2)).info("test {0}");

        julLogger.log(FINE, "test {0}", new Object());
        julLogger.log(FINER, "test {0}", new Object());
        verify(agentFacadeLogger, times(2)).debug("test {0}");

        julLogger.log(FINEST, "test {0}", new Object());
        verify(agentFacadeLogger).trace("test {0}");
    }

    @Test
    void testLogWithParameters() {
        julLogger.log(SEVERE, "test {0}", new Object[]{new Object()});
        verify(agentFacadeLogger).error("test {0}");

        julLogger.log(WARNING, "test {0}", new Object[]{new Object()});
        verify(agentFacadeLogger).warn("test {0}");

        julLogger.log(INFO, "test {0}", new Object[]{new Object()});
        julLogger.log(CONFIG, "test {0}", new Object[]{new Object()});
        verify(agentFacadeLogger, times(2)).info("test {0}");

        julLogger.log(FINE, "test {0}", new Object[]{new Object()});
        julLogger.log(FINER, "test {0}", new Object[]{new Object()});
        verify(agentFacadeLogger, times(2)).debug("test {0}");

        julLogger.log(FINEST, "test {0}", new Object[]{new Object()});
        verify(agentFacadeLogger).trace("test {0}");
    }

    @Test
    void testLogRecord() {
        julLogger.log(new LogRecord(INFO, "test"));
        verify(agentFacadeLogger).info("test");
    }

    @Test
    void testLogp() {
        julLogger.logp(INFO, null, null, "test");
        julLogger.logp(INFO, null, null, "test", new Object());
        julLogger.logp(INFO, null, null, "test", new Object[]{});
        julLogger.logp(INFO, null, null, "test", e);
        verify(agentFacadeLogger, times(3)).info("test");
        verify(agentFacadeLogger, times(1)).info("test", e);
    }

    @Test
    void testLogrb() {
        julLogger.logrb(INFO, "", "", "", "test");
        julLogger.logrb(INFO, "", "", "", "test", new Object());
        julLogger.logrb(INFO, "", "", "", "test", new Object[]{});
        julLogger.logrb(INFO, "", "", (ResourceBundle) null, "test");
        julLogger.logrb(INFO, null, "test");
        julLogger.logrb(INFO, "", "", "", "test", e);
        julLogger.logrb(INFO, "", "", (ResourceBundle) null, "test", e);
        julLogger.logrb(INFO, null, "test", e);
        verify(agentFacadeLogger, times(5)).info("test");
        verify(agentFacadeLogger, times(3)).info("test", e);
    }

    @Test
    void testIsLoggable() {
        final JulBridgeLogger logger = JulBridgeLogger.getLogger(getClass().getName());
        assertThat(logger.isLoggable(SEVERE)).isTrue();
        assertThat(logger.isLoggable(WARNING)).isTrue();
        assertThat(logger.isLoggable(INFO)).isTrue();
        assertThat(logger.isLoggable(CONFIG)).isTrue();
        // debug logging should be enabled for tests
        assertThat(logger.isLoggable(FINE)).isTrue();
        assertThat(logger.isLoggable(FINER)).isTrue();
        assertThat(logger.isLoggable(FINEST)).isFalse();
        assertThat(logger.getLevel()).isSameAs(FINE);
    }

    @Test
    void testNoops() {
        julLogger.setLevel(FINEST);
        julLogger.setLevel(null);
        julLogger.entering(null, null);
        julLogger.entering(null, null, new Object());
        julLogger.entering(null, null, new Object[]{});
        julLogger.exiting(null, null);
        julLogger.exiting(null, null, null);
        julLogger.throwing(null, null, e);
        julLogger.setResourceBundle(null);
        julLogger.setFilter(null);
        julLogger.addHandler(null);
        julLogger.removeHandler(null);
        julLogger.setUseParentHandlers(true);
        julLogger.setParent(null);
        verifyNoMoreInteractions(agentFacadeLogger);
    }
}
