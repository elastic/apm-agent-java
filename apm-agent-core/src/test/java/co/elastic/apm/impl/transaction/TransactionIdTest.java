package co.elastic.apm.impl.transaction;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class TransactionIdTest {

    @Test
    void toUuid() {
        TransactionId id = new TransactionId();
        id.setToRandomValue();
        UUID uuid = id.toUuid();
        assertThat(uuid.getMostSignificantBits()).isEqualTo(id.getMostSignificantBits());
        assertThat(uuid.getLeastSignificantBits()).isEqualTo(id.getLeastSignificantBits());
    }
}
