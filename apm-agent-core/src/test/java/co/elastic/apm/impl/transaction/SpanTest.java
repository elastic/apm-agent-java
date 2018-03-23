package co.elastic.apm.impl.transaction;

import org.junit.jupiter.api.Test;

import static co.elastic.apm.JsonUtils.toJson;
import static org.assertj.core.api.Assertions.assertThat;

class SpanTest {

    @Test
    void resetState() {
        Span span = new Span()
            .withName("SELECT FROM product_types")
            .withType("db.postgresql.query");
        span.getContext().getDb()
            .withInstance("customers")
            .withStatement("SELECT * FROM product_types WHERE user_id=?")
            .withType("sql")
            .withUser("readonly_user");
        span.resetState();
        assertThat(toJson(span)).isEqualTo(toJson(new Span()));
    }
}
