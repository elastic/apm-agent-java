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
package co.elastic.apm.agent.configuration.validation;

import org.stagemonitor.configuration.ConfigurationOption;

import javax.annotation.Nullable;

public class RangeValidator<T extends Comparable> implements ConfigurationOption.Validator<T> {

    @Nullable
    private final T min;
    @Nullable
    private final T max;
    private final boolean mustBeInRange;

    private RangeValidator(@Nullable T min, @Nullable T max, boolean mustBeInRange) {
        this.min = min;
        this.max = max;
        this.mustBeInRange = mustBeInRange;
    }

    public static <T extends Comparable> RangeValidator<T> isInRange(T min, T max) {
        return new RangeValidator<>(min, max, true);
    }

    public static <T extends Comparable> RangeValidator<T> isNotInRange(T min, T max) {
        return new RangeValidator<>(min, max, false);
    }

    public static <T extends Comparable> RangeValidator<T> min(T min) {
        return new RangeValidator<>(min, null, true);
    }

    public static <T extends Comparable> RangeValidator<T> max(T max) {
        return new RangeValidator<>(null, max, true);
    }

    @Override
    public void assertValid(@Nullable T value) {
        if (value != null) {
            boolean isInRange = true;
            if (min != null) {
                isInRange = min.compareTo(value) <= 0;
            }
            if (max != null) {
                isInRange &= value.compareTo(max) <= 0;
            }

            if (!isInRange && mustBeInRange) {
                throw new IllegalArgumentException(value + " must be in the range [" + min + "," + max + "]");
            }

            if (isInRange && !mustBeInRange) {
                throw new IllegalArgumentException(value + " must not be in the range [" + min + "," + max + "]");
            }
        }
    }

    @Nullable
    public T getMax() {
        return max;
    }

    @Nullable
    public T getMin() {
        return min;
    }

    public boolean isNegativeMatch() {
        return !mustBeInRange;
    }
}
