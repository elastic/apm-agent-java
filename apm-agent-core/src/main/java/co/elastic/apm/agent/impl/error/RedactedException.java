package co.elastic.apm.agent.impl.error;

public class RedactedException extends Exception {

    public RedactedException() {
        super("This exception is a placeholder for an exception which has occurred in the application." +
            "The stacktrace corresponds to where elastic APM detected the exception.");
    }

}
