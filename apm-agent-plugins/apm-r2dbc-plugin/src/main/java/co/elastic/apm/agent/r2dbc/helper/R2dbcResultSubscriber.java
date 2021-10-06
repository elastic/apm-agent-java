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
package co.elastic.apm.agent.r2dbc.helper;

import co.elastic.apm.agent.impl.transaction.Span;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;

public class R2dbcResultSubscriber<T> implements CoreSubscriber<T>, Subscription {
    private static final Logger log = LoggerFactory.getLogger(R2dbcResultSubscriber.class);

    private final CoreSubscriber<? super T> subscriber;
    private final Span span;
    private Subscription subscription;

    public R2dbcResultSubscriber(CoreSubscriber<? super T> subscriber, Span span) {
        this.subscriber = subscriber;
        this.span = span;
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = subscription;
        subscriber.onSubscribe(s);
    }

    @Override
    public void onNext(T t) {
        if (t instanceof Integer) {
            Integer affectedRowCount = (Integer) t;
            if (span != null) {
                long current = span.getContext().getDb().getAffectedRowsCount();
                span.getContext().getDb().withAffectedRowsCount(current > 0 ? current + affectedRowCount : affectedRowCount);
            }
        }
        subscriber.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        subscriber.onError(t);
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
    }

    @Override
    public void request(long n) {
        subscription.request(n);
    }

    @Override
    public void cancel() {
        subscription.cancel();
    }
}
