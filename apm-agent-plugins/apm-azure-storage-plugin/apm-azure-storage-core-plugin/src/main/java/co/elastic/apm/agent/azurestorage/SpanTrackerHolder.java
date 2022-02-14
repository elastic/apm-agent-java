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
package co.elastic.apm.agent.azurestorage;

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.weakconcurrent.DetachedThreadLocal;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;

import javax.annotation.Nullable;

public class SpanTrackerHolder {
    private static DetachedThreadLocal<SpanTrackerHolder> detachedThreadLocal = WeakConcurrent.buildThreadLocal();

    public static SpanTrackerHolder getSpanTrackHolder() {
        SpanTrackerHolder spanTrackerHolder = detachedThreadLocal.get();
        if (spanTrackerHolder == null) {
            spanTrackerHolder = new SpanTrackerHolder();
            detachedThreadLocal.set(spanTrackerHolder);
        }
        return spanTrackerHolder;
    }
    public static void removeSpanTrackHolder() {
        detachedThreadLocal.remove();
    }

    public static boolean isCreated() {
        return detachedThreadLocal.get() != null;
    }

    private SpanTrackerHolder() {
    }

    @Nullable
    private Span span;
    private boolean storageEntrypointCreated;

    @Nullable
    public Span getSpan() {
        return span;
    }

    public void setSpan(Span span) {
        this.span = span;
    }

    public boolean isStorageEntrypointCreated() {
        return storageEntrypointCreated;
    }

    public void setStorageEntrypointCreated(boolean storageEntrypointCreated) {
        this.storageEntrypointCreated = storageEntrypointCreated;
    }
}
