package co.elastic.apm.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomeTest {

    @Test
    void apiOutcomeMatchesInternalOutcome() {

        Set<String> apiEnumNames = getEnumValues(Outcome.values());
        Set<String> internalEnumNames = getEnumValues(co.elastic.apm.agent.impl.transaction.Outcome.values());

        assertThat(apiEnumNames)
            .containsExactlyInAnyOrderElementsOf(internalEnumNames);

    }

    private Set<String> getEnumValues(Enum<?>[] values) {
        return Arrays.stream(values)
            .map(Enum::name)
            .collect(Collectors.toSet());
    }


}
