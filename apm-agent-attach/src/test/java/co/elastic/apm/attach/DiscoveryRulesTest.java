/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.attach;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryRulesTest {

    private final DiscoveryRules discoveryRules = new DiscoveryRules();
    private final UserRegistry userRegistry = UserRegistry.empty();

    @Test
    void testNoRules() {
        assertThat(discoveryRules.isAnyMatch(mockVm("1"), userRegistry)).isFalse();
    }

    @Test
    void testIncludeAll() {
        discoveryRules.includeAll();
        assertThat(discoveryRules.isAnyMatch(mockVm("1"), userRegistry)).isTrue();
    }

    @Test
    void testExcludeNotMatching() {
        discoveryRules.excludePid("2");
        assertThat(discoveryRules.isAnyMatch(mockVm("1"), userRegistry)).isTrue();
    }

    @Test
    void testExcludeMatching() {
        discoveryRules.excludePid("1");
        assertThat(discoveryRules.isAnyMatch(mockVm("1"), userRegistry)).isFalse();
    }

    @Test
    void testIncludeNotMatching() {
        discoveryRules.includePid("1");
        assertThat(discoveryRules.isAnyMatch(mockVm("2"), userRegistry)).isFalse();
    }

    @Test
    void testIncludeMatching() {
        discoveryRules.includePid("1");
        assertThat(discoveryRules.isAnyMatch(mockVm("1"), userRegistry)).isTrue();
    }

    @Test
    void testIncludeMultiple() {
        discoveryRules.includePid("1");
        discoveryRules.includePid("2");
        assertThat(discoveryRules.isAnyMatch(mockVm("1"), userRegistry)).isTrue();
        assertThat(discoveryRules.isAnyMatch(mockVm("2"), userRegistry)).isTrue();
        assertThat(discoveryRules.isAnyMatch(mockVm("3"), userRegistry)).isFalse();
    }

    @Test
    void testExcludeNotIncluded() {
        discoveryRules.includePid("1");
        discoveryRules.excludePid("2");
        assertThat(discoveryRules.isAnyMatch(mockVm("1"), userRegistry)).isTrue();
        assertThat(discoveryRules.isAnyMatch(mockVm("2"), userRegistry)).isFalse();
        assertThat(discoveryRules.isAnyMatch(mockVm("3"), userRegistry)).isTrue();
    }

    private JvmInfo mockVm(String s) {
        return JvmInfo.withCurrentUser(s, new Properties());
    }
}
