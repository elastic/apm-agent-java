package co.elastic.apm.agent.awssdk.v2;

import co.elastic.apm.agent.awssdk.common.AbstractAwsClientIT;

public abstract class AbstractAws2ClientIT extends AbstractAwsClientIT {

    public static final String LOCALSTACK_VERSION = "3.0.2";

    public AbstractAws2ClientIT() {
        super(LOCALSTACK_VERSION);
    }
}
