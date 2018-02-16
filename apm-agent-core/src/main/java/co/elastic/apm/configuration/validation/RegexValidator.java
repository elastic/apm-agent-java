package co.elastic.apm.configuration.validation;

import org.stagemonitor.configuration.ConfigurationOption;

import java.util.regex.Pattern;

public class RegexValidator implements ConfigurationOption.Validator<String> {

    private final Pattern pattern;
    private final String errorMessage;

    public static RegexValidator of(String regex) {
        return new RegexValidator(regex, null);
    }
    public static RegexValidator of(String regex, String errorMessage) {
        return new RegexValidator(regex, errorMessage);
    }

    private RegexValidator(String regex, String errorMessage) {
        pattern = Pattern.compile(regex);
        this.errorMessage = errorMessage;
    }

    @Override
    public void assertValid(String value) {
        if (!pattern.matcher(value).matches()) {
            final String message;
            if (errorMessage == null) {
                message = "Value '%s' does not match regex %s";
            } else {
                message = errorMessage;
            }
            throw new IllegalArgumentException(String.format(message, value, pattern));
        }
    }
}
