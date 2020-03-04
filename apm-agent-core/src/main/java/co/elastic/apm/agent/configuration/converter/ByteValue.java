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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ByteValue {

    public static final Pattern BYTE_PATTERN = Pattern.compile("^(\\d+)(b|kb|mb|gb)$");

    private final long bytes;
    private final String byteString;

    public static ByteValue of(String byteString) {
        byteString = byteString.toLowerCase();
        Matcher matcher = BYTE_PATTERN.matcher(byteString);
        if (matcher.matches()) {
            long value = Long.parseLong(matcher.group(1));
            return new ByteValue(byteString, value * getUnitMultiplier(matcher.group(2)));
        } else {
            throw new IllegalArgumentException("Invalid byte value '" + byteString + "'");
        }
    }

    private static int getUnitMultiplier(String unit) {
        switch (unit) {
            case "b":
                return 1;
            case "kb":
                return 1024;
            case "mb":
                return 1024 * 1024;
            case "gb":
                return 1024 * 1024 * 1024;
            default:
                throw new IllegalStateException("Byte unit '" + unit + "' is unknown");
        }
    }

    private ByteValue(String byteString, long bytes) {
        this.byteString = byteString;
        this.bytes = bytes;
    }

    public long getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return byteString;
    }
}
