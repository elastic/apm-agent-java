package specs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;

public class TestJsonSpec {

    public static JsonNode getJson(String jsonFile) {
        return getJson(TestJsonSpec.class, "json-specs/" + jsonFile);
    }

    public static JsonNode getJson(Class<?> type, String path) {
        URL jsonSpec = type.getClassLoader().getResource(path);
        try {
            return new ObjectMapper().readTree(jsonSpec);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
