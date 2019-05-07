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
package co.elastic.apm.agent.impl.context;

import org.junit.jupiter.api.Test;

import static co.elastic.apm.agent.JsonUtils.toJson;
import static org.assertj.core.api.Assertions.assertThat;

class UrlTest {

    @Test
    void testResetState() {
        final Url url = newUrl();
        url.resetState();
        assertThat(toJson(url)).isEqualTo(toJson(new Url()));
    }

    @Test
    void testCopyOf() {
        final Url url = newUrl();
        final Url copy = new Url();
        copy.copyFrom(url);
        assertThat(toJson(url)).isEqualTo(toJson(copy));
        assertThat(toJson(url)).isNotEqualTo(toJson(new Url()));
    }

    private Url newUrl() {
        return new Url()
            .withHostname("localhost")
            .withPathname("/foo")
            .withPort(8080)
            .withProtocol("http")
            .withSearch("foo=bar")
            .appendToFull("http://localhost:8080/foo?foo=bar");
    }
}
