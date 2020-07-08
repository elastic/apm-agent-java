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
package co.elastic.apm.servlet;

import org.testcontainers.containers.output.OutputFrame;

import java.util.function.Consumer;
import java.util.regex.Pattern;

public class StandardOutLogConsumer implements Consumer<OutputFrame> {
    private static final Pattern ANSI_CODE_PATTERN = Pattern.compile("\\[\\d[ABCD]");
    private String prefix = "";

    public StandardOutLogConsumer() {
    }

    public StandardOutLogConsumer withPrefix(String prefix) {
        this.prefix = "[" + prefix + "] ";
        return this;
    }

    @Override
    public void accept(OutputFrame outputFrame) {
        if (outputFrame != null) {
            String utf8String = outputFrame.getUtf8String();

            if (utf8String != null) {
                OutputFrame.OutputType outputType = outputFrame.getType();
                String message = utf8String.trim();

                if (ANSI_CODE_PATTERN.matcher(message).matches()) {
                    return;
                }

                switch (outputType) {
                    case END:
                        break;
                    case STDOUT:
                    case STDERR:
                        System.out.println(String.format("%s%s", prefix, message));
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected outputType " + outputType);
                }
            }
        }
    }
}
