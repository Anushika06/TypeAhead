package com.anushika.typeahead.cache;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Consistent hashing ring for distributed Redis node assignment.
 *
 * <h2>What is consistent hashing?</h2>
 * Standard hashing ({@code key % N}) breaks when nodes are added or removed —
 * every key remaps, causing a full cache invalidation.  Consistent hashing
 * places both keys and nodes on a circular ring (0 to 2<sup>64</sup>-1).
 * A key is assigned to the first node encountered when traversing the ring
 * clockwise from the key's hash position.  When a node is added or removed,
 * only the keys in the affected segment need to be remapped.
 *
 * <h2>Virtual nodes</h2>
 * A physical node is represented by {@value #VIRTUAL_NODES} virtual nodes,
 * each at a different position on the ring (formed by hashing
 * {@code "nodeName#0"}, {@code "nodeName#1"}, etc.).  Virtual nodes improve
 * load distribution: without them, uneven spacing of physical nodes on the
 * ring causes one node to own a disproportionately large arc.
 *
 * <h2>Hash function</h2>
 * MD5 is used for ring placement — cryptographic strength is not needed here;
 * we only need a uniform distribution across the ring.  The first 8 bytes of
 * the digest are read as a big-endian {@code long} to produce a 64-bit ring
 * position.
 *
 * <h2>Simulated nodes</h2>
 * This implementation simulates three Redis nodes for demonstration purposes:
 * <ul>
 *   <li>{@code redis-node-1}</li>
 *   <li>{@code redis-node-2}</li>
 *   <li>{@code redis-node-3}</li>
 * </ul>
 * In a real deployment, each node name would correspond to a distinct Redis
 * instance with its own host and port.
 */
@Component
public class ConsistentHashRing {

    private static final Logger log = LoggerFactory.getLogger(ConsistentHashRing.class);

    /** Number of virtual nodes per physical node. Higher = better distribution. */
    public static final int VIRTUAL_NODES = 150;

    /**
     * The ring: a sorted map of ring position → physical node name.
     * A {@link TreeMap} is used because it supports {@code ceilingEntry()} and
     * {@code firstEntry()}, which are the core operations for ring lookup.
     */
    private final TreeMap<Long, String> ring = new TreeMap<>();

    /** Physical node names currently on the ring. */
    private final List<String> physicalNodes = new ArrayList<>();

    // ──────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Registers the three simulated Redis nodes when the Spring context starts.
     * Called automatically by {@code @PostConstruct}.
     */
    @PostConstruct
    public void init() {
        addNode("redis-node-1");
        addNode("redis-node-2");
        addNode("redis-node-3");
        log.info("ConsistentHashRing initialised: {} physical nodes, {} virtual positions",
                physicalNodes.size(), ring.size());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the physical node responsible for the given key.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Hash the key to a 64-bit ring position.</li>
     *   <li>Find the first virtual node at or after that position
     *       ({@code ceilingEntry}).</li>
     *   <li>If none found (key hashed past the last virtual node), wrap
     *       around to the first virtual node on the ring.</li>
     * </ol>
     *
     * @param key the cache key (e.g. {@code "goo"}, {@code "iphone"})
     * @return physical node name, or {@code "no-nodes"} if the ring is empty
     */
    public String getNode(String key) {
        if (ring.isEmpty()) {
            return "no-nodes";
        }

        long hash = hashKey(key);

        // Find the first virtual node position >= hash (clockwise traversal)
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);

        // Wrap around: if hash > last position, use the first node on the ring
        if (entry == null) {
            entry = ring.firstEntry();
        }

        return entry.getValue();
    }

    /**
     * Adds a physical node to the ring by inserting {@value #VIRTUAL_NODES}
     * virtual replicas at evenly distributed positions derived from MD5 hashes.
     *
     * @param nodeName logical name of the node, e.g. {@code "redis-node-1"}
     */
    public void addNode(String nodeName) {
        physicalNodes.add(nodeName);
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            long position = hashKey(nodeName + "#" + i);
            ring.put(position, nodeName);
        }
        log.debug("Node '{}' added to ring with {} virtual replicas", nodeName, VIRTUAL_NODES);
    }

    /**
     * Removes a physical node and all its virtual replicas from the ring.
     * Keys previously assigned to this node will be redistributed to the
     * next node clockwise (handled automatically by the ring structure).
     *
     * @param nodeName the name of the node to remove
     */
    public void removeNode(String nodeName) {
        physicalNodes.remove(nodeName);
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            long position = hashKey(nodeName + "#" + i);
            ring.remove(position);
        }
        log.info("Node '{}' removed from ring", nodeName);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Ring introspection (for GET /cache/ring)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable snapshot of the current physical node list.
     *
     * @return physical node names in insertion order
     */
    public List<String> getPhysicalNodes() {
        return Collections.unmodifiableList(physicalNodes);
    }

    /** @return total number of virtual node positions on the ring */
    public int getTotalRingPositions() {
        return ring.size();
    }

    /**
     * Computes the approximate ownership percentage of each physical node.
     *
     * <p>Ownership is calculated by iterating over all consecutive ring positions
     * and summing the arc length (difference between consecutive positions)
     * attributed to each node.  The wrap-around arc (from the last position to
     * the first) is included.  All arc lengths are expressed as a fraction of
     * the full 2<sup>64</sup> unsigned range.
     *
     * @return map of node name → ownership percentage (0.0 – 100.0), sorted by node name
     */
    public Map<String, Double> getOwnershipPercentages() {
        if (ring.isEmpty()) {
            return Map.of();
        }

        // Accumulate arc lengths per physical node
        Map<String, Long> arcSumUnsigned = new LinkedHashMap<>();
        for (String node : physicalNodes) {
            arcSumUnsigned.put(node, 0L);
        }

        List<Long> positions = new ArrayList<>(ring.keySet());
        // positions are already sorted (TreeMap)

        for (int i = 0; i < positions.size(); i++) {
            long current = positions.get(i);
            long next    = (i + 1 < positions.size()) ? positions.get(i + 1)
                                                       : positions.get(0);  // wrap-around

            // Unsigned arc length: handles the wrap-around correctly
            long arc = Long.compareUnsigned(next, current) > 0
                    ? next - current
                    : Long.MAX_VALUE - current + next - Long.MIN_VALUE + 1;

            String node = ring.get(current);
            arcSumUnsigned.merge(node, arc, Long::sum);
        }

        // Convert arc sums to percentages (total unsigned range = 2^64)
        // We use double arithmetic with 2^64 approximation via Long.toUnsignedString
        double totalRange = Math.pow(2, 64);
        Map<String, Double> percentages = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : arcSumUnsigned.entrySet()) {
            // Treat accumulated arc as unsigned by using double conversion
            double arcDouble = Long.compareUnsigned(e.getValue(), 0) >= 0
                    ? (double) (e.getValue() & Long.MAX_VALUE) + (e.getValue() < 0 ? (double) Long.MAX_VALUE + 1 : 0)
                    : 0;
            percentages.put(e.getKey(), Math.round((arcDouble / totalRange) * 1000.0) / 10.0);
        }
        return percentages;
    }

    /**
     * Returns the mapping of a set of representative example prefixes to their
     * assigned nodes, useful for demonstrating ring behavior in the UI.
     *
     * @return ordered map of prefix → assigned node name
     */
    public Map<String, String> getSampleMappings() {
        List<String> samples = List.of(
                "goo", "goog", "googl", "google",
                "iph", "ipho", "iphon", "iphone",
                "cha", "chat", "chatg", "chatgp", "chatgpt",
                "ama", "amaz", "amazon",
                "app", "mic", "net", "you"
        );

        Map<String, String> mappings = new LinkedHashMap<>();
        for (String prefix : samples) {
            mappings.put(prefix, getNode(prefix));
        }
        return mappings;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private: hashing
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Hashes a string to a 64-bit ring position using MD5.
     *
     * <p>MD5 produces 128 bits; we use the first 8 bytes as a big-endian
     * {@code long}.  The result is used only for ring placement, not security.
     *
     * @param key any non-null string
     * @return 64-bit ring position
     */
    private long hashKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            // Read first 8 bytes as big-endian long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            // MD5 is guaranteed by the Java specification — this cannot happen
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}
