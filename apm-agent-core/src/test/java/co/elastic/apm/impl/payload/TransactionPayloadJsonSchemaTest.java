package co.elastic.apm.impl.payload;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.sampling.ConstantSampler;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TransactionPayloadJsonSchemaTest {

    private TransactionPayload payload;
    private JsonSchema schema;

    @BeforeEach
    void setUp() {
        payload = createPayloadWithRequiredValues();
        payload.getTransactions().add(createTransactionWithRequiredValues());
        schema = JsonSchemaFactory.getInstance().getSchema(getClass().getResourceAsStream("/schema/transactions/payload.json"));
    }

    private TransactionPayload createPayloadWithRequiredValues() {
        Service service = new Service().withAgent(new Agent("name", "version")).withName("name");
        SystemInfo system = new SystemInfo("", "", "");
        return new TransactionPayload(new ProcessInfo("title"), service, system);
    }

    private Transaction createTransactionWithRequiredValues() {
        Transaction t = new Transaction();
        t.start(mock(ElasticApmTracer.class), 0, ConstantSampler.of(true));
        t.setType("type");
        t.getContext().getRequest().withMethod("GET");
        Span s = new Span();
        s.start(mock(ElasticApmTracer.class), t, null, 0, false)
            .withType("type")
            .withName("name");
        t.addSpan(s);
        return t;
    }

    @Test
    void testJsonSchema() {
        Set<ValidationMessage> errors = schema.validate(new ObjectMapper().valueToTree(payload));
        assertThat(errors).isEmpty();
    }
}
