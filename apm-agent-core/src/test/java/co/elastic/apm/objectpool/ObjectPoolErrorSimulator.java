package co.elastic.apm.objectpool;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Stream;

public class ObjectPoolErrorSimulator {

    public static final String START_TRANSACTION = "startTransaction";
    public static final String RECYCLING = "recycling";
    public static Queue<Integer> objectPool = new ArrayBlockingQueue<>(512);
    public static Set<Integer> allTransactions = new HashSet<>();

    public static void main(String[] args) throws Exception {
        try (Stream<String> lineStream = Files.lines(Paths.get(args[0]))) {
            lineStream
                .forEach(log -> {
                    if (log.contains(START_TRANSACTION)) {
                        simulateStartTransaction(log, Integer.parseInt(log, log.lastIndexOf('(') + 1, log.lastIndexOf(')'), 10));
                    } else if (log.contains(RECYCLING)) {
                        simulateRecycling(log, Integer.parseInt(log, log.indexOf(RECYCLING) + RECYCLING.length() + 1, log.length(), 10));
                    } else if (log.contains("This transaction has already been started")) {
                        System.out.println(log);
                    }
                });
        }
        System.out.println("objectPool.size(): " + objectPool.size());
        System.out.println("allTransactions.size(): " + allTransactions.size());
        System.out.println("objectPool = " + objectPool);
        System.out.println("allTransactions = " + allTransactions);
    }

    private static void simulateStartTransaction(String log, int identityHash) {
        allTransactions.add(identityHash);
        final Integer expectedIdentityHash = objectPool.peek();
        if (expectedIdentityHash != null) {
            if (!expectedIdentityHash.equals(identityHash)) {
                System.out.println(String.format("Expected %d but got %d", expectedIdentityHash, identityHash));
                System.out.println(log);
            }
            if (!objectPool.remove(identityHash)) {
                System.out.println(String.format("Object pool does not contain %d", identityHash));
                System.out.println(log);
            }
        }
    }

    private static void simulateRecycling(String log, int identityHash) {
        if (isTransaction(identityHash)) {
            if (objectPool.contains(identityHash)) {
                System.out.println(String.format("Object pool already contains %d", identityHash));
                System.out.println(log);
            }
            objectPool.offer(identityHash);
        }
    }

    private static boolean isTransaction(int identityHash) {
        return allTransactions.contains(identityHash);
    }
}
