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

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;

public class R2dbcConnectionSubscriber<T> implements CoreSubscriber<T>, Subscription {
    private static final Logger log = LoggerFactory.getLogger(R2dbcConnectionSubscriber.class);

    private final CoreSubscriber<? super T> subscriber;
    private final ConnectionFactoryOptions connectionFactoryOptions;

    private Subscription subscription;

    public R2dbcConnectionSubscriber(CoreSubscriber<? super T> subscriber,
                                     final ConnectionFactoryOptions connectionFactoryOptions) {
        this.subscriber = subscriber;
        this.connectionFactoryOptions = connectionFactoryOptions;
    }

    @Override
    public void request(long n) {
        log.debug("Request connection {}", n);
        subscription.request(n);
    }

    @Override
    public void cancel() {
        log.debug("Cancel connection");
        subscription.cancel();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        log.debug("onSubscribe connection");
        this.subscription = subscription;

        subscriber.onSubscribe(subscription);
    }

    @Override
    public void onNext(T next) {
        log.debug("onNext connection");
        try {
            if (next instanceof Connection) {
                Connection connection = (Connection) next;
                R2dbcHelper helper = R2dbcHelper.get();
                helper.mapConnectionOptionsData(connection, connectionFactoryOptions);
            }
            subscriber.onNext(next);
        } catch (Throwable e) {
            throw e;
        }
    }

    @Override
    public void onError(Throwable t) {
        log.debug("onError connection");
        subscriber.onError(t);
    }

    @Override
    public void onComplete() {
        log.debug("onComplete connection");
        subscriber.onComplete();
    }
}
