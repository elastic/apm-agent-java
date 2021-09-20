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
package co.elastic.apm.agent.impl.context;

import org.junit.jupiter.api.Test;

import static co.elastic.apm.agent.JsonUtils.toJson;
import static org.assertj.core.api.Assertions.assertThat;

class TransactionContextTest {

    @Test
    void testCopyFrom() {
        TransactionContext context = createContext();
        TransactionContext copyOfContext = new TransactionContext();
        copyOfContext.copyFrom(context);
        assertThat(toJson(context)).isEqualTo(toJson(copyOfContext));
    }

    @Test
    void testCopyFromCopiesTags() {
        TransactionContext context = new TransactionContext();
        context.addLabel("foo", "bar");
        TransactionContext copyOfContext = new TransactionContext();
        copyOfContext.copyFrom(context);
        assertThat(copyOfContext.getLabel("foo")).isEqualTo("bar");
    }

    @Test
    void testCopyFromDoNotCopyCustom() {
        TransactionContext context = new TransactionContext();
        context.addCustom("foo", "bar");
        TransactionContext copyOfContext = new TransactionContext();
        copyOfContext.copyFrom(context);
        assertThat(copyOfContext.hasCustom()).isFalse();
    }

    private TransactionContext createContext() {
        TransactionContext context = new TransactionContext();
        Request request = context.getRequest();
        request.withHttpVersion("1.1");
        request.withMethod("POST");
        request.withBodyBuffer().append("Hello World");
        request.endOfBufferInput();
        request.getUrl()
            .withProtocol("https")
            .withHostname("www.example.com")
            .withPort(8080)
            .withPathname("/p/a/t/h")
            .withSearch("?query=string");
        request.getSocket()
            .withRemoteAddress("12.53.12.1");
        request.addHeader("user-agent", "Mozilla Chrome Edge");
        request.addHeader("content-type", "text/html");
        request.addHeader("cookie", "c1=v1; c2=v2");
        request.addHeader("some-other-header", "foo");
        request.addHeader("array", "foo, bar, baz");
        request.getCookies().add("c1", "v1");
        request.getCookies().add("c2", "v2");

        context.getResponse()
            .withStatusCode(200)
            .withFinished(true)
            .withHeadersSent(true)
            .addHeader("content-type", "application/json");

        context.getUser()
            .withId("99")
            .withUsername("foo")
            .withEmail("foo@example.com");
        return context;
    }
}
