package org.example;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.example.TestHelpers.findSubArray;
import static org.example.TestHelpers.getDiffingEntries;
import static org.junit.Assert.*;

// Yeah, this is very meta, I know...
public class TestHelpersTests {
    @Test
    public void testGetDiffingEntries() {
        Map<String, String> hashesBefore = new HashMap<>();
        hashesBefore.put("this/is/some/ToBeModifiedEntry.class", "aaAAaaAA");
        hashesBefore.put("this/is/some/ShouldBeUnchangedEntry.class", "bbBBbbBB");
        hashesBefore.put("this/is/another/ToBeModifiedEntry.class", "ccCCccCC");
        hashesBefore.put("this/is/another/ToBeRemovedThing.class", "ddDDddDD");

        Map<String, String> hashesAfter = new HashMap<>();
        hashesAfter.put("this/is/some/ToBeModifiedEntry.class", "MODIFIED");        // <-- modified
        hashesAfter.put("this/is/some/AddedEntry.class", "bbBBbbBB");               // <-- added
        hashesAfter.put("this/is/some/ShouldBeUnchangedEntry.class", "bbBBbbBB");   // <-- untouched
        hashesAfter.put("this/is/another/ToBeModifiedEntry.class", "MODIFIED");     // <-- modified
        //hashesAfter.put("this/is/another/ToBeRemovedThing.class", "ddDDddDD");          // <-- removed
        Set<String> diffingEntries = getDiffingEntries(hashesBefore, hashesAfter);

        assertTrue("Found the first modification.",
                diffingEntries.contains("this/is/some/ToBeModifiedEntry.class"));
        assertTrue("Found the second modification.",
                diffingEntries.contains("this/is/another/ToBeModifiedEntry.class"));
        assertFalse("Ignored the added entry.",
                diffingEntries.contains("this/is/some/AddedEntry.class"));
        assertFalse("Ignored the removed entry.",
                diffingEntries.contains("his/is/another/ToBeRemovedThing.class"));

    }

    @Test
    public void testFindSubArray() {
        assertEquals(Optional.of(1), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{2, 3, 4}));
        assertEquals(Optional.of(0), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1}));
        assertEquals(Optional.of(1), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{2}));
        assertEquals(Optional.of(4), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{5}));
        assertEquals(Optional.of(0), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1, 2}));
        assertEquals(Optional.of(3), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{4, 5}));
        assertEquals(Optional.of(0), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1, 2, 3, 4, 5}));
        assertEquals(Optional.of(1), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{2, 3, 4, 5}));
        assertEquals(Optional.empty(), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{2, 3, 4, 5, 1}));
        assertEquals(Optional.empty(), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1, 2, 3, 4, 5, 5}));
        assertEquals(Optional.empty(), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1, 1, 2, 3, 4, 5}));
        assertEquals(Optional.empty(), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1, 1, 2, 3, 4}));
        assertEquals(Optional.empty(), findSubArray(new byte[]{}, new byte[]{1, 1, 2, 3, 4}));
        assertEquals(Optional.of(0), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{}));
        assertEquals(Optional.of(0), findSubArray(new byte[]{}, new byte[]{}));
    }
}
