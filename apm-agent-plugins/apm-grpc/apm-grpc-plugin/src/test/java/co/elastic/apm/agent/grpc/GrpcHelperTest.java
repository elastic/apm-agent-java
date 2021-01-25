package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.impl.transaction.Outcome;
import io.grpc.Status;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcHelperTest {

    @ParameterizedTest
    @EnumSource(Status.Code.class)
    void statusMapping(Status.Code grpcCode) {

        Status status = grpcCode.toStatus();

        assertThat(GrpcHelper.toOutcome(status)).isEqualTo(status.isOk() ? Outcome.SUCCESS : Outcome.FAILURE);
    }

}
