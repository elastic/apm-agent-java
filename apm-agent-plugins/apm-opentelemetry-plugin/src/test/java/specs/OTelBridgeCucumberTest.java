package specs;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(strict = true, plugin = {"pretty"}, tags = "@opentelemetry-bridge and @wip")
public class OTelBridgeCucumberTest {

}
