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
package co.elastic.apm.agent.asynchttpclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import io.netty.handler.codec.http.HttpHeaders;
import org.asynchttpclient.*;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class AsyncHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private AsyncHttpClient client;

    @BeforeEach
    void setUp() {
        AsyncHttpClientConfig config = Dsl.config()
            .setFollowRedirect(true)
            .build();
        client = asyncHttpClient(config);
    }

    @AfterEach
    void tearDown() {
        try {
            if(client != null) {
                client.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void performGet(String path) throws ExecutionException, InterruptedException, TimeoutException { ;
        Request request = new RequestBuilder()
            .setUrl(path)
            .build();

        AsyncHandler asyncHandler = new AsyncHandler<Object>() {
            private final Logger logger = LoggerFactory.getLogger(AsyncHandler.class);

            @Override
            public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                logger.info("ON_BODY_PART_RECEIVED");
                return State.CONTINUE;
            }

            @Override
            public void onThrowable(Throwable t) { }

            @Override
            public Object onCompleted() throws Exception {
                logger.info("ON_COMPLETED");
                return null;
            }

            @Override
            public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                logger.info("ON_STATUS_RECEIVED: " + responseStatus.getStatusCode());
                return State.CONTINUE;
            }

            @Override
            public State onHeadersReceived(HttpHeaders headers) throws Exception {
                logger.info("ON_HEADERS_RECEIVED: " + headers.toString());
                return State.CONTINUE;
            }
        };

        asyncHttpClient()
            .executeRequest(request).get();
    }

    @Test
    void testGetAdviceClassReturnsAsyncInstrumentationClass() {
        AsyncHttpClientInstrumentation asyncHttpClientInstrumentation = new AsyncHttpClientInstrumentation();

        Assert.assertEquals(asyncHttpClientInstrumentation.getAdviceClass(), AsyncHttpClientInstrumentation.AsyncHttpClientInstrumentationAdvice.class);
    }
}
