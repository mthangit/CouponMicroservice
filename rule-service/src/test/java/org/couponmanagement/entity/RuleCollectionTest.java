package org.couponmanagement.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleCollectionTest {

    @Test
    void ruleCollectionConstructors_CreateValidRuleCollection() {
        // Test NoArgs constructor
        RuleCollection collection = new RuleCollection();
        assertNull(collection.getId());
        assertNull(collection.getName());
        assertNull(collection.getRuleIds());
        assertNull(collection.getCreatedAt());
        assertNull(collection.getUpdatedAt());

        // Test AllArgs constructor
        LocalDateTime now = LocalDateTime.now();
        RuleCollection collectionWithAllArgs = new RuleCollection(
                1,
                "Test Collection",
                "[1, 2, 3]",
                now,
                now
        );

        assertEquals(1, collectionWithAllArgs.getId());
        assertEquals("Test Collection", collectionWithAllArgs.getName());
        assertEquals("[1, 2, 3]", collectionWithAllArgs.getRuleIds());
        assertEquals(now, collectionWithAllArgs.getCreatedAt());
        assertEquals(now, collectionWithAllArgs.getUpdatedAt());
    }

    @Test
    void settersAndGetters() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        LocalDateTime now = LocalDateTime.now();

        // Act
        collection.setId(1);
        collection.setName("Test Collection");
        collection.setRuleIds("[1, 2, 3]");
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);

        // Assert
        assertEquals(1, collection.getId());
        assertEquals("Test Collection", collection.getName());
        assertEquals("[1, 2, 3]", collection.getRuleIds());
        assertEquals(now, collection.getCreatedAt());
        assertEquals(now, collection.getUpdatedAt());
    }

    @Test
    void prePersist_SetsTimestamps() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        LocalDateTime beforePersist = LocalDateTime.now();

        // Act
        collection.onCreate();

        // Assert
        assertNotNull(collection.getCreatedAt());
        assertNotNull(collection.getUpdatedAt());
        assertTrue(collection.getCreatedAt().isAfter(beforePersist.minusSeconds(1)));
        assertTrue(collection.getUpdatedAt().isAfter(beforePersist.minusSeconds(1)));
        assertEquals(collection.getCreatedAt(), collection.getUpdatedAt());
    }

    @Test
    void preUpdate_UpdatesTimestamp() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        collection.onCreate();
        LocalDateTime originalCreatedAt = collection.getCreatedAt();
        LocalDateTime originalUpdatedAt = collection.getUpdatedAt();

        // Wait a bit to ensure timestamp difference
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        collection.onUpdate();

        // Assert
        assertEquals(originalCreatedAt, collection.getCreatedAt()); // Should not change
        assertTrue(collection.getUpdatedAt().isAfter(originalUpdatedAt)); // Should be updated
    }

    @Test
    void getRuleIdsList_ValidJson_ReturnsCorrectList() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        collection.setRuleIds("[1, 2, 3]");

        // Act
        List<Integer> ruleIdsList = collection.getRuleIdsList();

        // Assert
        assertNotNull(ruleIdsList);
        assertEquals(3, ruleIdsList.size());
        assertTrue(ruleIdsList.contains(1));
        assertTrue(ruleIdsList.contains(2));
        assertTrue(ruleIdsList.contains(3));
    }

    @Test
    void getRuleIdsList_EmptyJson_ReturnsEmptyList() {
        // Test with null
        RuleCollection collection1 = new RuleCollection();
        collection1.setRuleIds(null);
        assertTrue(collection1.getRuleIdsList().isEmpty());

        // Test with empty string
        RuleCollection collection2 = new RuleCollection();
        collection2.setRuleIds("");
        assertTrue(collection2.getRuleIdsList().isEmpty());

        // Test with whitespace
        RuleCollection collection3 = new RuleCollection();
        collection3.setRuleIds("   ");
        assertTrue(collection3.getRuleIdsList().isEmpty());

        // Test with empty array
        RuleCollection collection4 = new RuleCollection();
        collection4.setRuleIds("[]");
        assertTrue(collection4.getRuleIdsList().isEmpty());
    }

    @Test
    void getRuleIdsList_InvalidJson_ReturnsEmptyList() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        collection.setRuleIds("invalid-json");

        // Act
        List<Integer> ruleIdsList = collection.getRuleIdsList();

        // Assert
        assertNotNull(ruleIdsList);
        assertTrue(ruleIdsList.isEmpty());
    }

    @Test
    void getRuleIdsList_SingleElement_ReturnsCorrectList() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        collection.setRuleIds("[42]");

        // Act
        List<Integer> ruleIdsList = collection.getRuleIdsList();

        // Assert
        assertNotNull(ruleIdsList);
        assertEquals(1, ruleIdsList.size());
        assertEquals(42, ruleIdsList.get(0));
    }

    @Test
    void setRuleIdsList_ValidList_SetsCorrectJson() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        List<Integer> ruleIds = List.of(1, 2, 3);

        // Act
        collection.setRuleIdsList(ruleIds);

        // Assert
        assertNotNull(collection.getRuleIds());
        assertTrue(collection.getRuleIds().contains("1"));
        assertTrue(collection.getRuleIds().contains("2"));
        assertTrue(collection.getRuleIds().contains("3"));

        // Verify round-trip
        List<Integer> retrievedIds = collection.getRuleIdsList();
        assertEquals(ruleIds.size(), retrievedIds.size());
        assertTrue(retrievedIds.containsAll(ruleIds));
    }

    @Test
    void setRuleIdsList_EmptyList_SetsEmptyArrayJson() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        List<Integer> emptyList = List.of();

        // Act
        collection.setRuleIdsList(emptyList);

        // Assert
        assertEquals("[]", collection.getRuleIds());
        assertTrue(collection.getRuleIdsList().isEmpty());
    }

    @Test
    void setRuleIdsList_SingleElement_SetsCorrectJson() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        List<Integer> singleElementList = List.of(42);

        // Act
        collection.setRuleIdsList(singleElementList);

        // Assert
        assertTrue(collection.getRuleIds().contains("42"));
        assertEquals(1, collection.getRuleIdsList().size());
        assertEquals(42, collection.getRuleIdsList().get(0));
    }

    @Test
    void equalsAndHashCode() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        RuleCollection collection1 = new RuleCollection(
                1,
                "Test Collection",
                "[1, 2, 3]",
                now,
                now
        );

        RuleCollection collection2 = new RuleCollection(
                1,
                "Test Collection",
                "[1, 2, 3]",
                now,
                now
        );

        RuleCollection collection3 = new RuleCollection(
                2,
                "Different Collection",
                "[4, 5, 6]",
                now,
                now
        );

        // Assert
        assertEquals(collection1, collection2);
        assertEquals(collection1.hashCode(), collection2.hashCode());
        assertNotEquals(collection1, collection3);
        assertNotEquals(collection1.hashCode(), collection3.hashCode());
    }

    @Test
    void toString_ContainsExpectedFields() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        collection.setId(1);
        collection.setName("Test Collection");
        collection.setRuleIds("[1, 2, 3]");

        // Act
        String toString = collection.toString();

        // Assert
        assertTrue(toString.contains("RuleCollection"));
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("name=Test Collection"));
        assertTrue(toString.contains("ruleIds=[1, 2, 3]"));
    }

    @Test
    void jsonRoundTrip_ComplexRuleIds() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        List<Integer> complexIds = List.of(1, 100, 999, 1234);

        // Act
        collection.setRuleIdsList(complexIds);
        List<Integer> retrievedIds = collection.getRuleIdsList();

        // Assert
        assertEquals(complexIds.size(), retrievedIds.size());
        assertTrue(retrievedIds.containsAll(complexIds));
        complexIds.forEach(id -> assertTrue(retrievedIds.contains(id)));
    }

    @Test
    void jsonRoundTrip_PreserveOrder() {
        // Arrange
        RuleCollection collection = new RuleCollection();
        List<Integer> orderedIds = List.of(5, 1, 3, 2, 4);

        // Act
        collection.setRuleIdsList(orderedIds);
        List<Integer> retrievedIds = collection.getRuleIdsList();

        // Assert
        assertEquals(orderedIds, retrievedIds); // Should preserve order
    }
}
