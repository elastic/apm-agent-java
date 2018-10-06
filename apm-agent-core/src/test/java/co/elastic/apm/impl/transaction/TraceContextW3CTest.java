package co.elastic.apm.impl.transaction;

import co.elastic.apm.util.PotentiallyMultiValuedMap;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

public class TraceContextW3CTest {

    private JsonNode testData;


    @BeforeEach
    void setUp() throws IOException {
        testData = new ObjectMapper()
            .enable(JsonParser.Feature.ALLOW_COMMENTS)
            .readTree(getClass().getResource("/w3c_test_data.json"));
    }

    @Test
    void testW3CData() {
        assertSoftly(softly -> {
            for (JsonNode testCase : testData) {
                PotentiallyMultiValuedMap headersMap = getHeaders(testCase.get("headers"));
                if (headersMap.getAll("traceparent").size() == 1) {
                    final String traceParentHeader = headersMap.getFirst("traceparent");
                    final boolean traceparentValid = testCase.get("is_traceparent_valid").booleanValue();
                    final TraceContext traceContext = TraceContext.with64BitId();
                    softly.assertThat(traceContext.asChildOf(traceParentHeader))
                        .withFailMessage("Expected '%s' to be %s", traceParentHeader, traceparentValid ? "valid" : "invalid")
                        .isEqualTo(traceparentValid);
                }
            }
        });
    }

    private PotentiallyMultiValuedMap getHeaders(JsonNode headers) {
        final PotentiallyMultiValuedMap map = new PotentiallyMultiValuedMap();
        for (JsonNode header : headers) {
            map.add(header.get(0).textValue(), header.get(1).textValue());
        }
        return map;
    }
}
