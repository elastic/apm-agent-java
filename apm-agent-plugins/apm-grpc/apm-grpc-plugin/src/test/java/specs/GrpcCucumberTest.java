package specs;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(strict = true, plugin = {"pretty"}, tags = "@grpc")
public class GrpcCucumberTest {
}
