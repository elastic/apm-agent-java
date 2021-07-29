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
package co.elastic.apm.agent.webflux.client;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

public class WebfluxClientSubscriber<T> implements CoreSubscriber<T> {
    public static final Logger logger = LoggerFactory.getLogger(WebfluxClientSubscriber.class);

    private static final ConcurrentHashMap<String, AbstractSpan> webClientTransactionMap = new ConcurrentHashMap();
    private static final ConcurrentHashMap<String, AbstractSpan> logPrefixTransactionMap = new ConcurrentHashMap();
    private static final ConcurrentHashMap<String, Object> logPrefixSubscriberMap = new ConcurrentHashMap();

    private String debugPrefix;
    private final Tracer tracer;
    private CoreSubscriber<? super T> subscriber;
    private String logPrefix;

    public WebfluxClientSubscriber(CoreSubscriber<? super T> subscriber, String logPrefix, Tracer tracer, String debugId) {
        this.subscriber = subscriber;
        this.logPrefix = logPrefix;
        this.tracer = tracer;
        this.debugPrefix = debugId;
    }


    /**
     * @return context associated with {@literal this}.
     */
    @Nullable
    private AbstractSpan<?> getContext() {
        return logPrefixTransactionMap.get(this.logPrefix);
    }

    /**
     * Wrapped method entry
     *
     * @param method  method name (only for debugging)
     * @param context context
     * @return {@literal true} if context has been activated
     */
    private boolean doEnter(String method, @Nullable AbstractSpan<?> context) {
//        debugTrace(true, method, context);

        if (context == null || tracer.getActive() == context) {
            // already activated or discarded
            return false;
        }

        context.activate();
        return true;
    }

    private void doExit(boolean deactivate, String method, @Nullable AbstractSpan<?> context) {
        doExit(deactivate, method, context, false);
    }

    /**
     * Wrapped method exit
     *
     * @param deactivate {@literal true} to de-activate due to a previous activation, no-op otherwise
     * @param method     method name (only for debugging)
     * @param context    context
     */
    private void doExit(boolean deactivate, String method, @Nullable AbstractSpan<?> context, boolean end) {
//        debugTrace(false, method, context);

        if (context == null || !deactivate) {
            return;
        }

        if (context != tracer.getActive()) {
            // don't attempt to deactivate if not the active one
            return;
        }

        // the current context has been activated on enter thus must be the active one
        context.deactivate();
        if (end) {

            System.out.println("AdHocSubscriber " + this.hashCode() + " " + debugPrefix + " ending ctx=" + context + " on "
                + Thread.currentThread().getName() + " active=" + tracer.getActive() + " ctx outcome=" + context.getOutcome());
            //FIXME:
            System.out.println("exit=" + context.isExit() + " finished=" + context.isFinished() + " discarded=" + context.isDiscarded());
            if(!context.isFinished()){
                context.end();
            }
//            if (transaction != null) {
//                System.out.println("AdHocSubscriber " + this.hashCode() + " "+ debugId + " ending transaction=" + transaction);
////                transaction.end();
//                contextMap.put(this, transaction);
//            } else {
//                System.out.println("AdHocSubscriber " + this.hashCode() + " "+ debugId + " transaction is null! " + transaction);
//            }

        }
    }

    private void discardIf(boolean condition) {
        if (!condition) {
            return;
        }
        //clean context
    }

    @Override
    public void onSubscribe(Subscription s) {
        System.out.println("AdHocSubscriber " + this.hashCode() + " " + debugPrefix + " onSubscribe s=" + s
            + " thread=" + Thread.currentThread().getName());

        AbstractSpan<?> context = getContext();
        boolean hasActivated = doEnter("onSubscribe", context);

        Throwable thrown = null;
        try {
            if (subscriber != null) {
                subscriber.onSubscribe(s);
            } else {
                s.request(Long.MAX_VALUE);
            }
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(hasActivated, "onSubscribe", context);
            discardIf(thrown != null);
        }
    }

    @Override
    public void onNext(T t) {
        AbstractSpan<?> context = getContext();
        boolean hasActivated = doEnter("onNext", context);

        System.out.println("AdHocSubscriber " + this.hashCode() + " " + debugPrefix + " ctx=" + context + " onNext " + debugPrefix
            + "lift " + t + " thread=" + Thread.currentThread().getName());

        Throwable thrown = null;
        try {
            if (subscriber != null) {
                subscriber.onNext(t);
            }
            if (context != null) {
                Span itemSpan = context.getTransaction().createSpan()
                    .withName("fluxItem-" + debugPrefix + "-" + t.toString() + " " + t.getClass())
                    .withSubtype("webflux item");

                itemSpan.activate();
                itemSpan.addLabel("item", (String) t.toString());
                itemSpan.deactivate();
                itemSpan.end();
            } else {
                System.out.println("AdHocSubscriber " + this.hashCode() + " " + debugPrefix + " ending ctx but ctx is null!=" + context
                    + " on " + Thread.currentThread().getName() + " active=" + tracer.getActive());
            }
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(hasActivated, "onNext", context);
            discardIf(thrown != null);
        }
    }

    @Override
    public void onError(Throwable throwable) {

        AbstractSpan<?> context = getContext();
        boolean hasActivated = doEnter("onError", context);
        System.out.println("AdHocSubscriber " + this.hashCode() + " " + debugPrefix + " onError " + debugPrefix
            + " thread=" + Thread.currentThread().getName() + " context=" + context + " hasActivated=" + hasActivated);

        try {

            if (subscriber != null) {
                subscriber.onError(throwable);
            }
            if (context != null) {
                context = context.withOutcome(Outcome.FAILURE);
                System.out.println("AdHocSubscriber " + this.hashCode() + " " + debugPrefix + " onError context "
                    + context + " outcome=" + context.getOutcome());
            } else {
                System.out.println("AdHocSubscriber " + this.hashCode() + " " + debugPrefix + " onError context is null!? ctx=" + context);
            }


        } finally {
            doExit(hasActivated, "onError", context, true);

            discardIf(true);
        }
    }

    @Override
    public void onComplete() {
        System.out.println("AdHocSubscriber " + this.hashCode() + " " + debugPrefix + " onComplete " + debugPrefix
            + " thread=" + Thread.currentThread().getName());
        AbstractSpan<?> context = getContext();
        boolean hasActivated = doEnter("onComplete", context);

        try {
            if (subscriber != null) {
                subscriber.onComplete();
            }
            if (context != null) {
                context.withOutcome(Outcome.SUCCESS);
                System.out.println("AdHocSubscriber " + this.hashCode() + " " + debugPrefix + " onComplete ctx=" + context + " "
                    + context.getOutcome());
            } else {
                System.out.println("AdHocSubscriber " + this.hashCode() + " " + debugPrefix + " onComplete context is null!? ctx="
                    + context);
            }


        } finally {
            doExit(hasActivated, "onComplete", context, true);

            discardIf(true);
        }
    }

    static public ConcurrentHashMap<String, AbstractSpan> getWebClientMap() {
        return webClientTransactionMap;
    }

    static public ConcurrentHashMap<String, AbstractSpan> getLogPrefixMap() {
        return logPrefixTransactionMap;
    }

    static public ConcurrentHashMap<String, Object> getLogPrefixSubscriberMap() {
        return logPrefixSubscriberMap;
    }
}
