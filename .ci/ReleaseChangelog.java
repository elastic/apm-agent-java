import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Predicate;

public class ReleaseChangelog {

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Expected exactly two arguments: <ChangelogFile> <VersionToRelease>");
            System.exit(-1);
        }
        Path fileName = Paths.get(args[0]);
        String version = args[1].trim();
        if (!version.matches("\\d+\\.\\d+\\.\\d+")) {
            System.out.println("Version must be in the format x.x.x but was not: " + version);
            System.exit(-1);
        }
        Lines f = new Lines(Files.readAllLines(fileName, StandardCharsets.UTF_8));
        int unreleasedStart = f.findLine(str -> str.trim().equals("=== Unreleased"), 0).orElseThrow() + 1;
        int unreleasedEnd = f.findLine(str -> str.startsWith("[[release-notes-"), unreleasedStart).orElseThrow();

        Lines changes = f.cut(unreleasedStart, unreleasedEnd);
        f.insert(new Lines(List.of("")), unreleasedStart); //add a blank line below unreleased heading
        changes.trim();

        // a few sanity checks
        if (changes.lineCount() == 0) {
            System.out.println("Unreleased changes are empty, there must be something wrong!");
            System.exit(-1);
        }
        OptionalInt wrongIndentedHeading = changes.findLine(str -> str.matches("^==?=?=?[^=].*"), 0);
        if (wrongIndentedHeading.isPresent()) {
            System.out.println("Found heading which is too high level (must be at least =====) within changes: "
                + changes.getLine(wrongIndentedHeading.getAsInt()));
            System.exit(-1);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
            .withZone(ZoneOffset.UTC);

        changes.insert(new Lines(List.of(
            "",
            String.format("[[release-notes-%s]]", version),
            String.format("==== %s - %s", version, formatter.format(Instant.now())),
            ""
        )), 0);

        int majorVersion = Integer.parseInt(version.split("\\.")[0]);
        String majorHeading = String.format("=== Java Agent version %d.x", majorVersion);
        OptionalInt sectionStart = f.findLine(str -> str.trim().equals(majorHeading), 0);
        if (sectionStart.isEmpty()) {
            System.out.println("Could not find heading for major version: " + majorHeading);
            System.out.println("Is this a new major version? If yes, please add an empty section for it manually to the Changelog");
            System.exit(-1);
        }

        f.insert(changes, sectionStart.getAsInt() + 1);

        Files.writeString(fileName, f.toString(), StandardCharsets.UTF_8);
    }

    static class Lines {

        private final List<String> lines;

        public Lines(List<String> lines) {
            this.lines = new ArrayList<>(lines);
        }

        int lineCount() {
            return lines.size();
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

        /**
         * Trims lines consisting of only blanks at the top and bottom
         */
        void trim() {
            while (!lines.isEmpty() && lines.get(0).matches("\\s*")) {
                lines.remove(0);
            }
            while (!lines.isEmpty() && lines.get(lines.size() - 1).matches("\\s*")) {
                lines.remove(lines.size() - 1);
            }
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
