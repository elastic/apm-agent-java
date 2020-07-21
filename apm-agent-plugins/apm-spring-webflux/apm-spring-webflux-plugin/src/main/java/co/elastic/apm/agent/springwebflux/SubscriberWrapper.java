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
package co.elastic.apm.agent.springwebflux;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;

public class SubscriberWrapper<T> implements CoreSubscriber<T> {

    // TODO : remove SubscriberWrapper class as it's not needed beyond debugging
    private final CoreSubscriber<? super T> subscriber;
    protected String name;

    public SubscriberWrapper(CoreSubscriber<? super T> subscriber, String name) {
        this.subscriber = subscriber;
        this.name = name;
    }

    @Override
    public void onSubscribe(Subscription s) {
        System.out.println(String.format("%s [enter] %s()", name, "onSubscribe"));
        try {
            subscriber.onSubscribe(s);
        } finally {
            System.out.println(String.format("%s [exit]  %s()", name, "onSubscribe"));
        }

    }

    @Override
    public void onNext(T t) {
        System.out.println(String.format("%s [enter] %s()", name, "onNext"));
        try {
            subscriber.onNext(t);
        } finally {
            System.out.println(String.format("%s [exit]  %s()", name, "onNext"));
        }
    }

    @Override
    public void onError(Throwable t) {
        System.out.println(String.format("%s [enter] %s()", name, "onError"));
        try {
            subscriber.onError(t);
        } finally {
            System.out.println(String.format("%s [exit]  %s()", name, "onError"));
        }
    }

    @Override
    public void onComplete() {
        System.out.println(String.format("%s [enter] %s()", name, "onComplete"));
        try {
            subscriber.onComplete();
        } finally {
            System.out.println(String.format("%s [exit]  %s()", name, "onComplete"));
        }
    }
}
