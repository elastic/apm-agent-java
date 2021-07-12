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
package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.impl.transaction.Outcome;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcHelperTest {

    @ParameterizedTest
    @EnumSource(Status.Code.class)
    void statusMapping(Status.Code grpcCode) {

        Status status = grpcCode.toStatus();

        assertThat(GrpcHelper.toClientOutcome(status)).isEqualTo(status.isOk() ? Outcome.SUCCESS : Outcome.FAILURE);
    }

    @Test
    void noStatusMapping(){
        assertThat(GrpcHelper.toClientOutcome(null))
            .isEqualTo(Outcome.FAILURE);
    }

}
