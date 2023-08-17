package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.tracer.util.HexUtils;
import co.elastic.apm.agent.util.ByteUtils;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HexSerializationUtilsTest {

    @Test
    void testLongToHex() {
        byte[] bytes = new byte[8];
        HexUtils.nextBytes("09c2572177fdae24", 0, bytes);
        long l = ByteUtils.getLong(bytes, 0);
        JsonWriter jw = new DslJson<>().newWriter();
        HexSerializationUtils.writeAsHex(l, jw);
        assertThat(jw.toString()).isEqualTo("09c2572177fdae24");
    }
}
