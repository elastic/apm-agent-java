/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package io.grpc.elastic.test;

import io.grpc.Attributes;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

// class name and package make it instrumented by ClientCallImplInstrumentation
public class TestClientCallImpl<ReqT, RespT> extends ClientCall<ReqT, RespT> {

    private final ClientCall<ReqT, RespT> clientCall;
    private final AtomicReference<String> exceptionMethod;

    public TestClientCallImpl(ClientCall<ReqT, RespT> clientCall, AtomicReference<String> exceptionMethod) {
        this.clientCall = clientCall;
        this.exceptionMethod = exceptionMethod;
    }

    @Override
    public void start(Listener<RespT> listener, Metadata headers) {
        throwExceptionIfRequired("start");
        clientCall.start(new TestListener(listener), headers);
    }

    @Override
    public void request(int numMessages) {
        clientCall.request(numMessages);
    }

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
        clientCall.cancel(message, cause);
    }

    @Override
    public void halfClose() {
        clientCall.halfClose();
    }

    @Override
    public void sendMessage(ReqT message) {
        clientCall.sendMessage(message);
    }

    @Override
    public boolean isReady() {
        return clientCall.isReady();
    }

    @Override
    public void setMessageCompression(boolean enabled) {
        clientCall.setMessageCompression(enabled);
    }

    @Override
    public Attributes getAttributes() {
        return clientCall.getAttributes();
    }

    private class TestListener extends Listener<RespT> {

        private final Listener<RespT> listener;

        private TestListener(Listener<RespT> listener) {
            this.listener = listener;
        }

        @Override
        public void onHeaders(Metadata headers) {
            throwExceptionIfRequired("onHeaders");
            listener.onHeaders(headers);
        }

        @Override
        public void onMessage(RespT message) {
            throwExceptionIfRequired("onMessage");
            listener.onMessage(message);
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            throwExceptionIfRequired("onClose");
            listener.onClose(status, trailers);
        }

        @Override
        public void onReady() {
            throwExceptionIfRequired("onReady");
            listener.onReady();
        }
    }

    private void throwExceptionIfRequired(String methodName) {
        if (methodName.equals(exceptionMethod.get())) {
            throw new RuntimeException("intentional listener exception in method: " + methodName);
        }
    }

}
