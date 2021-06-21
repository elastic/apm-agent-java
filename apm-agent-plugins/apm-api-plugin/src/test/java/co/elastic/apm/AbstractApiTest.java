package co.elastic.apm;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import org.junit.Before;
import org.junit.jupiter.api.BeforeEach;

public class AbstractApiTest extends AbstractInstrumentationTest {

    @Before
    @BeforeEach
    public void disableRecycling() {
        disableRecyclingValidation();
    }
}
