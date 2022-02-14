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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class AzureStorageInstrumentationStorageEntrypoint extends TracerAwareInstrumentation {
    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return ElementMatchers.nameStartsWith("com.azure.storage.blob");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.azure.storage.blob.BlobAsyncClient")
            .or(named("com.azure.storage.blob.BlobClient"))
            .or(named("com.azure.storage.blob.BlobContainerAsyncClient"))
            .or(named("com.azure.storage.blob.BlobContainerClient"))
            .or(named("com.azure.storage.blob.BlobServiceAsyncClient"))
            .or(named("com.azure.storage.blob.BlobServiceClient"))
            .or(named("com.azure.storage.blob.implementation.AppendBlobsImpl"))
            .or(named("com.azure.storage.blob.implementation.BlobsImpl"))
            .or(named("com.azure.storage.blob.implementation.BlockBlobsImpl"))
            .or(named("com.azure.storage.blob.implementation.ContainersImpl"))
            .or(named("com.azure.storage.blob.implementation.PageBlobsImpl"))
            .or(named("com.azure.storage.blob.implementation.ServicesImpl"))
            .or(named("com.azure.storage.blob.specialized.AppendBlobAsyncClient"))
            .or(named("com.azure.storage.blob.specialized.AppendBlobClient"))
            .or(named("com.azure.storage.blob.specialized.BlobAsyncClientBase"))
            .or(named("com.azure.storage.blob.specialized.BlobClientBase"))
            .or(named("com.azure.storage.blob.specialized.BlobLeaseAsyncClient"))
            .or(named("com.azure.storage.blob.specialized.BlobLeaseClient"))
            .or(named("com.azure.storage.blob.specialized.BlockBlobAsyncClient"))
            .or(named("com.azure.storage.blob.specialized.BlockBlobClient"))
            .or(named("com.azure.storage.blob.specialized.PageBlobAsyncClient"))
            .or(named("com.azure.storage.blob.specialized.PageBlobClient"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isAnnotatedWith(named("com.azure.core.annotation.ServiceMethod"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("azurestorage");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.azurestorage.AzureStorageInstrumentationStorageEntrypoint$StorageEntrypointAdvice";
    }

    public static class StorageEntrypointAdvice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter() {
            if (!SpanTrackerHolder.isCreated()) {
                SpanTrackerHolder spanTrackerHolder = SpanTrackerHolder.getSpanTrackHolder();
                spanTrackerHolder.setStorageEntrypointCreated(true);
                return spanTrackerHolder;
            }
            return null;
        }

        @Nullable
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                                  @Nullable @Advice.Enter Object spanTrackerHolderObj) {
            if (thrown != null ) {
                // in case of thrown exception, we don't need to wrap to end transaction
                return ;
            }
            SpanTrackerHolder spanTrackerHolder = (SpanTrackerHolder) spanTrackerHolderObj;
            if (spanTrackerHolder != null && spanTrackerHolder.isStorageEntrypointCreated()) {
                if (spanTrackerHolder.getSpan() != null) {
                    spanTrackerHolder.getSpan().captureException(thrown).deactivate();
                    spanTrackerHolder.getSpan().end();
                }
                SpanTrackerHolder.removeSpanTrackHolder();
            }
        }

    }
}
