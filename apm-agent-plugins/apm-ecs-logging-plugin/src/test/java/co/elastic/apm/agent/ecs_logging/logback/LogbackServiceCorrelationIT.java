package co.elastic.apm.agent.ecs_logging.logback;

import co.elastic.apm.agent.ecs_logging.EcsServiceCorrelationIT;

public class LogbackServiceCorrelationIT extends EcsServiceCorrelationIT {

    @Override
    protected String getArtifactName() {
        return "logback-ecs-encoder";
    }

    @Override
    protected String getServiceNameTestClass() {
        return "co.elastic.apm.agent.ecs_logging.logback.LogbackServiceNameInstrumentationTest";
    }

    @Override
    protected String getServiceVersionTestClass() {
        return "co.elastic.apm.agent.ecs_logging.logback.LogbackServiceVersionInstrumentationTest";
    }
}
