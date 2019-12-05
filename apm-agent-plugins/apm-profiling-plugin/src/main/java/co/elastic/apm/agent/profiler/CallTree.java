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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.matcher.WildcardMatcher;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class CallTree {

    private static final StackTraceElement ROOT = new StackTraceElement(CallTree.class.getName(), "root", null, -1);
    protected int count;
    private List<CallTree> children = new ArrayList<>();
    private StackTraceElement frame;
    private int start;
    private int end;
    @Nullable
    private TraceContext traceContext;

    public CallTree(StackTraceElement frame, int start, @Nullable TraceContext traceContext) {
        this.frame = frame;
        this.start = start;
        this.traceContext = traceContext;
    }

    public static CallTree.Root createRoot(TraceContext traceContext, long msPerTick, List<WildcardMatcher> excludedClasses) {
        return new CallTree.Root(ROOT, 1, msPerTick, excludedClasses, traceContext);
    }

    protected void addFrame(ListIterator<StackTraceElement> iterator, int tick, List<WildcardMatcher> excludedClasses, @Nullable TraceContext traceContext) {
        count++;

        CallTree lastChild = getLastChild();
        // if the StackTraceElement corresponding to the last child is not in the stack trace
        // it's assumed to have ended one tick ago
        boolean endChild = true;
        // skipping frames corresponding to excludedClasses
        while (iterator.hasPrevious()) {
            final StackTraceElement frame = iterator.previous();
            if (WildcardMatcher.isNoneMatch(excludedClasses, frame.getClassName())) {
                if (lastChild != null) {
                    if (!lastChild.isEnded() && lastChild.frame.equals(frame)) {
                        lastChild.addFrame(iterator, tick, excludedClasses, traceContext);
                        endChild = false;
                    } else {
                        // we're looking at a frame which is a new sibling to listChild
                        lastChild.end(tick - 1);
                        addChild(frame, iterator, tick, excludedClasses, traceContext);
                    }
                } else {
                    addChild(frame, iterator, tick, excludedClasses, traceContext);
                }
                break;
            }
        }
        if (lastChild != null && !lastChild.isEnded() && endChild) {
            lastChild.end(tick - 1);
        }
    }

    void addChild(StackTraceElement frame, ListIterator<StackTraceElement> iterator, int tick, List<WildcardMatcher> frameMatcher, @Nullable TraceContext traceContext) {
        CallTree callTree = new CallTree(frame, tick, traceContext);
        children.add(callTree);
        callTree.addFrame(iterator, tick, frameMatcher, null);
    }

    public int getDurationTicks() {
        return end - start;
    }

    long getDurationUs(long usPerTick) {
        return getDurationTicks() * usPerTick;
    }

    public int getCount() {
        return count;
    }

    public StackTraceElement getFrame() {
        return frame;
    }

    public List<CallTree> getChildren() {
        return children;
    }

    void end(int tick) {
        if (end != 0) {
            return;
        }
        end = tick;
        CallTree lastChild = getLastChild();
        if (lastChild != null && !lastChild.isEnded()) {
            lastChild.end(tick);
        }
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    private boolean isPillar() {
        return children.size() == 1 && children.get(0).count == count;
    }

    @Nullable
    public CallTree getLastChild() {
        return children.size() > 0 ? children.get(children.size() - 1) : null;
    }

    public boolean isEnded() {
        return end != 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            toString(sb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    public void toString(Appendable out) throws IOException {
        toString(out, 0);
    }

    private void toString(Appendable out, int level) throws IOException {
        for (int i = 0; i < level; i++) {
            out.append("  ");
        }
        out.append(frame.getClassName()).append('.').append(frame.getMethodName())
            .append(' ').append(Integer.toString(count))
            .append('\n');
        for (CallTree node : children) {
            node.toString(out, level + 1);
        }
    }

    void spanify(TraceContext parent, long baseTimestamp, long usPerTick) {
        if (traceContext != null) {
            parent = traceContext;
        }
        Span span = null;
        if (!isPillar() || isLeaf()) {
            span = asSpan(parent, baseTimestamp, usPerTick);
        }
        for (CallTree child : getChildren()) {
            child.spanify(span != null ? span.getTraceContext() : parent, baseTimestamp, usPerTick);
        }
        if (span != null) {
            span.end(span.getTimestamp() + getDurationUs(usPerTick));
        }
    }

    protected Span asSpan(TraceContext parent, long baseTimestamp, long usPerTick) {
        return parent.createSpan(baseTimestamp + ((start - 1) * usPerTick))
            .withType("app")
            .withSubtype("inferred")
            .appendToName(frame.getClassName())
            .appendToName("#")
            .appendToName(frame.getMethodName());
    }

    public void removeNodesFasterThan(float percent, int minTicks) {
        int ticks = (int) (getDurationTicks() * percent);
        removeNodesFasterThan(Math.max(ticks, minTicks));
    }

    public void removeNodesFasterThan(int ticks) {
        for (Iterator<CallTree> iterator = getChildren().iterator(); iterator.hasNext(); ) {
            CallTree child = iterator.next();
            if (child.getDurationTicks() < ticks) {
                iterator.remove();
            } else {
                child.removeNodesFasterThan(ticks);
            }
        }
    }

    public static class Root extends CallTree {
        private long timestamp;
        private long msPerTick;
        private final List<WildcardMatcher> excludedClasses;
        protected TraceContext traceContext;
        private TraceContext activeSpan;

        public Root(StackTraceElement frame, int start, long msPerTick, List<WildcardMatcher> excludedClasses, TraceContext traceContext) {
            super(frame, start, traceContext);
            this.msPerTick = msPerTick;
            this.excludedClasses = excludedClasses;
            this.traceContext = traceContext;
            activeSpan = traceContext;
        }

        public void setActiveSpan(TraceContext context) {
            this.activeSpan = context;
        }

        public void addStackTrace(List<StackTraceElement> stackTrace) {
            if (count == 0) {
                timestamp = this.traceContext.getClock().getEpochMicros();
            }
            addFrame(stackTrace.listIterator(stackTrace.size()), count + 1, excludedClasses, activeSpan);
        }

        public void spanify() {
            for (CallTree child : getChildren()) {
                child.spanify(traceContext, timestamp, msPerTick * 1000);
            }
        }

        public TraceContext getTraceContext() {
            return traceContext;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void end() {
            end(count);
        }
    }
}
