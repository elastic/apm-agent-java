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

import org.stagemonitor.configuration.converter.AbstractValueConverter;
import org.stagemonitor.configuration.converter.DoubleValueConverter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public class RoundedDoubleConverter extends AbstractValueConverter<Double> {

    public static final int DEFAULT_PRECISION = 4;
    private static final RoundedDoubleConverter DEFAULT_INSTANCE = new RoundedDoubleConverter(DEFAULT_PRECISION);

    private final double precisionFactor;
    private final NumberFormat numberFormat;

    public static RoundedDoubleConverter withDefaultPrecision() {
        return DEFAULT_INSTANCE;
    }

    // package protected for testing
    RoundedDoubleConverter(int precisionDigits) {
        if (precisionDigits < 0) {
            throw new IllegalArgumentException("expects a zero-or-positive precision");
        }
        StringBuilder format = new StringBuilder("#.");
        for (int i = 0; i < precisionDigits; i++) {
            format.append("#");
        }
        this.numberFormat = new DecimalFormat(format.toString(), DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        this.precisionFactor = Math.pow(10, precisionDigits);
    }

    @Override
    public Double convert(String s) throws IllegalArgumentException {
        return round(DoubleValueConverter.INSTANCE.convert(s));
    }

    @Override
    public String toString(Double value) {
        return numberFormat.format(value);
    }

    public double round(double value) {
        double rounded = Math.round(value * precisionFactor) / precisionFactor;
        if (value > 0 && rounded == 0) {
            // avoid rounding to zero
            rounded = 1d / precisionFactor;
        }
        return rounded;
    }
}
