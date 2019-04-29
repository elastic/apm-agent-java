/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OsgiBootDelegationEnablerTest {

    private final OsgiBootDelegationEnabler osgiBootDelegationEnabler = new OsgiBootDelegationEnabler();

    @BeforeEach
    @AfterEach
    void clearState() {
        System.clearProperty("org.osgi.framework.bootdelegation");
        System.clearProperty("atlassian.org.osgi.framework.bootdelegation");
    }

    @Test
    void testBootdelegation() {
        osgiBootDelegationEnabler.start(mock(ElasticApmTracer.class));
        assertThat(System.getProperties())
            .containsEntry("org.osgi.framework.bootdelegation", "co.elastic.apm.agent.*")
            .containsKey("atlassian.org.osgi.framework.bootdelegation");
        assertThat(System.getProperty("atlassian.org.osgi.framework.bootdelegation")).matches(".+,co.elastic.apm.agent.*");
    }

    @Test
    void testBootdelegationWithExistingProperty() {
        System.setProperty("org.osgi.framework.bootdelegation", "foo.bar");
        osgiBootDelegationEnabler.start(mock(ElasticApmTracer.class));
        assertThat(System.getProperties())
            .containsEntry("org.osgi.framework.bootdelegation", "foo.bar,co.elastic.apm.agent.*")
            .containsKey("atlassian.org.osgi.framework.bootdelegation");
    }

    @Test
    void testAtlassianBootdelegationWithExistingProperty() {
        System.setProperty("atlassian.org.osgi.framework.bootdelegation", "foo.bar");
        osgiBootDelegationEnabler.start(mock(ElasticApmTracer.class));
        assertThat(System.getProperties())
            .containsEntry("atlassian.org.osgi.framework.bootdelegation", "foo.bar,co.elastic.apm.agent.*");
    }
}
