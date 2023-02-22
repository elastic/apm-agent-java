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
package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface Tracer {

    @Nullable
    Transaction<?> startRootTransaction(@Nullable ClassLoader initiatingClassLoader);

    @Nullable
    Transaction<?> startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro);

    @Nullable
    <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader);

    @Nullable
    <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros);

    @Nullable
    <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader);

    @Nullable
    Transaction<?> currentTransaction();

    @Nullable
    AbstractSpan<?> getActive();

    @Nullable
    Span<?> getActiveSpan();

    void captureAndReportException(@Nullable Throwable e, ClassLoader initiatingClassLoader);

    @Nullable
    String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent);

    @Nullable
    ErrorCapture captureException(@Nullable Throwable e, @Nullable AbstractSpan<?> parent, @Nullable ClassLoader initiatingClassLoader);

    @Nullable
    Span<?> getActiveExitSpan();

    @Nullable
    Span<?> createExitChildSpan();

    void endSpan(Span<?> span);

    void endTransaction(Transaction<?> transaction);

    void endError(ErrorCapture errorCapture);

    <T> T getConfig(Class<T> configuration);

    ObjectPoolFactory getObjectPoolFactory();

    boolean isRunning();

    void setServiceInfoForClassLoader(ClassLoader classLoader, ServiceInfo serviceInfo);

    ServiceInfo getServiceInfoForClassLoader(ClassLoader classLoader);

    ServiceInfo autoDetectedServiceName();
}
