/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.api;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Function;

/**
 * This class is the main entry point of the public API for the Elastic APM agent.
 * <p>
 * The tracer gives you access to the currently active transaction and span.
 * It can also be used to track an exception.
 * To use the API, you can just invoke the static methods on this class.
 * </p>
 * Use this API to set a custom transaction name,
 * for example:
 * <pre>{@code
 * ElasticApm.currentTransaction().setName("SuchController#muchMethod");
 * }</pre>
 */
/*
 * Implementation note:
 * The parameters of static methods are linked eagerly.
 * In order to be able to refer to Java 8 types but still be Java 7 compatible,
 * The Java 7 compatible code is extracted to a super class.
 * We take advantage of the fact that static methods are inherited as well.
 * So on Java 8, you can just call ElasticApm.startTransaction(),
 * even though that method is defined in ElasticApm's super class ElasticApmJava7.
 *
 * When stuck on Java 7, just call ElasticApmJava7.startTransaction().
 * Observation: actually, it also seems to work to call ElasticApm.startTransaction().
 * I assume the JVM does not eagerly link the methods when only referring to static methods of the super class.
 */
public class ElasticApm extends ElasticApmJava7 {

    /**
     * Similar to {@link ElasticApm#startTransaction()} but creates this transaction as the child of a remote parent.
     *
     * <p>
     * Example:
     * </p>
     * <pre>
     * Transaction transaction = ElasticApm.startTransactionWithRemoteParent(request::getHeader);
     * </pre>
     * <p>
     * Note: If the protocol supports multi-value headers, use {@link #startTransactionWithRemoteParent(Function, Function)}
     * </p>
     * <p>
     * Note: This method can only be used on Java 8+.
     * If you are stuck on Java 7, use {@link ElasticApm#startTransactionWithRemoteParent(Map)}.
     * </p>
     *
     * @param getFirstHeader a function which receives a header name and returns the fist header with that name
     * @return the started transaction
     * @since 1.3.0
     */
    @Nonnull
    @IgnoreJRERequirement
    public static Transaction startTransactionWithRemoteParent(final Function<String, String> getFirstHeader) {
        return startTransactionWithRemoteParent(getFirstHeader, null);
    }

    /**
     * Similar to {@link ElasticApm#startTransaction()} but creates this transaction as the child of a remote parent.
     *
     * <p>
     * Example:
     * </p>
     * <pre>
     * Transaction transaction = ElasticApm.startTransactionWithRemoteParent(request::getHeader, request::getHeaders);
     * </pre>
     * <p>
     * Note: If the protocol does not support multi-value headers, use {@link #startTransactionWithRemoteParent(Function)}
     * </p>
     * <p>
     * Note: This method can only be used on Java 8+.
     * If you are stuck on Java 7, use {@link ElasticApm#startTransactionWithRemoteParent(Map)}.
     * </p>
     *
     * @param getFirstHeader a function which receives a header name and returns the fist header with that name
     * @param getAllHeaders  a function which receives a header name and returns all headers with that name
     * @return the started transaction
     * @since 1.3.0
     */
    @Nonnull
    @IgnoreJRERequirement
    public static Transaction startTransactionWithRemoteParent(Function<String, String> getFirstHeader, Function<String, Iterable<String>> getAllHeaders) {
        Object transaction = doStartTransactionWithRemoteParentFunction(getFirstHeader, getAllHeaders);
        return transaction != null ? new TransactionImpl(transaction) : NoopTransaction.INSTANCE;
    }

    @IgnoreJRERequirement
    private static Object doStartTransactionWithRemoteParentFunction(Function<String, String> getFirstHeader, Function<String, Iterable<String>> getAllHeaders) {
        // co.elastic.apm.agent.plugin.api.ElasticApmApiInstrumentation.StartTransactionWithRemoteParentInstrumentation
        return null;
    }
}
