import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class Tests {
    private static int checks;

    private static void check(boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("Failed check " + checks);
        }
    }

    private static void edgeCases(IntSet set) {
        check(!set.contains(7));
        check(!set.delete(7));
        int[] keys = {8, 4, 12, 2, 6, 10, 14, 9, 11, 13,
                      Integer.MIN_VALUE, Integer.MAX_VALUE, 0, -1};
        for (int key : keys) {
            check(set.insert(key));
            check(!set.insert(key));
        }
        check(set.size() == keys.length);
        for (int key : keys) {
            check(set.contains(key));
            check(set.delete(key));
            check(!set.delete(key));
            check(!set.contains(key));
        }
        check(set.size() == 0);
        check(set.insert(5));
        check(set.delete(5));
    }

    private static void randomized(IntSet set) {
        Set<Integer> reference = new HashSet<Integer>();
        Random random = new Random(42);
        for (int i = 0; i < 30000; i++) {
            int key = random.nextInt(2000) - 1000;
            int operation = random.nextInt(3);
            if (operation == 0) {
                check(set.insert(key) == reference.add(key));
            } else if (operation == 1) {
                check(set.contains(key) == reference.contains(key));
            } else {
                check(set.delete(key) == reference.remove(key));
            }
            check(set.size() == reference.size());
        }
        for (int key = -1000; key < 1000; key++) {
            check(set.contains(key) == reference.contains(key));
        }
        for (int key : reference) {
            check(set.delete(key));
        }
        check(set.size() == 0);
    }

    private static void stress(IntSet set, int step) {
        for (int i = 0; i < 10000; i++) {
            check(set.insert(i * step));
        }
        check(set.size() == 10000);
        for (int i = 9999; i >= 0; i--) {
            check(set.contains(i * step));
            check(set.delete(i * step));
        }
        check(set.size() == 0);
    }

    public static void main(String[] args) {
        edgeCases(new BinarySearchTree());
        edgeCases(new HashTable());
        randomized(new BinarySearchTree());
        randomized(new HashTable());
        stress(new BinarySearchTree(), 1);
        stress(new HashTable(), 16384);
        System.out.println("PASS: " + checks + " checks");
    }
}
