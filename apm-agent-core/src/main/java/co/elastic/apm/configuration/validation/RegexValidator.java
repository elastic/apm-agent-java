/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
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
     * @param errorMessagePattern a error message format pattern. The placeholder {@code {0}} contains the actual value,
     *                            while the placeholder {@code {1}} represents the regex.
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
