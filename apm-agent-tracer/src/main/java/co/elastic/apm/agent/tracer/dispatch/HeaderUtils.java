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
package co.elastic.apm.agent.tracer.dispatch;

import java.util.Set;

public class HeaderUtils {

    private HeaderUtils() {
    }

    public static <C> boolean containsAny(Set<String> headerNames, C carrier, TextHeaderGetter<C> headerGetter) {
        for (String headerName : headerNames) {
            if (headerGetter.getFirstHeader(headerName, carrier) != null) {
                return true;
            }
        }
        return false;
    }

    public static <S, D> void copy(Set<String> headerNames, S source, TextHeaderGetter<S> headerGetter, D destination, TextHeaderSetter<D> headerSetter) {
        for (String headerName : headerNames) {
            String headerValue = headerGetter.getFirstHeader(headerName, source);
            if (headerValue != null) {
                headerSetter.setHeader(headerName, headerValue, destination);
            }
        }
    }

    public static <C> void remove(Set<String> headerNames, C carrier, HeaderRemover<C> headerRemover) {
        for (String headerName : headerNames) {
            headerRemover.remove(headerName, carrier);
        }
    }
}
