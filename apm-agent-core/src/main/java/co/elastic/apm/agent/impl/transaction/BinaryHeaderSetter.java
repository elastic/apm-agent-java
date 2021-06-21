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
package co.elastic.apm.agent.impl.transaction;

import javax.annotation.Nullable;

public interface BinaryHeaderSetter<C> extends HeaderSetter<byte[], C> {

    /**
     * Since the implementation itself knows the intrinsics of the headers and carrier lifecycle and handling, it should
     * be responsible for providing a byte array. This enables the implementation to cache byte arrays wherever required
     * and possible.
     * <p>
     * NOTE: if this method returns null, the tracer will allocate a buffer for each header.
     *
     * @param headerName the header name for which the byte array is required
     * @param length     the length of the required byte array
     * @return a byte array with the requested length, or null if header-value-buffer is not supported.
     */
    @Nullable
    byte[] getFixedLengthByteArray(String headerName, int length);
}
