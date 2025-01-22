import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PartitionProjects {


    /**
     * Utitlity to be invoked with the output of ./mvnw validate to partition the non-pom projects in equally sized groups
     * The projects are shuffeled detmerinistically, because ususally testing projects are directly after each other in the
     * default order.
     * The first parameter is the number of partitions to generate, the second is the id (starting with zero) of the partition to output.
     * The third parameter is the output of "./mvnw validate", it will be scanned for the "Reactor build Order" to identify the projects.
     */
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java PartitionProjects <partition-count> <partition-id> <mvnw validate output>");
            throw new IllegalArgumentException("Invalid argument count: " + args.length);
        }

        int partitionCount = Integer.parseInt(args[0]);
        int partitionId = Integer.parseInt(args[1]);
        String mvnValidateOutput = args[2];

        Pattern projectPattern = Pattern.compile("\\[INFO] +(?<project>\\S+:\\S+) *\\[(?<type>\\S+)]");

        Set<String> nonPomProjects = new HashSet<>();
        Matcher matcher = projectPattern.matcher(mvnValidateOutput);
        while (matcher.find()) {
            String project = matcher.group("project");
            String type = matcher.group("type");
            if (!"pom".equals(type)) {
                nonPomProjects.add(project);
            }
        }

        if (nonPomProjects.size() < partitionCount) {
            throw new IllegalStateException("Found " + nonPomProjects.size() + " projects and provided partition count " + partitionCount + " is bigger than that");
        }

        ArrayList<String> projectsSorted = new ArrayList<>(nonPomProjects);
        projectsSorted.sort(Comparator.naturalOrder()); //sort first in case maven reactor has a indeterminisitc order
        Collections.shuffle(projectsSorted, new Random(7)); // deterministically shuffle to separate workload equally

        int count = projectsSorted.size();
        int partitionStartInclusive = partitionId * count / partitionCount;
        int partitionEndExclusive = (partitionId + 1) * count / partitionCount;

        String partition = projectsSorted
            .subList(partitionStartInclusive, partitionEndExclusive)
            .stream()
            .sorted()
            .collect(Collectors.joining(","));
        System.out.println(partition);
    }
}
