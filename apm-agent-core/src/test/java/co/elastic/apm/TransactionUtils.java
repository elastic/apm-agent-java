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
package co.elastic.apm;

import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.sampling.ConstantSampler;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;

import java.util.Arrays;
import java.util.List;

public class TransactionUtils {

    private static final List<String> STRINGS = Arrays.asList("bar", "baz");

    public static void fillTransaction(Transaction t) {
        t.start(null, 0, ConstantSampler.of(true));
        t.setName("GET /api/types");
        t.setType("request");
        t.withResult("success");

        Context context = t.getContext();
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

        context.getTags().put("organization_uuid", "9f0e9d64-c185-4d21-a6f4-4673ed561ec8");
        context.getCustom().put("my_key", 1);
        context.getCustom().put("some_other_value", "foo bar");
        context.getCustom().put("and_objects", STRINGS);

        Span span = new Span()
            .start(null, t, null, 0, false)
            .withName("SELECT FROM product_types")
            .withType("db.postgresql.query");
        span.getContext().getDb()
            .withInstance("customers")
            .withStatement("SELECT * FROM product_types WHERE user_id=?")
            .withType("sql")
            .withUser("readonly_user");
        t.getSpans().add(span);
        t.getSpans().add(new Span()
            .start(null, t, null, 0, false)
            .withName("GET /api/types")
            .withType("request"));
        t.getSpans().add(new Span()
            .start(null, t, null, 0, false)
            .withName("GET /api/types")
            .withType("request"));
        t.getSpans().add(new Span()
            .start(null, t, null, 0, false)
            .withName("GET /api/types")
            .withType("request"));
    }

}
