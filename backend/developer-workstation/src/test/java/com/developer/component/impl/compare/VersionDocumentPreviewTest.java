package com.developer.component.impl.compare;

import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VersionDocumentPreviewTest {
    @Test void middleEditsStayVisibleWithoutReturningWholeDocument() {
        String prefix = "a".repeat(100_000), suffix = "z".repeat(100_000);
        var field = VersionDocumentPreview.field(TextNode.valueOf(prefix + "OLD" + suffix), TextNode.valueOf(prefix + "NEW" + suffix));
        assertTrue(field.oldValue().contains("OLD")); assertTrue(field.newValue().contains("NEW"));
        assertTrue(field.oldValue().length() <= 1202); assertTrue(field.textContext().oldStart() > 0);
        assertFalse(field.textContext().omittedChanges());
    }

    @Test void multipleDistantChangesDeclareOmittedChangedText() {
        var field = VersionDocumentPreview.field(TextNode.valueOf("old" + "x".repeat(10_000) + "tail old"),
                TextNode.valueOf("new" + "x".repeat(10_000) + "tail new"));
        assertTrue(field.textContext().omittedChanges()); assertTrue(field.oldValue().contains("old"));
        assertTrue(field.oldValue().length() <= 1202);
    }

    @Test void missingEmptyAndShortValuesAreNotCollapsed() {
        var added = VersionDocumentPreview.field(null, TextNode.valueOf(""));
        assertNull(added.oldValue()); assertEquals("", added.newValue()); assertNull(added.textContext());
        var cleared = VersionDocumentPreview.field(TextNode.valueOf("old"), TextNode.valueOf(""));
        assertEquals("old", cleared.oldValue()); assertEquals("", cleared.newValue());
    }

    @Test void surrogatePairsAreNotSplitAtExcerptBoundaries() {
        var field = VersionDocumentPreview.field(TextNode.valueOf("😀".repeat(2000) + "a"),
                TextNode.valueOf("😀".repeat(2000) + "b"));
        assertFalse(Character.isLowSurrogate(field.oldValue().charAt(1)));
        assertTrue(field.oldValue().endsWith("a"));
    }
}
