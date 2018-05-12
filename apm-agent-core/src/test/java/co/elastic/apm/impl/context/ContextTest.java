/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl.context;

import org.junit.jupiter.api.Test;

import static co.elastic.apm.JsonUtils.toJson;
import static org.assertj.core.api.Assertions.assertThat;

class ContextTest {

    @Test
    void testCopyFrom() {
        Context context = createContext();
        Context copyOfContext = new Context();
        copyOfContext.copyFrom(context);
        assertThat(toJson(context)).isEqualTo(toJson(copyOfContext));
    }

    @Test
    void testCopyFromDoNotCopyTags() {
        Context context = new Context();
        context.getTags().put("foo", "bar");
        Context copyOfContext = new Context();
        copyOfContext.copyFrom(context);
        assertThat(copyOfContext.getTags()).isEmpty();
    }

    @Test
    void testCopyFromDoNotCopyCustom() {
        Context context = new Context();
        context.getCustom().put("foo", "bar");
        Context copyOfContext = new Context();
        copyOfContext.copyFrom(context);
        assertThat(copyOfContext.getCustom()).isEmpty();
    }

    private Context createContext() {
        Context context = new Context();
        Request request = context.getRequest();
        request.withHttpVersion("1.1");
        request.withMethod("POST");
        request.withRawBody("Hello World");
        request.getUrl()
            .withProtocol("https")
            .appendToFull("https://www.example.com/p/a/t/h?query=string#hash")
            .withHostname("www.example.com")
            .withPort(8080)
            .withPathname("/p/a/t/h")
            .withSearch("?query=string");
        request.getSocket()
            .withEncrypted(true)
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
