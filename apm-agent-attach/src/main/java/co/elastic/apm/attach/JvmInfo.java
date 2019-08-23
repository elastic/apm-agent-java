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
package co.elastic.apm.attach;

import java.util.Objects;

class JvmInfo {
    final String pid;
    final String packageOrPath;

    JvmInfo(String pid, String packageOrPath) {
        this.pid = pid;
        this.packageOrPath = packageOrPath;
    }

    static JvmInfo parse(String jpsLine) {
        final int firstSpace = jpsLine.indexOf(' ');
        return new JvmInfo(jpsLine.substring(0, firstSpace), jpsLine.substring(firstSpace + 1));
    }

    @Override
    public String toString() {
        return pid + ' ' + packageOrPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JvmInfo jvmInfo = (JvmInfo) o;
        return pid.equals(jvmInfo.pid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid);
    }
}
