package co.elastic.apm.agent.awssdk.v1;

import co.elastic.apm.agent.awssdk.common.AbstractAwsClientIT;

public abstract class AbstractAws1ClientIT extends AbstractAwsClientIT {

    public static final String LOCALSTACK_VERSION = "0.14.2";

    public AbstractAws1ClientIT() {
        super(LOCALSTACK_VERSION);
    }
}
