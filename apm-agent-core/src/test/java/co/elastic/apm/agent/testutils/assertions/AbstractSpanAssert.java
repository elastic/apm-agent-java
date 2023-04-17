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

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContext;

import java.util.Objects;
import java.util.stream.Collectors;

public class AbstractSpanAssert<SELF extends AbstractSpanAssert<SELF, ACTUAL>, ACTUAL extends AbstractSpan<?>> extends BaseAssert<SELF, ACTUAL> {

    protected AbstractSpanAssert(ACTUAL actual, Class<SELF> selfType) {
        super(actual, selfType);
    }

    public SELF hasName(String name) {
        isNotNull();
        checkString("Expected span with name '%s' but was '%s'", name, normalizeToString(actual.getNameForSerialization()));
        return thiz();
    }

    public SELF hasNameContaining(String nameContains) {
        isNotNull();
        String actualName = normalizeToString(actual.getNameForSerialization());
        if (!actualName.contains(nameContains)) {
            failWithMessage("Expected name '%s' to contain '%s'", actualName, nameContains);
        }
        return thiz();
    }

    public SELF hasType(String type) {
        isNotNull();
        checkString("Expected span with type '%s' but was '%s'", type, actual.getType());
        return thiz();
    }

    public SELF isExit() {
        isNotNull();
        checkTrue("Expected exit span, but was non-exit span", actual.isExit());
        return thiz();
    }

    public SELF isNotExit() {
        isNotNull();
        checkTrue("Expected a non-exit span, but was an exit span", !actual.isExit());
        return thiz();
    }

    public SELF hasParent(AbstractSpan<?> expectedParent) {
        TraceContext parentCtx = expectedParent.getTraceContext();
        TraceContext actualCtx = actual.getTraceContext();

        checkObject("Expected span to have traceId '%s' but was '%s'", parentCtx.getTraceId(), actualCtx.getTraceId());
        checkObject("Expected span to have parent-Id '%s' but was '%s'", parentCtx.getId(), actualCtx.getParentId());

        return thiz();
    }

    public SELF hasSpanLinkCount(int expected) {
        isNotNull();
        checkInt("Expected span to have '%d' span-links but has '%s'", expected, actual.getSpanLinks().size());
        return thiz();
    }

    public SELF hasSpanLink(AbstractSpan<?> expectedLink) {
        if (!checkSpanLinksContain(expectedLink.getTraceContext())) {
            String links = actual.getSpanLinks().stream()
                .map(ctx -> String.format("%s-%s", ctx.getTraceId(), ctx.getParentId()))
                .collect(Collectors.joining(", ", "[", "]"));
            failWithMessage("Expected span-links to contain %s, but did not: %s", expectedLink, links);
        }
        return thiz();
    }

    private boolean checkSpanLinksContain(TraceContext expectedLink) {
        return actual.getSpanLinks().stream()
            .anyMatch(ctx -> Objects.equals(ctx.getParentId(), expectedLink.getId())
                && Objects.equals(ctx.getTraceId(), expectedLink.getTraceId())
            );
    }

    @SuppressWarnings("unchecked")
    private SELF thiz() {
        return (SELF) this;
    }


}
