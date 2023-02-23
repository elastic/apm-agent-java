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
package co.elastic.apm.agent.configuration.converter;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class WildcardMatcher extends co.elastic.apm.agent.common.util.WildcardMatcher implements co.elastic.apm.agent.sdk.configuration.WildcardMatcher {

    private static final WildcardMatcher MATCH_ALL = new WildcardMatcher(co.elastic.apm.agent.common.util.WildcardMatcher.matchAll());
    private static final List<WildcardMatcher> MATCH_ALL_LIST = Collections.singletonList(MATCH_ALL);

    private final co.elastic.apm.agent.common.util.WildcardMatcher matcher;

    public WildcardMatcher(co.elastic.apm.agent.common.util.WildcardMatcher matcher) {
        this.matcher = matcher;
    }

    public static WildcardMatcher valueOf(String wildcardString) {
        return new WildcardMatcher(co.elastic.apm.agent.common.util.WildcardMatcher.valueOf(wildcardString));
    }

    public static WildcardMatcher caseSensitiveMatcher(String matcher) {
        return new WildcardMatcher(co.elastic.apm.agent.common.util.WildcardMatcher.caseSensitiveMatcher(matcher));
    }

    public static WildcardMatcher matchAll() {
        return MATCH_ALL;
    }

    public static List<WildcardMatcher> matchAllList() {
        return MATCH_ALL_LIST;
    }

    @Override
    public boolean matches(CharSequence s) {
        return matcher.matches(s);
    }

    @Override
    public boolean matches(CharSequence firstPart, @Nullable CharSequence secondPart) {
        return matcher.matches(firstPart, secondPart);
    }

    @Override
    public String getMatcher() {
        return matcher.getMatcher();
    }
}
