package dev.tributary.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tributary.cli.Model.ChangeSet;
import dev.tributary.cli.Model.Component;
import dev.tributary.cli.Model.Conflict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealOutputsTest {

    private static String fixture(String name) throws IOException {
        try (var in = RealOutputsTest.class.getResourceAsStream("/fixtures/" + name)) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.startsWith("﻿") ? text.substring(1) : text;
        }
    }

    private static Component component(String fixture) throws IOException {
        return StatusParser.parse(fixture(fixture)).get(0).components().get(0);
    }

    @Test
    void outgoingChangeSetCarriesFilesAndStateFlags() throws Exception {
        Component component = component("status_outgoing.json");
        assertEquals(1, component.outgoing().size());
        ChangeSet changeSet = component.outgoing().get(0);
        assertTrue(changeSet.comment().startsWith("change set comment"), changeSet.comment());
        assertTrue(changeSet.current());
        assertFalse(changeSet.complete());
        assertFalse(changeSet.conflict());
        assertEquals(1, changeSet.changes().size());
        assertTrue(changeSet.changes().get(0).path().endsWith("package-info.java"));
        assertTrue(component.unresolved().isEmpty());
    }

    @Test
    void incomingChangeSetIsListedWithItsFiles() throws Exception {
        Component component = component("status_incoming.json");
        assertEquals(1, component.incoming().size());
        assertFalse(component.incoming().get(0).changes().isEmpty());
        assertTrue(component.outgoing().isEmpty());
    }

    @Test
    void suspendedChangeSetsAreReportedSeparately() throws Exception {
        Component component = component("status_suspended.json");
        assertTrue(component.outgoing().isEmpty());
        assertEquals(1, component.suspended().size());
    }

    @Test
    void conflictFlagComesFromTheChangeSetState() throws Exception {
        Component component = component("status_conflict.json");
        assertTrue(component.outgoing().stream().anyMatch(ChangeSet::conflict));
    }

    @Test
    void showConflictsIsParsed() throws Exception {
        List<Conflict> conflicts = StatusParser.parseConflicts(fixture("show_conflicts.json"));
        assertEquals(1, conflicts.size());
        Conflict conflict = conflicts.get(0);
        assertTrue(conflict.path().startsWith("/src/main/java/"), conflict.path());
        assertTrue(conflict.path().endsWith("package-info.java"));
        assertTrue(conflict.contentConflict());
        assertFalse(conflict.propertyConflict());
        assertEquals("Modified", conflict.outgoingType());
        assertEquals("Modified", conflict.proposedType());
    }

    @Test
    void conflictContentKeepsLinesUntouchedExceptTheFirstPrefix() throws Exception {
        String mine = StatusParser.parseConflictContent(fixture("conflict_content_mine.txt"));
        String proposed = StatusParser.parseConflictContent(fixture("conflict_content_proposed.txt"));
        String ancestor = StatusParser.parseConflictContent(fixture("conflict_content_ancestor.txt"));
        assertTrue(mine.startsWith("package "), mine);
        assertTrue(mine.contains("// alteracao b"), mine);
        assertTrue(proposed.contains("// alteracao a"), proposed);
        assertFalse(ancestor.contains("// alteracao"), ancestor);
        assertFalse(mine.startsWith(" "));
    }

    @Test
    void onlyTheFirstContentLineLosesItsIndentation() {
        String output = "Content:\n  first\n    indented line\n  two spaces\n\n";
        assertEquals("first\n    indented line\n  two spaces\n", StatusParser.parseConflictContent(output));
    }

    @Test
    void createChangeSetOutputHasTheNewUuid() throws Exception {
        String uuid = StatusParser.firstUuid(fixture("create_changeset.json"));
        assertTrue(uuid != null && uuid.startsWith("_FAKEUUID"), uuid);
    }

    @Test
    void exitCodesLearnedFromTheServerAreClassified() {
        assertEquals(
                RtcException.Kind.CONFLICT,
                ErrorCodes.classify(
                                new CliResult(11, "", "Following workspaces still have conflicts", false), "scm accept")
                        .kind());
        assertEquals(
                RtcException.Kind.UNCOMMITTED_CHANGES,
                ErrorCodes.classify(
                                new CliResult(34, "", "There are 1 items that are not checked in.", false),
                                "scm resolve")
                        .kind());
    }
}
