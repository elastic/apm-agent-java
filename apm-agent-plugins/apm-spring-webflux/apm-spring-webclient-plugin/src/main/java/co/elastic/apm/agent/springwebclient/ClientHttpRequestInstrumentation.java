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
package co.elastic.apm.agent.springwebclient;

import co.elastic.apm.agent.httpclient.RequestBodyRecordingHelper;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.client.reactive.ClientHttpRequest;
import reactor.core.publisher.Flux;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments both {@link ClientHttpRequest#writeWith(Publisher)} and {@link ClientHttpRequest#writeAndFlushWith(Publisher)}
 * to capture the request body.
 */
public class ClientHttpRequestInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("org.springframework.http.client.reactive")
            .and(nameContains("HttpRequest"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.springframework.http.client.reactive.ClientHttpRequest"))
            .and(not(isInterface()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return takesArgument(0, named("org.reactivestreams.Publisher")).and(
            named("writeWith").or(named("writeAndFlushWith")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "spring-webclient");
    }

    public static class AdviceClass {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(0))
        @SuppressWarnings("unchecked")
        public static Publisher<?> onBefore(
            @Advice.Origin("#m") String methodName,
            @Advice.This ClientHttpRequest clientRequest,
            @Advice.Argument(0) Publisher<?> bodyPublisher
        ) {
            RequestBodyRecordingHelper activeRecording = BodyCaptureRegistry.activateRecording(clientRequest);
            // Note that activateRecording would return null on subsequent calls for the same span
            // This is important because writeAndFlushWith might be built on top of writeWith (or the other way round)
            // The removal helps to not double capture the body in this case.
            if (activeRecording == null) {
                return bodyPublisher;
            }
            RecordingConsumer recordingConsumer = new RecordingConsumer(activeRecording);
            if (methodName.equals("writeWith")) {
                Publisher<? extends DataBuffer> actualPublisher = (Publisher<? extends DataBuffer>) bodyPublisher;
                return Flux.from(actualPublisher)
                    .doOnNext(recordingConsumer);
            } else if (methodName.equals("writeAndFlushWith")) {
                Publisher<? extends Publisher<? extends DataBuffer>> actualPublisher
                    = (Publisher<? extends Publisher<? extends DataBuffer>>) bodyPublisher;
                return Flux.from(actualPublisher)
                    .map(new Function<Publisher<? extends DataBuffer>, Publisher<? extends DataBuffer>>() {
                        @Override
                        public Publisher<? extends DataBuffer> apply(Publisher<? extends DataBuffer> publisher) {
                            return Flux.from(publisher)
                                .doOnNext(recordingConsumer);
                        }
                    });
            } else {
                throw new IllegalStateException("This case should never happen");
            }
        }
    }

    private static class RecordingConsumer implements Consumer<DataBuffer> {

        private final RequestBodyRecordingHelper recordTo;

        private RecordingConsumer(RequestBodyRecordingHelper recordTo) {
            this.recordTo = recordTo;
        }

        @Override
        public void accept(DataBuffer dataBuffer) {
            int positionBackUp = dataBuffer.readPosition();
            while (dataBuffer.readableByteCount() > 0) {
                if (!recordTo.appendToBody(dataBuffer.read())) {
                    break;
                }
            }
            dataBuffer.readPosition(positionBackUp);
        }
    }

}
