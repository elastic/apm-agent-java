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
package co.elastic.apm.agent.dubbo.helper;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;

import java.util.function.BiConsumer;

public class AsyncCallbackCreatorImpl implements AsyncCallbackCreator {

    private final static BiConsumer<Result, Throwable> INSTANCE = new AsyncCallback();

    @Override
    public BiConsumer<Result, Throwable> create(AbstractSpan<?> span) {
        return INSTANCE;
    }

    public static class AsyncCallback implements BiConsumer<Result, Throwable> {

        @Override
        public void accept(Result result, Throwable t) {
            AbstractSpan<?> span = (AbstractSpan<?>) RpcContext.getContext().get(DubboTraceHelper.SPAN_KEY);
            if (span != null) {
                try {
                    RpcContext.getContext().remove(DubboTraceHelper.SPAN_KEY);
                    span.captureException(t).captureException(result.getException());
                } finally {
                    span.end();
                }
            }
        }
    }
}
