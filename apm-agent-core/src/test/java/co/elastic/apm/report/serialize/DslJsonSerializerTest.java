package co.elastic.apm.report.serialize;

import com.dslplatform.json.JsonWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;


class DslJsonSerializerTest {

    private DslJsonSerializer serializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        serializer = new DslJsonSerializer();
        objectMapper = new ObjectMapper();
    }

    @Test
    void serializeTags() {
        assertSoftly(softly -> {
            softly.assertThat(serializeTags(Map.of(".**", "foo.bar"))).isEqualTo(toJson(Map.of("___", "foo.bar")));
            softly.assertThat(serializeTags(Map.of("foo.bar", "baz"))).isEqualTo(toJson(Map.of("foo_bar", "baz")));
            softly.assertThat(serializeTags(Map.of("foo.bar.baz", "qux"))).isEqualTo(toJson(Map.of("foo_bar_baz", "qux")));
            softly.assertThat(serializeTags(Map.of("foo*bar*baz", "qux"))).isEqualTo(toJson(Map.of("foo_bar_baz", "qux")));
            softly.assertThat(serializeTags(Map.of("foo\"bar\"baz", "qux"))).isEqualTo(toJson(Map.of("foo_bar_baz", "qux")));
        });
    }

    @Test
    void testLimitStringValueLength() throws IOException {
        StringBuilder longValue = new StringBuilder(DslJsonSerializer.MAX_VALUE_LENGTH + 1);
        for (int i = 0; i < DslJsonSerializer.MAX_VALUE_LENGTH + 1; i++) {
            longValue.append('0');
        }

        serializer.jw.writeByte(JsonWriter.OBJECT_START);
        serializer.writeField("stringBuilder", longValue);
        serializer.writeField("string", longValue.toString());
        serializer.writeLastField("lastString", longValue.toString());
        serializer.jw.writeByte(JsonWriter.OBJECT_END);
        final JsonNode jsonNode = objectMapper.readTree(serializer.jw.toString());
        assertThat(jsonNode.get("stringBuilder").textValue()).hasSize(DslJsonSerializer.MAX_VALUE_LENGTH).endsWith("…");
        assertThat(jsonNode.get("string").textValue()).hasSize(DslJsonSerializer.MAX_VALUE_LENGTH).endsWith("…");
        assertThat(jsonNode.get("lastString").textValue()).hasSize(DslJsonSerializer.MAX_VALUE_LENGTH).endsWith("…");
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String serializeTags(Map<String, String> tags) {
        serializer.serializeTags(tags);
        final String jsonString = serializer.jw.toString();
        serializer.jw.reset();
        return jsonString;
    }
}
