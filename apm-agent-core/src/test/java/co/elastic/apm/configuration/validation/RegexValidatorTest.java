package co.elastic.apm.configuration.validation;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class RegexValidatorTest {

    @Test
    void testRegexValidator() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThatCode(() -> RegexValidator.of("foo").assertValid("foo")).doesNotThrowAnyException();
            // checking for nullness is not the responsibility of the validator, but it must be null safe
            softly.assertThatCode(() -> RegexValidator.of("foo").assertValid(null)).doesNotThrowAnyException();
            softly.assertThatCode(() -> RegexValidator.of("foo").assertValid("bar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Value \"bar\" does not match regex foo");
            softly.assertThatCode(() -> RegexValidator.of("foo", "{0} is not {1}").assertValid("bar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bar is not foo");
        });
    }
}
