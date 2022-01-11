/*
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
 */
package co.elastic.apm.agent.logback;

import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Utilities for applying configurations that are not identical in all supported versions
 */
class VersionUtils {

    @Nullable
    private static MethodHandle setMaxSizeStringMethod;
    @Nullable
    private static MethodHandle setMaxSizeFileSizeMethod;

    @Nullable
    private static MethodHandle isImmediateFlushMethod;
    @Nullable
    private static MethodHandle setImmediateFlushMethod;

    static {
        try {
            try {
                setMaxSizeFileSizeMethod = MethodHandles.lookup()
                    .findVirtual(SizeBasedTriggeringPolicy.class, "setMaxFileSize", MethodType.methodType(void.class, FileSize.class));
            } catch (NoSuchMethodException e) {
                setMaxSizeStringMethod = MethodHandles.lookup()
                    .findVirtual(SizeBasedTriggeringPolicy.class, "setMaxFileSize", MethodType.methodType(void.class, String.class));
            }
        } catch (Exception e) {
            // We cannot log here because this plugin escapes slf4j package reallocation. Instead, we will log when trying to get the max size
        }

        try {
            isImmediateFlushMethod = MethodHandles.lookup()
                .findVirtual(OutputStreamAppender.class, "isImmediateFlush", MethodType.methodType(boolean.class));
            setImmediateFlushMethod = MethodHandles.lookup()
                .findVirtual(OutputStreamAppender.class, "setImmediateFlush", MethodType.methodType(void.class, boolean.class));
        } catch (NoSuchMethodException e) {
            // expected
        } catch (Exception e) {
            // We cannot log here because this plugin escapes slf4j package reallocation.
        }
    }

    /**
     * Up to version 1.1.7 (inclusive), {@link SizeBasedTriggeringPolicy#setMaxFileSize} expects a {@code String} argument.
     * Starting at version 1.1.8, this method expects a {@link ch.qos.logback.core.util.FileSize} argument, which doesn't
     * have a public constructor in older versions.
     * @param policy policy to configure
     * @param maxFileSize max full size in bytes
     */
    static void setMaxFileSize(SizeBasedTriggeringPolicy<?> policy, long maxFileSize) throws Throwable {
        if (maxFileSize > 0) {
            String fileSizeStr = String.valueOf(maxFileSize);
            if (setMaxSizeFileSizeMethod != null) {
                FileSize fileSize = FileSize.valueOf(fileSizeStr);
                setMaxSizeFileSizeMethod.invoke(policy, fileSize);
            } else if (setMaxSizeStringMethod != null) {
                setMaxSizeStringMethod.invoke(policy, fileSizeStr);
            }
        } else {
            throw new IllegalStateException("Invalid max size of file bytes - " + maxFileSize);
        }
    }

    /**
     * Supported only from version 1.2.0.
     * In older versions, this feature was controlled through the encoders. Since we are using
     * {@link co.elastic.logging.logback.EcsEncoder}, the shaded logs will always be written as if {@code immediateFlush}
     * is set to {@code true} in versions <1.2.0
     * @param originalAppender the original appender
     * @param ecsAppender the custom ECS appender
     */
    static void copyImmediateFlushSetting(OutputStreamAppender<?> originalAppender, OutputStreamAppender<?> ecsAppender) throws Throwable {

        if (setImmediateFlushMethod != null && isImmediateFlushMethod != null) {
            boolean immediateFlushSet = (boolean) isImmediateFlushMethod.invoke(originalAppender);
            setImmediateFlushMethod.invoke(ecsAppender, immediateFlushSet);
        }
    }
}
