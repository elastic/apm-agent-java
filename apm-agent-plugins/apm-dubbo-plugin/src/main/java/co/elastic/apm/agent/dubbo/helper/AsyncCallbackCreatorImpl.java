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
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.dubbo.rpc.AppResponse;

import java.util.function.BiConsumer;

public class AsyncCallbackCreatorImpl implements AsyncCallbackCreator {

    @Override
    public BiConsumer<AppResponse, Throwable> create(AbstractSpan<?> span, Object[] args) {
        return new AsyncCallback(span, args);
    }

    public static class AsyncCallback implements BiConsumer<AppResponse, Throwable> {

        private AbstractSpan<?> span;

        private Object[] args;

        public AsyncCallback(AbstractSpan<?> span, Object[] args) {
            this.span = span;
            this.args = args;
        }

        @Override
        public void accept(AppResponse appResponse, Throwable t) {
            try {
                Throwable actualExp = t != null ? t : appResponse.getException();
                if (actualExp != null) {
                    span.captureException(actualExp);
                }

                //only provider transaction capture args and return value or exception
                if (span instanceof Transaction) {
                    DubboTraceHelper.doCapture(
                        (Transaction) span, args, actualExp,
                        appResponse != null ? appResponse.getValue() : null);
                }
            } finally {
                span.end();
            }
        }
    }
}
