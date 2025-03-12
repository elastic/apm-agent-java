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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReleaseChangelog {

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Expected exactly three arguments: <ChangelogFile> <ReleaseNotesPath> <VersionToRelease>");
            System.exit(-1);
        }
        Path nextChangelogFile = Paths.get(args[0]);
        Path releaseNotesDir = Paths.get(args[1]);
        Path releaseNotesFile = releaseNotesDir.resolve("index.md");
        Path deprecationsFile = releaseNotesDir.resolve("deprecations.md");
        VersionNumber version = VersionNumber.parse(args[2].trim());

        Lines nextChangelogLines = new Lines(Files.readAllLines(nextChangelogFile, StandardCharsets.UTF_8));
        Lines fixes = nextChangelogLines.cutLinesBetween("<!--FIXES-START-->", "<!--FIXES-END-->");
        Lines enhancements = nextChangelogLines.cutLinesBetween("<!--ENHANCEMENTS-START-->", "<!--ENHANCEMENTS-END-->");
        Lines deprecations = nextChangelogLines.cutLinesBetween("<!--DEPRECATIONS-START-->", "<!--DEPRECATIONS-END-->");


        var formatter = DateTimeFormatter.ofPattern("LLLL d, yyyy", Locale.ENGLISH);
        String releaseDateLine = "**Release date:** " + formatter.format(LocalDate.now());

        Lines allReleaseNotes = new Lines(Files.readAllLines(releaseNotesFile, StandardCharsets.UTF_8));
        int insertBeforeLine = findHeadingOfPreviousVersion(allReleaseNotes, version);
        allReleaseNotes.insert(generateReleaseNotes(version, releaseDateLine, enhancements, fixes), insertBeforeLine);

        if (!deprecations.isEmpty()) {
            Lines allDeprecations = new Lines(Files.readAllLines(deprecationsFile, StandardCharsets.UTF_8));
            int insertDepsBeforeLine = findHeadingOfPreviousVersion(allDeprecations, version);
            allDeprecations.insert(generateDeprecations(version, releaseDateLine, deprecations), insertDepsBeforeLine);
            Files.writeString(deprecationsFile, allDeprecations + "\n", StandardCharsets.UTF_8);
        }
        Files.writeString(releaseNotesFile, allReleaseNotes + "\n", StandardCharsets.UTF_8);
        Files.writeString(nextChangelogFile, nextChangelogLines + "\n", StandardCharsets.UTF_8);
    }

    private static Lines generateReleaseNotes(VersionNumber version, String releaseDateLine, Lines enhancements, Lines fixes) {
        Lines result = new Lines()
            .append("## " + version.dotStr() + " [elastic-apm-java-agent-" + version.dashStr() + "-release-notes]")
            .append(releaseDateLine);
        if (!enhancements.isEmpty()) {
            result
                .append("")
                .append("### Features and enhancements [elastic-apm-java-agent-" + version.dashStr() + "-features-enhancements]")
                .append(enhancements);
        }
        if (!fixes.isEmpty()) {
            result
                .append("")
                .append("### Fixes [elastic-apm-java-agent-" + version.dashStr() + "-fixes]")
                .append(fixes);
        }
        result.append("");
        return result;
    }


    private static Lines generateDeprecations(VersionNumber version, String releaseDateLine, Lines deprecations) {
        return new Lines()
            .append("## " + version.dotStr() + " [elastic-apm-java-agent-" + version.dashStr() + "-deprecations]")
            .append(releaseDateLine)
            .append("")
            .append(deprecations)
            .append("");
    }

    static int findHeadingOfPreviousVersion(Lines lines, VersionNumber version) {
        Pattern headingPattern = Pattern.compile("## (\\d+\\.\\d+\\.\\d+) .*");
        Comparator<VersionNumber> comp = VersionNumber.comparator();
        int currentBestLineNo = -1;
        VersionNumber currentBestVersion = null;
        for (int i = 0; i < lines.lineCount(); i++) {
            Matcher matcher = headingPattern.matcher(lines.getLine(i));
            if (matcher.matches()) {
                VersionNumber headingForVersion = VersionNumber.parse(matcher.group(1));
                if (comp.compare(headingForVersion, version) < 0
                    && (currentBestVersion == null || comp.compare(headingForVersion, currentBestVersion) > 0)) {
                    currentBestLineNo = i;
                    currentBestVersion = headingForVersion;
                }
            }
        }
        return currentBestLineNo;
    }

    record VersionNumber(int major, int minor, int patch) {
        public static VersionNumber parse(String versionString) {
            if (!versionString.matches("\\d+\\.\\d+\\.\\d+")) {
                throw new IllegalArgumentException("Version must be in the format x.x.x but was not: " + versionString);
            }
            String[] parts = versionString.split("\\.");
            return new VersionNumber(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }

        static Comparator<VersionNumber> comparator() {
            return Comparator
                .comparing(VersionNumber::major)
                .thenComparing(VersionNumber::minor)
                .thenComparing(VersionNumber::patch);
        }

        String dashSt() {
            return major + "-" + minor + "-" + patch;
        }

        String dotStr() {
            return major + "." + minor + "." + patch;
        }

    }

    static class Lines {

        private final List<String> lines;

        public Lines() {
            this.lines = new ArrayList<>();
        }

        public Lines(List<String> lines) {
            this.lines = new ArrayList<>(lines);
        }

        int lineCount() {
            return lines.size();
        }

        boolean isEmpty() {
            return lines.isEmpty();
        }

        Lines cutLinesBetween(String startLine, String endLine) {
            int start = findLine(l -> l.trim().equals(startLine), 0)
                .orElseThrow(() -> new IllegalStateException("Expected line '" + startLine + "' to exist"));
            int end = findLine(l -> l.trim().equals(endLine), start + 1)
                .orElseThrow(() -> new IllegalStateException("Expected line '" + endLine + "' to exist after '" + startLine + "'"));
            Lines result = cut(start + 1, end).trim();

            lines.add(start + 1, "");

            return result;
        }

        OptionalInt findLine(Predicate<String> condition, int startAt) {
            for (int i = startAt; i < lines.size(); i++) {
                if (condition.test(lines.get(i))) {
                    return OptionalInt.of(i);
                }
            }
            return OptionalInt.empty();
        }

        Lines cut(int startInclusive, int endExclusive) {
            List<String> cutLines = new ArrayList<>();
            for (int i = startInclusive; i < endExclusive; i++) {
                cutLines.add(lines.remove(startInclusive));
            }
            return new Lines(cutLines);
        }

        void insert(Lines other, int insertAt) {
            this.lines.addAll(insertAt, other.lines);
        }

        Lines append(String line) {
            lines.add(line);
            return this;
        }

        Lines append(Lines toAppend) {
            lines.addAll(toAppend.lines);
            return this;
        }

        /**
         * Trims lines consisting of only blanks at the top and bottom
         */
        Lines trim() {
            while (!lines.isEmpty() && lines.get(0).matches("\\s*")) {
                lines.remove(0);
            }
            while (!lines.isEmpty() && lines.get(lines.size() - 1).matches("\\s*")) {
                lines.remove(lines.size() - 1);
            }
            return this;
        }

        @Override
        public String toString() {
            return String.join("\n", lines);
        }

        public String getLine(int number) {
            return lines.get(number);
        }
    }

}
