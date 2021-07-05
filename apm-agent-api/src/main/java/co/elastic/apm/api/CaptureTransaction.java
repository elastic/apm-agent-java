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
package co.elastic.apm.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotating a method with {@code @}{@link CaptureTransaction} creates a {@link Transaction} for that method.
 * <p>
 * Note that this only works when there is no active transaction on the same thread.
 * </p>
 * <p>
 * Note: it is required to configure the {@code application_packages}, otherwise this annotation will be ignored.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CaptureTransaction {

    /**
     * The name of the {@link Transaction}.
     * Defaults to the {@code ClassName#methodName}
     */
    String value() default "";

    /**
     * The type of the transaction.
     */
    String type() default Transaction.TYPE_REQUEST;
}
