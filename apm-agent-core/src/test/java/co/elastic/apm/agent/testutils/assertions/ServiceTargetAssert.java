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
package co.elastic.apm.agent.testutils.assertions;

import co.elastic.apm.agent.impl.context.ServiceTarget;
import org.assertj.core.api.AbstractAssert;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceTargetAssert extends AbstractAssert<ServiceTargetAssert, ServiceTarget> {

    ServiceTargetAssert(ServiceTarget actual) {
        super(actual, ServiceTargetAssert.class);
    }

    private static String normalizeToString(CharSequence cs){
        return cs == null ? null: cs.toString();
    }

    private void checkString(String msg, String expected, @Nullable String actual) {
        if (!expected.equals(actual)) {
            failWithMessage(msg, expected, actual);
        }
    }

    private void checkNull(String msg, @Nullable Object actual){
        if(actual != null){
            failWithMessage(msg, actual);
        }
    }

    private void checkTrue(String msg, boolean expectedTrue){
        if(!expectedTrue){
            failWithMessage(msg);
        }
    }

    public ServiceTargetAssert hasType(String type) {
        isNotNull();
        checkString("Expected service target with type %s but was %s",type, actual.getType());
        return this;
    }

    public ServiceTargetAssert hasName(String name) {
        isNotNull();
        checkString("Expected service target with name '%s' but was '%s'", name, normalizeToString(actual.getName()));
        return this;
    }

    public ServiceTargetAssert hasNoName() {
        isNotNull();
        checkNull("Expected service target without name but was %s", actual.getName());
        return this;
    }

    public ServiceTargetAssert hasDestinationResource(String expected) {
        isNotNull();
        checkString("Expected service target with destination resource %s but was %s", expected, normalizeToString(actual.getDestinationResource()));
        return this;
    }

    public ServiceTargetAssert hasNotDestinationResourceSetByUser() {
        checkTrue("Expected service target without destination resource set by user", !actual.isDestinationResourceSetByUser());
        return this;
    }


    public ServiceTargetAssert isEmpty() {
        isNotNull();
        checkNull("Expected service target without type but was %s", actual.getType());
        hasNoName();
        checkNull("Expected service target without destination resource was %s", actual.getDestinationResource());
        checkTrue("Expected service target without content", !actual.hasContent());
        hasNotDestinationResourceSetByUser();
        return this;
    }

}
