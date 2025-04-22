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
package co.elastic.apm.agent.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.provider.Arguments;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JdkVersionTest {

    private static List<Arguments> releaseSchedule() {
        // from https://www.oracle.com/java/technologies/java-se-support-roadmap.html
        return List.of(
            Arguments.of(24, LocalDate.parse("2025-04-01")),
            Arguments.of(25, LocalDate.parse("2025-10-01")),
            Arguments.of(26, LocalDate.parse("2026-04-01"))
        );
    }

    @Test
    @EnabledIfSystemProperty(named = "elastic.jdkCompatibilityTest", matches = "true")
    void jdkReleaseScheduleReminder() {
        // not using @MethodSource and directly the arguments stream to ensure at least one is in the future
        jdkReleaseScheduleReminder(releaseSchedule(), LocalDate.now());
    }

    void jdkReleaseScheduleReminder(List<Arguments> args, LocalDate now) {

        assertThat(args).isNotEmpty();

        args.forEach(a -> jdkReleaseScheduleReminder((int) a.get()[0], (LocalDate) a.get()[1], now));
    }

    void jdkReleaseScheduleReminder(int version, LocalDate gaDate, LocalDate now) {
        if (now.isBefore(gaDate)) {
            return;
        }

        assertThat(false)
            .describedAs(
                "This test fails to remind you that JDK %d is about or already released,\n\n" +
                    "please update the following:\n" +
                    "\n" +
                    "- .github/workflows/main.yml in 'jdk-compatibility-tests' : replace early-access with released GA version and add the new early-access version\n" +
                    "- in this test: remove released version in the release schedule\n" +
                    "- in this test: update release schedule if needed\n"
                , version)
            .isTrue();

    }

    @Test
    void selfTest() {
        // verify that the test will fail in the future when we need to get reminded to update

        LocalDate now = LocalDate.now();

        assertThatThrownBy(() -> jdkReleaseScheduleReminder(List.of(), now))
            .isInstanceOf(AssertionError.class);

        assertThatThrownBy(() ->
            jdkReleaseScheduleReminder(List.of(Arguments.of(42, now)), now))
            .isInstanceOf(AssertionError.class);

        // should not trigger until the release day
        jdkReleaseScheduleReminder(List.of(Arguments.of(42, now.plusDays(1))), now);

    }
}
