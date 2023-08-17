package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.kafka.helper.KafkaRecordHeaderAccessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaRecordHeaderAccessorTest {

    @Test
    public void testLegacyHeaderSetterTranslation() {
        String W3C_HEADER = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01";
        byte[] binary_header = {
            0, //version
            0, //trace-id field-id
            0x0a, (byte) 0xf7, 0x65, 0x19, 0x16, (byte) 0xcd, 0x43, (byte) 0xdd,
            (byte) 0x84, 0x48, (byte) 0xeb, 0x21, 0x1c, (byte) 0x80, 0x31, (byte) 0x9c,
            1, //parent-id field-id
            0x00, (byte) 0xf0, 0x67, (byte) 0xaa, 0x0b, (byte) 0xa9, 0x02, (byte) 0xb7,
            2, //flags field-id
            0x01,
        };
        ProducerRecord<String, String> dummyRecord = new ProducerRecord<String, String>("", 0, "", "");
        //set twice to ensure it is not added twice
        KafkaRecordHeaderAccessor.instance().setHeader("elastic-apm-traceparent", W3C_HEADER, dummyRecord);
        KafkaRecordHeaderAccessor.instance().setHeader("elastic-apm-traceparent", W3C_HEADER, dummyRecord);

        assertThat(dummyRecord.headers()).hasSize(1);
        assertThat(dummyRecord.headers().lastHeader("elasticapmtraceparent").value())
            .isEqualTo(binary_header);

        KafkaRecordHeaderAccessor.instance().remove("elastic-apm-traceparent", dummyRecord);
        assertThat(dummyRecord.headers()).hasSize(0);
    }


    @Test
    public void testLegacyHeaderGetterTranslation() {
        String W3C_HEADER = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01";
        byte[] binary_header = {
            0, //version
            0, //trace-id field-id
            0x0a, (byte) 0xf7, 0x65, 0x19, 0x16, (byte) 0xcd, 0x43, (byte) 0xdd,
            (byte) 0x84, 0x48, (byte) 0xeb, 0x21, 0x1c, (byte) 0x80, 0x31, (byte) 0x9c,
            1, //parent-id field-id
            0x00, (byte) 0xf0, 0x67, (byte) 0xaa, 0x0b, (byte) 0xa9, 0x02, (byte) 0xb7,
            2, //flags field-id
            0x01,
        };
        Headers headers = new RecordHeaders().add("elasticapmtraceparent", binary_header);
        ConsumerRecord<String, String> dummyRecord = new ConsumerRecord<String, String>("", 0, 0, -1L, TimestampType.NO_TIMESTAMP_TYPE, -1, -1, "", "", headers, Optional.empty());

        String headerText = KafkaRecordHeaderAccessor.instance().getFirstHeader("elastic-apm-traceparent", dummyRecord);
        assertThat(headerText).isEqualTo(W3C_HEADER);

        List<String> allHeaders = new ArrayList<>();
        KafkaRecordHeaderAccessor.instance().forEach("elastic-apm-traceparent", dummyRecord, null, (val, state) -> allHeaders.add(val));
        assertThat(allHeaders).containsExactly(W3C_HEADER);
    }


    @Test
    public void testInvalidLegacyHeaderGetterTranslation() {
        byte[] binary_header = {42};
        Headers headers = new RecordHeaders().add("elasticapmtraceparent", binary_header);
        ConsumerRecord<String, String> dummyRecord = new ConsumerRecord<String, String>("", 0, 0, -1L, TimestampType.NO_TIMESTAMP_TYPE, -1, -1, "", "", headers, Optional.empty());

        String headerText = KafkaRecordHeaderAccessor.instance().getFirstHeader("elastic-apm-traceparent", dummyRecord);
        assertThat(headerText).isNull();

        List<String> allHeaders = new ArrayList<>();
        KafkaRecordHeaderAccessor.instance().forEach("elastic-apm-traceparent", dummyRecord, null, (val, state) -> allHeaders.add(val));
        assertThat(allHeaders).isEmpty();
    }


    @Test
    public void testW3cHeaderSetter() {
        String W3C_HEADER = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01";
        ProducerRecord<String, String> dummyRecord = new ProducerRecord<String, String>("", 0, "", "");
        //set twice to ensure it is not added twice
        KafkaRecordHeaderAccessor.instance().setHeader("traceparent", W3C_HEADER, dummyRecord);
        KafkaRecordHeaderAccessor.instance().setHeader("traceparent", W3C_HEADER, dummyRecord);

        assertThat(dummyRecord.headers()).hasSize(1);
        assertThat(dummyRecord.headers().lastHeader("traceparent").value())
            .isEqualTo(W3C_HEADER.getBytes(StandardCharsets.UTF_8));

        KafkaRecordHeaderAccessor.instance().remove("traceparent", dummyRecord);
        assertThat(dummyRecord.headers()).hasSize(0);
    }


    @Test
    public void testW3CHeaderGetter() {
        String W3C_HEADER = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01";
        Headers headers = new RecordHeaders().add("traceparent", W3C_HEADER.getBytes(StandardCharsets.UTF_8));
        ConsumerRecord<String, String> dummyRecord = new ConsumerRecord<String, String>("", 0, 0, -1L, TimestampType.NO_TIMESTAMP_TYPE, -1, -1, "", "", headers, Optional.empty());

        String headerText = KafkaRecordHeaderAccessor.instance().getFirstHeader("traceparent", dummyRecord);
        assertThat(headerText).isEqualTo(W3C_HEADER);

        List<String> allHeaders = new ArrayList<>();
        KafkaRecordHeaderAccessor.instance().forEach("traceparent", dummyRecord, null, (val, state) -> allHeaders.add(val));
        assertThat(allHeaders).containsExactly(W3C_HEADER);
    }

}
