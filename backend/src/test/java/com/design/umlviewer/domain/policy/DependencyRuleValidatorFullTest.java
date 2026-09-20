package com.design.umlviewer.domain.policy;

import com.design.umlviewer.domain.model.DependencyEdge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DependencyRuleValidatorFullTest {

    @Test
    void testFromLevelsAndNullEdgeCases() {
        // Null levels
        DependencyRuleValidator emptyValidator = DependencyRuleValidator.fromLevels(null);
        assertTrue(emptyValidator.getRanks().isEmpty());

        // Level with null group
        List<List<String>> levelsWithNull = new ArrayList<>();
        levelsWithNull.add(null);
        levelsWithNull.add(List.of("domain"));
        DependencyRuleValidator v = DependencyRuleValidator.fromLevels(levelsWithNull);
        assertEquals(1, v.getRanks().get("domain"));

        // Null edge and blank id checks
        assertNull(v.resolveRank(null));
        assertNull(v.resolveRank(""));
        assertNull(v.resolveRank("unknown"));

        // Non-dependency edges are never violating
        DependencyEdge assoc = new DependencyEdge("domain.A", "adapter.B", DependencyEdge.Kind.ASSOCIATION, null, false);
        assertFalse(v.evaluate(assoc).isViolating());

        // Edge where target is unranked
        DependencyEdge unrankedTarget = new DependencyEdge("domain.A", "unranked.B", DependencyEdge.Kind.DEPENDENCY, null, false);
        assertFalse(v.evaluate(unrankedTarget).isViolating());

        // Edge where source is unranked
        DependencyEdge unrankedSource = new DependencyEdge("unranked.A", "domain.B", DependencyEdge.Kind.DEPENDENCY, null, false);
        assertFalse(v.evaluate(unrankedSource).isViolating());

        // Edge where fromRank > toRank (outer -> inner, not violating)
        Map<String, Integer> twoRanks = Map.of("inner", 0, "outer", 1);
        DependencyRuleValidator v2 = new DependencyRuleValidator(twoRanks);
        DependencyEdge outerToInner = new DependencyEdge("outer.A", "inner.B", DependencyEdge.Kind.DEPENDENCY, null, false);
        assertFalse(v2.evaluate(outerToInner).isViolating());

        // Edge where fromRank == toRank (same rank, not violating)
        DependencyEdge sameRank = new DependencyEdge("inner.A", "inner.B", DependencyEdge.Kind.DEPENDENCY, null, false);
        assertFalse(v2.evaluate(sameRank).isViolating());

        // Edge where fromRank < toRank (inner -> outer, VIOLATING)
        DependencyEdge innerToOuter = new DependencyEdge("inner.A", "outer.B", DependencyEdge.Kind.DEPENDENCY, null, false);
        assertTrue(v2.evaluate(innerToOuter).isViolating());
    }

    @Test
    void testFromProposal() {
        // Null proposal
        DependencyRuleValidator empty = DependencyRuleValidator.fromProposal(null);
        assertTrue(empty.getRanks().isEmpty());

        // Proposal with layers
        Proposal.ProposalLayer layer1 = new Proposal.ProposalLayer("core", "Core", List.of("com.app.core"));
        Proposal.ProposalLayer layer2 = new Proposal.ProposalLayer("infra", "Infra", List.of("com.app.infra"));
        Proposal proposal = new Proposal("p1", "Test Proposal", List.of(layer1, layer2), List.of());

        DependencyRuleValidator validator = DependencyRuleValidator.fromProposal(proposal);
        assertEquals(0, validator.getRanks().get("com.app.core"));
        assertEquals(1, validator.getRanks().get("com.app.infra"));

        // Check proposal records
        assertEquals("p1", proposal.id());
        assertEquals("Test Proposal", proposal.name());
        assertEquals(2, proposal.layers().size());
        assertTrue(proposal.omit().isEmpty());
        assertEquals("core", layer1.id());
        assertEquals("Core", layer1.label());
        assertEquals(List.of("com.app.core"), layer1.packages());
    }

    @Test
    void testArchitecturePolicyRecord() {
        ArchitecturePolicy policy = new ArchitecturePolicy(
                "Title",
                "src",
                "prefix",
                true,
                List.of("a"),
                List.of(List.of("a")),
                List.of("foreign"),
                List.of(),
                List.of()
        );
        assertEquals("Title", policy.title());
        assertEquals("src", policy.src());
        assertEquals("prefix", policy.prefix());
        assertTrue(policy.hierarchical());
        assertEquals(List.of("a"), policy.order());
        assertEquals(1, policy.levels().size());
        assertEquals(List.of("foreign"), policy.foreign());
        assertTrue(policy.proposals().isEmpty());
        assertTrue(policy.omit().isEmpty());
    }
}
