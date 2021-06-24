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
package co.elastic.apm.agent.sdk.weakmap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class NullCheck {

    private static final Logger logger = LoggerFactory.getLogger(NullCheck.class);

    /**
     * checks if key or value is {@literal null}
     *
     * @param v key or value
     * @return {@literal true} if key is non-null, {@literal false} if null
     */
    private static <T> boolean isNull(@Nullable T v, boolean isKey) {
        if (null != v) {
            return false;
        }
        String msg = String.format("trying to use null %s", isKey ? "key" : "value");
        if (logger.isDebugEnabled()) {
            logger.debug(msg, new RuntimeException(msg));
        } else {
            logger.warn(msg);
        }
        return true;
    }

    public static <T> boolean isNullKey(@Nullable T key) {
        return isNull(key, true);
    }

    public static <T> boolean isNullValue(@Nullable T value) {
        return isNull(value, false);
    }
}
