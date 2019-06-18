/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.web.WebConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@VisibleForAdvice
public class JaxRsTransactionHelper {

    private final WebConfiguration webConfiguration;

    @VisibleForAdvice
    public JaxRsTransactionHelper(ElasticApmTracer tracer) {
        this.webConfiguration = tracer.getConfig(WebConfiguration.class);
    }

    @VisibleForAdvice
    public void setTransactionName(@Nonnull Transaction currentTransaction,
                                   @Nonnull String signature,
                                   @Nullable String pathAnnotationValue) {
        String transactionName = signature;
        if (webConfiguration.isUseAnnotationValueForTransactionName()) {
            if (pathAnnotationValue != null) {
                transactionName = pathAnnotationValue;
            }
        }
        currentTransaction.withName(transactionName);
    }

}
