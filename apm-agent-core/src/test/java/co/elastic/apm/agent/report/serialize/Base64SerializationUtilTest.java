package co.elastic.apm.agent.report.serialize;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Random;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class Base64SerializationUtilTest {

    @Test
    public void empty() {
        JsonWriter jw = new DslJson<>(new DslJson.Settings<>()).newWriter();
        Base64SerializationUtils.writeBytesAsBase64UrlSafe(new byte[0], jw);
        assertThat(jw.size()).isEqualTo(0);
    }

    @Test
    public void randomInputs() {
        DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());

        Base64.Encoder reference = Base64.getUrlEncoder().withoutPadding();

        Random rnd = new Random(42);
        for (int i = 0; i < 100_000; i++) {
            int len = rnd.nextInt(31) + 1;
            byte[] data = new byte[len];
            rnd.nextBytes(data);

            String expectedResult = reference.encodeToString(data);

            JsonWriter jw = dslJson.newWriter();
            Base64SerializationUtils.writeBytesAsBase64UrlSafe(data, jw);

            assertThat(jw.toString()).isEqualTo(expectedResult);
        }
    }
}
