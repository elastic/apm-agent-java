package co.elastic.apm.impl.transaction;

import co.elastic.apm.TransactionUtils;
import org.junit.jupiter.api.Test;

import static co.elastic.apm.JsonUtils.toJson;
import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    @Test
    void resetState() {
        final Transaction transaction = new Transaction();
        TransactionUtils.fillTransaction(transaction);
        transaction.resetState();
        assertThat(toJson(transaction)).isEqualTo(toJson(new Transaction()));
    }
}
