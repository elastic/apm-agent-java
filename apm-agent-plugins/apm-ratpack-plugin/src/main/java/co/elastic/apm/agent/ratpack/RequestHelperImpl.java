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
package co.elastic.apm.agent.ratpack;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.web.WebConfiguration;
import io.netty.buffer.ByteBuf;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import ratpack.exec.Promise;
import ratpack.exec.Result;
import ratpack.func.Action;
import ratpack.http.MediaType;
import ratpack.http.Request;
import ratpack.http.TypedData;
import ratpack.stream.StreamEvent;
import ratpack.stream.TransformablePublisher;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.util.function.BiFunction;

@SuppressWarnings("unused")
public class RequestHelperImpl implements RequestInstrumentation.RequestHelper<Request, Promise<TypedData>, TransformablePublisher<? extends ByteBuf>> {

    private static final String IS_BODY_WIRETAPPED = "IS_BODY_WIRETAPPED";

    @Override
    public Promise<TypedData> addBodyWiretapIfCapturing(final Request request, final Promise<TypedData> promise) {
        return new BodyWiretapper().addWiretapIfCapturing(request, promise);
    }

    @Override
    public TransformablePublisher<? extends ByteBuf> addStreamWiretapIfCapturing(final Request request, final TransformablePublisher<? extends ByteBuf> publisher) {
        return new BodyWiretapper().addWiretapIfCapturing(request, publisher);
    }

    @SuppressWarnings("WeakerAccess")
    @IgnoreJRERequirement
    @VisibleForAdvice
    public static class BodyWiretapper {

        public boolean isCapturing(final Transaction transaction, final WebConfiguration webConfiguration, final MediaType contentType) {
            return transaction.isSampled()
                && !contentType.isEmpty()
                && !contentType.isForm()
                && webConfiguration.getCaptureBody() != WebConfiguration.EventType.OFF
                && WildcardMatcher.isAnyMatch(webConfiguration.getCaptureContentTypes(), contentType.getType());
        }

        @SuppressWarnings({"WeakerAccess"})
        @VisibleForAdvice
        public Promise<TypedData> addWiretapIfCapturing(final Request request, final Promise<TypedData> target) {
            return addWiretapIfCapturing(request, target, new CaptureBodyFunction());
        }

        public class CaptureBodyFunction implements BiFunction<Transaction, Promise<TypedData>, Promise<TypedData>> {

            @Override
            public Promise<TypedData> apply(final Transaction transaction, final Promise<TypedData> promise) {

                return promise.wiretap(new CaptureBodyAction(transaction));
            }
        }
        public class CaptureBodyAction implements Action<Result<TypedData>> {

            private final Transaction transaction;
            public CaptureBodyAction(final Transaction transaction) {
                this.transaction = transaction;
            }

            @Override
            public void execute(final Result<TypedData> result) throws Exception {

                final ByteBuf value = result.getValue().getBuffer();

                captureBody(transaction, value);
            }

        }
        @SuppressWarnings({"WeakerAccess"})
        @VisibleForAdvice
        public TransformablePublisher<? extends ByteBuf> addWiretapIfCapturing(final Request request, final TransformablePublisher<? extends ByteBuf> target) {
            return addWiretapIfCapturing(request, target, new CaptureStreamFunction());
        }

        public class CaptureStreamFunction implements BiFunction<Transaction, TransformablePublisher<? extends ByteBuf>, TransformablePublisher<? extends ByteBuf>> {

            @Override
            public TransformablePublisher<? extends ByteBuf> apply(final Transaction transaction, final TransformablePublisher<? extends ByteBuf> publisher) {
                return publisher.wiretap(new CaptureStreamAction(transaction));
            }
        }
        public class CaptureStreamAction implements Action<StreamEvent<? extends ByteBuf>> {

            private final Transaction transaction;

            public CaptureStreamAction(final Transaction transaction) {
                this.transaction = transaction;
            }

            @Override
            public void execute(final StreamEvent<? extends ByteBuf> streamEvent) throws Exception {

                final ByteBuf item = streamEvent.getItem();

                captureBody(transaction, item);
            }

        }
        <T> T addWiretapIfCapturing(final Request request, final T target, final BiFunction<Transaction, T, T> wiretap) {

            if (ElasticApmInstrumentation.tracer == null) {
                return target;
            }

            final Transaction transaction = ElasticApmInstrumentation.tracer.currentTransaction();
            final WebConfiguration webConfiguration = ElasticApmInstrumentation.tracer.getConfig(WebConfiguration.class);

            //noinspection ConstantConditions
            if (transaction == null || webConfiguration == null || isAlreadyWiretapped(transaction) || !hasBody(request)) {
                return target;
            }

            if (isCapturing(transaction, webConfiguration, request.getContentType())) {

                markWiretapped(transaction);
                return wiretap.apply(transaction, target);

            } else {
                redactBody(transaction);
                return target;
            }
        }

        boolean hasBody(final Request request) {
            return TransactionHolderImpl.METHODS_WITH_BODY.contains(request.getMethod()) && !request.getContentType().isEmpty();
        }

        boolean isAlreadyWiretapped(final Transaction transaction) {

            final Object wiretap = transaction.getContext().getCustom(IS_BODY_WIRETAPPED);

            return (wiretap instanceof Boolean) && (boolean) wiretap;
        }

        void markWiretapped(final Transaction transaction) {
            transaction.getContext().addCustom(IS_BODY_WIRETAPPED, true);
        }

        void redactBody(final Transaction transaction) {
            transaction.getContext().getRequest().redactBody();
        }

        void captureBody(final Transaction transaction, @Nullable final ByteBuf value) throws IOException {

            if (value == null) {
                return;
            }

            final ByteBuf buf = value.asReadOnly();
            final CharBuffer buffer = transaction.getContext().getRequest().withBodyBuffer();

            buf.resetReaderIndex();
            buf.readBytes(new AppendableOutputStream(buffer), buf.writerIndex());
        }

    }

    @SuppressWarnings("WeakerAccess")
    public static class AppendableOutputStream extends OutputStream {
        private final CharBuffer buffer;

        public AppendableOutputStream(final CharBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public void write(final int b) {
            buffer.append((char) b);
        }
    }

}


