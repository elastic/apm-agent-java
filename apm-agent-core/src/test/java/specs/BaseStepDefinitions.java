package specs;

import io.cucumber.java.en.Given;

public class BaseStepDefinitions {

    @Given("an agent")
    public void initAgent() {
        // not used, use before/after hooks instead for init & cleanup
    }
}
