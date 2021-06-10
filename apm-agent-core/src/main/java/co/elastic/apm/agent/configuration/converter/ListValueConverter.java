/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.configuration.converter;

import org.stagemonitor.configuration.converter.AbstractCollectionValueConverter;
import org.stagemonitor.configuration.converter.ValueConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListValueConverter<T> extends AbstractCollectionValueConverter<List<T>, T> {

    /**
     * Specialized delimiter supporting split by comma which is not enclosed in brackets.
     */
    public static final String COMMA_OUT_OF_BRACKETS = ",(?![^()]*\\))";

    protected final String delimiter;

    public ListValueConverter(ValueConverter<T> valueConverter) {
        super(valueConverter);
        this.delimiter = ",";
    }

    public ListValueConverter(ValueConverter<T> valueConverter, String delimiter) {
        super(valueConverter);
        this.delimiter = delimiter;
    }

    public List<T> convert(String s) {
        if (s != null && s.length() > 0) {
            final ArrayList<T> result = new ArrayList<>();
            for (String split : s.split(delimiter)) {
                result.add(valueConverter.convert(split.trim()));
            }
            return Collections.unmodifiableList(result);
        }
        return Collections.emptyList();
    }
}
