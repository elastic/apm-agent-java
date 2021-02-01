package specs;

import co.elastic.apm.agent.grpc.GrpcHelper;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
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
        Span span = state.startSpan();

        span.withName(String.format("gRPC span %s", grpcStatus))
            .withOutcome(getOutcome(grpcStatus, GrpcHelper::toClientOutcome));
    }

    @Given("a gRPC transaction with {string} status")
    public void grpcTransaction(String grpcStatus){
        Transaction transaction = state.startTransaction();

        transaction.withName(String.format("gRPC transaction %s", grpcStatus))
            .withOutcome(getOutcome(grpcStatus, GrpcHelper::toServerOutcome));
    }

    private static Outcome getOutcome(String grpcStatus, Function<Status,Outcome> mapFunction) {
        Status status = null;
        if (!"n/a".equals(grpcStatus)) {
            status = Status.fromCode(Status.Code.valueOf(grpcStatus));
        }
        return mapFunction.apply(status);
    }


}
