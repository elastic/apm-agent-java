package co.elastic.apm.agent.awslambda.lambdas;

import co.elastic.apm.agent.impl.GlobalTracer;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.Objects;

public abstract class AbstractFunction<ReqE, ResE> implements RequestHandler<ReqE, ResE> {

    protected void createChildSpan() {
        Objects.requireNonNull(GlobalTracer.requireTracerImpl().getActive())
            .createSpan()
            .withName("child-span")
            .activate()
            .deactivate()
            .end();
    }

    protected void raiseException(Context context) {
        if (((TestContext) context).shouldRaiseException()) {
            throw new RuntimeException("Requested to raise error");
        }
    }
}
