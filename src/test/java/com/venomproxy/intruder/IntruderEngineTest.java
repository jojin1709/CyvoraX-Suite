package com.venomproxy.intruder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntruderEngineTest {
    private static final String MARKER = "\u00A7";

    private final IntruderEngine engine = new IntruderEngine();
    private final String request = "GET http://example.test/search?a=" + MARKER + "one" + MARKER
            + "&b=" + MARKER + "two" + MARKER + " HTTP/1.1\r\nHost: example.test\r\n\r\n";

    @Test
    void sniperMutatesOnePositionAtATime() {
        List<IntruderEngine.Mutation> mutations = engine.mutationsFor(request, List.of("x", "y"), IntruderEngine.AttackType.SNIPER);

        assertEquals(4, mutations.size());
        assertTrue(mutations.get(0).requestRaw().contains("a=x&b=two"));
        assertTrue(mutations.get(2).requestRaw().contains("a=one&b=x"));
    }

    @Test
    void batteringRamMutatesAllPositionsWithSamePayload() {
        List<IntruderEngine.Mutation> mutations = engine.mutationsFor(request, List.of("x", "y"), IntruderEngine.AttackType.BATTERING_RAM);

        assertEquals(2, mutations.size());
        assertTrue(mutations.get(0).requestRaw().contains("a=x&b=x"));
        assertTrue(mutations.get(1).requestRaw().contains("a=y&b=y"));
    }

    @Test
    void pitchforkUsesPayloadSetsByPosition() {
        List<IntruderEngine.Mutation> mutations = engine.mutationsFor(request,
                List.of("a1", "a2", "", "b1", "b2"), IntruderEngine.AttackType.PITCHFORK);

        assertEquals(2, mutations.size());
        assertTrue(mutations.get(0).requestRaw().contains("a=a1&b=b1"));
        assertTrue(mutations.get(1).requestRaw().contains("a=a2&b=b2"));
    }

    @Test
    void clusterBombBuildsCartesianProduct() {
        List<IntruderEngine.Mutation> mutations = engine.mutationsFor(request,
                List.of("a1", "a2", "", "b1", "b2"), IntruderEngine.AttackType.CLUSTER_BOMB);

        assertEquals(4, mutations.size());
        assertTrue(mutations.stream().anyMatch(mutation -> mutation.requestRaw().contains("a=a2&b=b1")));
        assertTrue(mutations.stream().anyMatch(mutation -> mutation.requestRaw().contains("a=a2&b=b2")));
    }
}
