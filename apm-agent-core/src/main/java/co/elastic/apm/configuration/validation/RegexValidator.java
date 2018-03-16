package co.elastic.apm.configuration.validation;

import org.stagemonitor.configuration.ConfigurationOption;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.regex.Pattern;

public class RegexValidator implements ConfigurationOption.Validator<String> {

    private final Pattern pattern;
    private final String errorMessagePattern;

    private RegexValidator(String regex, String errorMessagePattern) {
        pattern = Pattern.compile(regex);
        this.errorMessagePattern = errorMessagePattern;
    }

    /**
     * Constructs a {@link RegexValidator} which validates a string based on a {@link Pattern}
     *
     * @param regex the regular expression which should be used to validate an input string
     * @return a {@link RegexValidator} which validates a string based on a {@link Pattern}
     */
    public static RegexValidator of(String regex) {
        return new RegexValidator(regex, "Value \"{0}\" does not match regex {1}");
    }

    /**
     * Constructs a {@link RegexValidator} which validates a string based on a {@link Pattern}
     *
     * @param regex               the regular expression which should be used to validate an input string
     * @param errorMessagePattern a error message format pattern. The placeholder <code>{0}</code> contains the actual value,
     *                            while the placeholder <code>{1}</code> represents the regex.
     * @return a {@link RegexValidator} which validates a string based on a {@link Pattern}
     */
    public static RegexValidator of(String regex, String errorMessagePattern) {
        return new RegexValidator(regex, errorMessagePattern);
    }

    @Override
    public void assertValid(@Nullable String value) {
        if (value != null && !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(MessageFormat.format(errorMessagePattern, value, pattern));
        }
    }
}
