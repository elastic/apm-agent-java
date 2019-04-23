/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.TransactionContext;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;

public class TransactionUtils {

    public static void fillTransaction(Transaction t) {
        t.start(TraceContext.asRoot(), null, (long) 0, ConstantSampler.of(true));
        t.setName("GET /api/types");
        t.withType("request");
        t.withResult("success");

        TransactionContext context = t.getContext();
        Request request = context.getRequest();
        request.withHttpVersion("1.1");
        request.withMethod("POST");
        request.withBodyBuffer().append("Hello World").flip();
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

        context.addLabel("organization_uuid", "9f0e9d64-c185-4d21-a6f4-4673ed561ec8");
        context.addCustom("my_key", 1);
        context.addCustom("some_other_value", "foo bar");
    }

    public static List<Span> getSpans(Transaction t) {
        List<Span> spans = new ArrayList<>();
        Span span = new Span(mock(ElasticApmTracer.class))
            .start(TraceContext.fromParent(), t, -1, false)
            .withName("SELECT FROM product_types")
            .withType("db")
            .withSubtype("postgresql")
            .withAction("query");
        span.getContext().getDb()
            .withInstance("customers")
            .withStatement("SELECT * FROM product_types WHERE user_id=?")
            .withType("sql")
            .withUser("readonly_user");
        span.addLabel("monitored_by", "ACME");
        span.addLabel("framework", "some-framework");
        spans.add(span);

        spans.add(new Span(mock(ElasticApmTracer.class))
            .start(TraceContext.fromParent(), t, -1, false)
            .withName("GET /api/types")
            .withType("request"));
        spans.add(new Span(mock(ElasticApmTracer.class))
            .start(TraceContext.fromParent(), t, -1, false)
            .withName("GET /api/types")
            .withType("request"));
        spans.add(new Span(mock(ElasticApmTracer.class))
            .start(TraceContext.fromParent(), t, -1, false)
            .withName("GET /api/types")
            .withType("request"));

        span = new Span(mock(ElasticApmTracer.class))
            .start(TraceContext.fromParent(), t, -1, false)
            .appendToName("GET ")
            .appendToName("test.elastic.co")
            .withType("ext")
            .withSubtype("http")
            .withAction("apache-httpclient");
        span.getContext().getHttp()
            .withUrl("http://test.elastic.co/test-service")
            .withMethod("POST")
            .withStatusCode(201);
        spans.add(span);
        return spans;
    }

}
