/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.agent.configuration.converter;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.converter.AbstractValueConverter;

public class ByteValueConverter extends AbstractValueConverter<ByteValue> {

    public static final ByteValueConverter INSTANCE = new ByteValueConverter();

    public static ConfigurationOption.ConfigurationOptionBuilder<ByteValue> byteOption() {
        return ConfigurationOption.builder(INSTANCE, ByteValue.class);
    }

    private ByteValueConverter() {
    }

    @Override
    public ByteValue convert(String s) throws IllegalArgumentException {
        return ByteValue.of(s);
    }

    @Override
    public String toString(ByteValue value) {
        return value.toString();
    }
}
