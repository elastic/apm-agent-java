/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.converter.AbstractValueConverter;
import org.stagemonitor.configuration.converter.DoubleValueConverter;

public class RoundedDoubleConverter extends AbstractValueConverter<Double> {

    private final int precisionDigits;

    public static ConfigurationOption.ConfigurationOptionBuilder<Double> withPrecision(int precisionDigits) {
        return ConfigurationOption.builder(new RoundedDoubleConverter(precisionDigits), Double.class);
    }

    // package protected for testing
    RoundedDoubleConverter(int precisionDigits) {
        if (precisionDigits < 0) {
            throw new IllegalArgumentException("expects a zero-or-positive precision");
        }
        this.precisionDigits = precisionDigits;
    }

    @Override
    public Double convert(String s) throws IllegalArgumentException {
        Double value = DoubleValueConverter.INSTANCE.convert(s);
        double f = Math.pow(10, precisionDigits);
        value = Math.round(value * f) / f;
        return value;
    }

    @Override
    public String toString(Double value) {
        return DoubleValueConverter.INSTANCE.toString(value);
    }
}
