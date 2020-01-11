/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.util;

/**
 * Based on <a href="https://gist.github.com/brianguertin/ada4b65c6d1c4f6d3eee3c12b6ce021b">https://gist.github.com/brianguertin</a>.
 * This code was released into the public domain by Brian Guertin on July 8, 2016 citing, verbatim the unlicense.
 */
public class Version implements Comparable<Version> {
    private final int[] numbers;

    public static Version of(String version) {
        return new Version(version);
    }

    private Version(String version) {
        final String[] parts = version.split("\\-")[0].split("\\.");
        numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = Integer.valueOf(parts[i]);
        }
    }

    @Override
    public int compareTo(Version another) {
        final int maxLength = Math.max(numbers.length, another.numbers.length);
        for (int i = 0; i < maxLength; i++) {
            final int left = i < numbers.length ? numbers[i] : 0;
            final int right = i < another.numbers.length ? another.numbers[i] : 0;
            if (left != right) {
                return left < right ? -1 : 1;
            }
        }
        return 0;
    }
}
