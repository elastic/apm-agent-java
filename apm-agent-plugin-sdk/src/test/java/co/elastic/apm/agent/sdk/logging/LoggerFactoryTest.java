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
package co.elastic.apm.agent.sdk.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class LoggerFactoryTest {

    @AfterEach
    void after() {
        LoggerFactory.initialize(null);
    }

    @Test
    void returnsLazyWhenFactoryNotSet() {
        LoggerFactory.initialize(null);

        Logger logger = LoggerFactory.getLogger("logger");

        checkLazy(logger, "logger");
    }

    @Test
    void returnsNoOpWhenFactoryFails() {

        String name = "failure";

        ILoggerFactory factory = mock(ILoggerFactory.class);

        doReturn(null).when(factory).getLogger(name);

        LoggerFactory.initialize(factory);

        Logger logger = LoggerFactory.getLogger(name);
        checkNoOp(logger);
    }

    @Test
    void lazyLifecycle() {

        LoggerFactory.initialize(null);

        String name = "lazy";

        Logger logger = LoggerFactory.getLogger(name);
        LoggerFactory.LazyInitLogger lazyLogger = checkLazy(logger, name);

        // should delegate to no-op until the factory is set
        checkNoOp(lazyLogger.getDelegate());

        ILoggerFactory factory = mock(ILoggerFactory.class);
        Logger factoryLogger = mock(Logger.class);
        doReturn(factoryLogger).when(factory).getLogger(name);

        LoggerFactory.initialize(factory);

        assertThat(lazyLogger.getDelegate())
            .describedAs("after factory is set logger delegate should come from the factory")
            .isSameAs(factoryLogger);

        // intentionally reset the factory to ensure it's not used anymore
        LoggerFactory.initialize(null);

        assertThat(lazyLogger.getDelegate())
            .describedAs("factory returned logger is cached")
            .isSameAs(factoryLogger);
    }

    @Test
    void lazyLifecycleFactoryFailure() {
        String name = "lazyFailure";

        ILoggerFactory factory = mock(ILoggerFactory.class);
        doReturn(null).when(factory).getLogger(name);

        Logger logger = LoggerFactory.getLogger(name);
        LoggerFactory.LazyInitLogger lazyLogger = checkLazy(logger, name);

        LoggerFactory.initialize(factory);

        checkNoOp(lazyLogger.getDelegate());

        LoggerFactory.initialize(null);

        checkNoOp(lazyLogger.getDelegate());
    }

    private LoggerFactory.LazyInitLogger checkLazy(Logger logger, String name) {
        assertThat(logger).isInstanceOf(LoggerFactory.LazyInitLogger.class);
        assertThat(logger.getName()).isEqualTo(name);
        return (LoggerFactory.LazyInitLogger) logger;
    }

    private void checkNoOp(Logger logger) {
        assertThat(logger).isInstanceOf(LoggerFactory.NoopLogger.class);
    }

}
