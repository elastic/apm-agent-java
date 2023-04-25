package co.elastic.apm.agent.tracer.configuration;

public interface ServerlessConfiguration {

    String getAwsLambdaHandler();

    long getDataFlushTimeout();

    boolean runsOnAwsLambda();
}
