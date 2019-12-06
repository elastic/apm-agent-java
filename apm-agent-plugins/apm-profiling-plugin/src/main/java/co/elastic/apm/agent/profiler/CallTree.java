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

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class CallTree {

    protected int count;
    private List<CallTree> children = new ArrayList<>();
    private String frame;
    private int start;
    private int end;
    @Nullable
    private TraceContext traceContext;

    public CallTree(String frame, int start, @Nullable TraceContext traceContext) {
        this.frame = frame;
        this.start = start;
        this.traceContext = traceContext;
    }

    public static CallTree.Root createRoot(TraceContext traceContext, long msPerTick) {
        return new CallTree.Root( 1, msPerTick, traceContext);
    }

    protected void addFrame(ListIterator<String> iterator, int tick, int samples, @Nullable TraceContext traceContext) {
        count += samples;

        CallTree lastChild = getLastChild();
        // if the frame corresponding to the last child is not in the stack trace
        // it's assumed to have ended one tick ago
        boolean endChild = true;
        if (iterator.hasPrevious()) {
            final String frame = iterator.previous();
            if (lastChild != null) {
                if (!lastChild.isEnded() && lastChild.frame.equals(frame)) {
                    lastChild.addFrame(iterator, tick, samples, traceContext);
                    endChild = false;
                } else {
                    // we're looking at a frame which is a new sibling to listChild
                    lastChild.end(tick - 1);
                    addChild(frame, iterator, tick, samples, traceContext);
                }
            } else {
                addChild(frame, iterator, tick, samples, traceContext);
            }
        }
        if (lastChild != null && !lastChild.isEnded() && endChild) {
            lastChild.end(tick - 1);
        }
    }

    void addChild(String frame, ListIterator<String> iterator, int tick, int samples, @Nullable TraceContext traceContext) {
        CallTree callTree = new CallTree(frame, tick, traceContext);
        children.add(callTree);
        callTree.addFrame(iterator, tick, samples, null);
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

    public String getFrame() {
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
        out.append(frame)
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
            .appendToName(frame);
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
        protected TraceContext traceContext;
        private TraceContext activeSpan;

        public Root(int start, long msPerTick, TraceContext traceContext) {
            super("root", start, traceContext);
            this.msPerTick = msPerTick;
            this.traceContext = traceContext;
            activeSpan = traceContext;
        }

        public void setActiveSpan(TraceContext context) {
            this.activeSpan = context;
        }

        public void addStackTrace(List<String> stackTrace) {
            addStackTrace(stackTrace, 1);
        }

        public void addStackTrace(List<String> stackTrace, int samples) {
            if (count == 0) {
                timestamp = this.traceContext.getClock().getEpochMicros();
            }
            addFrame(stackTrace.listIterator(stackTrace.size()), count + 1, samples, activeSpan);
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
