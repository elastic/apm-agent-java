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

import io.grpc.ServerCall;

import java.util.concurrent.atomic.AtomicReference;

// we have to use a test package that makes this listener to be instrumented by agent
public class TestServerListener<ReqT> extends ServerCall.Listener<ReqT> {

    private final ServerCall.Listener<ReqT> listener;
    private final AtomicReference<String> listenerExceptionMethod;

    public TestServerListener(ServerCall.Listener<ReqT> listener, AtomicReference<String> listenerExceptionMethod) {
        this.listener = listener;
        this.listenerExceptionMethod = listenerExceptionMethod;
    }

    @Override
    public void onMessage(ReqT message) {
        throwExceptionIfRequired("onMessage");
        listener.onMessage(message);
    }

    @Override
    public void onHalfClose() {
        throwExceptionIfRequired("onHalfClose");
        listener.onHalfClose();
    }

    @Override
    public void onCancel() {
        throwExceptionIfRequired("onCancel");
        listener.onCancel();
    }

    @Override
    public void onComplete() {
        throwExceptionIfRequired("onComplete");
        listener.onComplete();
    }

    @Override
    public void onReady() {
        throwExceptionIfRequired("onReady");
        listener.onReady();
    }

    private void throwExceptionIfRequired(String methodName) {
        if (methodName.equals(listenerExceptionMethod.get())) {
            throw new RuntimeException("intentional listener exception in method: " + methodName);
        }
    }
}
