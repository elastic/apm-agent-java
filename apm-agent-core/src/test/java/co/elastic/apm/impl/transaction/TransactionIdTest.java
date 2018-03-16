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

    @Test
    void testCopy() {
        TransactionId id1 = new TransactionId();
        TransactionId id2 = new TransactionId();
        assertThat(id1.getLeastSignificantBits()).isEqualTo(0);
        assertThat(id1).isEqualTo(id2);
        id1.setToRandomValue();
        assertThat(id1).isNotEqualTo(id2);

        id2.copyFrom(id1);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.getLeastSignificantBits()).isNotEqualTo(0);
    }
}
