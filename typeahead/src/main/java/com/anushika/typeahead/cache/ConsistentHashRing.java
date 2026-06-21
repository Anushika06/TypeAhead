package com.anushika.typeahead.cache;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Consistent hashing ring for distributed Redis node assignment.
 */
@Component
public class ConsistentHashRing {

    private static final Logger log = LoggerFactory.getLogger(ConsistentHashRing.class);

    /** Number of virtual nodes per physical node. Higher = better distribution. */
    public static final int VIRTUAL_NODES = 150;

    /**
     * The ring: a sorted map of ring position → physical node name.
     */
    private final TreeMap<Long, String> ring = new TreeMap<>();

    /** Physical node names currently on the ring. */
    private final List<String> physicalNodes = new ArrayList<>();



    /**
     * Registers the three simulated Redis nodes when the Spring context starts.
     */
    @PostConstruct
    public void init() {
        addNode("redis-node-1");
        addNode("redis-node-2");
        addNode("redis-node-3");
        log.info("ConsistentHashRing initialised: {} physical nodes, {} virtual positions",
                physicalNodes.size(), ring.size());
    }



    /**
     * Returns the physical node responsible for the given key.
     *
     * @param key the cache key
     * @return physical node name, or "no-nodes" if the ring is empty
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
     * Adds a physical node to the ring.
     *
     * @param nodeName logical name of the node
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
     * @return map of node name → ownership percentage (0.0 – 100.0), sorted by node name
     */
    public Map<String, Double> getOwnershipPercentages() {
        if (ring.isEmpty()) {
            return Map.of();
        }

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



    /**
     * Hashes a string to a 64-bit ring position using MD5.
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
