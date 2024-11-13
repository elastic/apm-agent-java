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
package co.elastic.apm.agent.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

class UrlConnectionUtilsTest {

    @BeforeEach
    void beforeEach() {
        Thread.currentThread().setContextClassLoader(null);
    }

    @AfterEach
    void afterEach() {
        Thread.currentThread().setContextClassLoader(null);
    }

    @Test
    void threadContextClassLoader_noop() {
        ClassLoader cl = UrlConnectionUtils.class.getClassLoader();
        testContextClassLoader(null, null, false);
        testContextClassLoader(cl, cl, false);
    }

    @Test
    void threadContextClassLoader_override() {
        ClassLoader cl = UrlConnectionUtils.class.getClassLoader();
        testContextClassLoader(null, cl, true);
        testContextClassLoader(cl, cl, true);

        DummyClassLoader overrideCl = new DummyClassLoader();
        testContextClassLoader(null, overrideCl, true);
        testContextClassLoader(cl, overrideCl, true);
    }

    private void testContextClassLoader(@Nullable ClassLoader initialClassLoader, @Nullable ClassLoader scopeClassLoader, boolean override) {
        Thread.currentThread().setContextClassLoader(initialClassLoader);
        checkContextClassLoader(initialClassLoader);
        try (UrlConnectionUtils.ContextClassloaderScope scope = UrlConnectionUtils.withContextClassloader(scopeClassLoader, override)) {
            checkContextClassLoader(scopeClassLoader);
        }
        checkContextClassLoader(initialClassLoader);
    }

    private void checkContextClassLoader(@Nullable ClassLoader expected) {
        ClassLoader threadContextCl = Thread.currentThread().getContextClassLoader();
        if (expected == null) {
            assertThat(threadContextCl).isNull();
        } else {
            assertThat(threadContextCl).isSameAs(expected);
        }
    }

    private static class DummyClassLoader extends ClassLoader {

    }
}
