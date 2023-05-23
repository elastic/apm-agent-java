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
package co.elastic.apm.agent.awslambda.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import com.amazonaws.services.lambda.runtime.Context;

import javax.annotation.Nullable;

public class PlainTransactionHelper extends AbstractLambdaTransactionHelper<Object, Object> {

    protected static final String TRANSACTION_TYPE = "request";

    @Nullable
    private static PlainTransactionHelper INSTANCE;

    private PlainTransactionHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    public static PlainTransactionHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PlainTransactionHelper(GlobalTracer.get().require(ElasticApmTracer.class));
        }
        return INSTANCE;
    }

    @Override
    protected Transaction doStartTransaction(Object input, Context lambdaContext) {
        return tracer.startRootTransaction(PrivilegedActionUtils.getClassLoader(lambdaContext.getClass()));
    }

    @Override
    public void captureOutputForTransaction(Transaction transaction, Object output) {
        // Nothing to do here
    }

    @Override
    protected void setTransactionTriggerData(Transaction transaction, Object input) {
        transaction.getFaas().getTrigger().withType("other");
        transaction.withType(TRANSACTION_TYPE);
    }
}
