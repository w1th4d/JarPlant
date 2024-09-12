package io.github.w1th4d.jarplant;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.w1th4d.jarplant.TestHelpers.findSubArray;
import static io.github.w1th4d.jarplant.TestHelpers.getDiffingEntries;
import static org.junit.Assert.*;

/**
 * Contains tests for the test helpers.
 * This may be a bit of meta testing (testing the tests). Not all methods from TestHelpers may warrant tests.
 * Add tests to this class whenever a TestHelper method gets a bit complicated and/or may need some debugging.
 */
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
