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
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.servlet.adapter.JavaxServletApiAdapter;
import org.junit.jupiter.api.Test;

import javax.servlet.ServletContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServletApiAdapterTest {

    public JavaxServletApiAdapter adapter = JavaxServletApiAdapter.get();

    @Test
    void safeGetClassLoader() {
        assertThat(adapter.getClassLoader(null)).isNull();

        ServletContext servletContext = mock(ServletContext.class);
        doThrow(UnsupportedOperationException.class).when(servletContext).getClassLoader();
        assertThat(adapter.getClassLoader(servletContext)).isNull();

        servletContext = mock(ServletContext.class);
        ClassLoader cl = mock(ClassLoader.class);
        when(servletContext.getClassLoader()).thenReturn(cl);
        assertThat(adapter.getClassLoader(servletContext)).isSameAs(cl);
    }
}
