package co.elastic.apm.agent.resttemplate;

public class SpringRestTemplateIntegrationNoOp extends SprintRestTemplateIntegration {

    public SpringRestTemplateIntegrationNoOp() {
        super(false);
    }
}
