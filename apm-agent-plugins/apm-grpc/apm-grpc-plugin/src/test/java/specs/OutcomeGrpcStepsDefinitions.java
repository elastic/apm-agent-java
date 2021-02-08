/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package specs;

import co.elastic.apm.agent.grpc.GrpcHelper;
import co.elastic.apm.agent.impl.transaction.Outcome;
import io.cucumber.java.en.Given;
import io.grpc.Status;

import java.util.function.Function;

public class OutcomeGrpcStepsDefinitions {

    private final OutcomeState state;

    public OutcomeGrpcStepsDefinitions(OutcomeState state) {
        this.state = state;
    }

    @Given("a gRPC span with {string} status")
    public void grpcSpan(String grpcStatus) {

        state.startRootTransactionIfRequired();

        state.startSpan()
            .withName(String.format("gRPC span %s", grpcStatus))
            .withOutcome(getOutcome(grpcStatus, GrpcHelper::toClientOutcome));
    }

    @Given("a gRPC transaction with {string} status")
    public void grpcTransaction(String grpcStatus) {
        state.startTransaction()
            .withName(String.format("gRPC transaction %s", grpcStatus))
            .withOutcome(getOutcome(grpcStatus, GrpcHelper::toServerOutcome));
    }

    private static Outcome getOutcome(String grpcStatus, Function<Status, Outcome> mapFunction) {
        Status status = null;
        if (!"n/a".equals(grpcStatus)) {
            status = Status.fromCode(Status.Code.valueOf(grpcStatus));
        }
        return mapFunction.apply(status);
    }


}
