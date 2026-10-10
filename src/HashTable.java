public final class HashTable implements IntSet {
    private static final class Entry {
        final int key;
        Entry next;

        Entry(int key, Entry next) {
            this.key = key;
            this.next = next;
        }
    }

    private Entry[] buckets = new Entry[16];
    private int size;

    private int index(int key, int capacity) {
        return Math.floorMod(key, capacity);
    }

    public boolean insert(int key) {
        if (contains(key)) {
            return false;
        }
        if (size + 1 > buckets.length * 3 / 4) {
            resize();
        }
        int index = index(key, buckets.length);
        buckets[index] = new Entry(key, buckets[index]);
        size++;
        return true;
    }

    private void resize() {
        Entry[] expanded = new Entry[buckets.length * 2];
        for (Entry head : buckets) {
            Entry current = head;
            while (current != null) {
                Entry next = current.next;
                int index = index(current.key, expanded.length);
                current.next = expanded[index];
                expanded[index] = current;
                current = next;
            }
        }
        buckets = expanded;
    }

    public boolean contains(int key) {
        Entry current = buckets[index(key, buckets.length)];
        while (current != null) {
            if (current.key == key) {
                return true;
            }
            current = current.next;
        }
        return false;
    }

    public boolean delete(int key) {
        int index = index(key, buckets.length);
        Entry previous = null;
        Entry current = buckets[index];
        while (current != null) {
            if (current.key == key) {
                if (previous == null) {
                    buckets[index] = current.next;
                } else {
                    previous.next = current.next;
                }
                size--;
                return true;
            }
            previous = current;
            current = current.next;
        }
        return false;
    }

    public int size() {
        return size;
    }
}
