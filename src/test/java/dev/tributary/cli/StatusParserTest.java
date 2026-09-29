package dev.tributary.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tributary.cli.Model.Component;
import dev.tributary.cli.Model.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusParserTest {

    private static String fixture(String name) throws IOException {
        try (var in = StatusParserTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesStatusWithIdeFilesUnresolved() throws Exception {
        List<Workspace> workspaces = StatusParser.parse(fixture("status_clean.json"));
        assertEquals(1, workspaces.size());
        Workspace ws = workspaces.get(0);
        assertEquals("Área de Trabalho P100_ATD_9485000_sample_service_DEV1", ws.name());
        assertEquals("P100_ATD_9485000_sample_service_DEV1", ws.flowTarget());
        Component c = ws.components().get(0);
        assertEquals("P100_sample_service", c.name());
        assertTrue(c.loaded());
        assertEquals(2, c.unresolved().size());
        assertTrue(c.unresolved().stream()
                .allMatch(change -> change.added() && change.path().endsWith(".iml")));
        assertTrue(c.incoming().isEmpty());
        assertTrue(c.outgoing().isEmpty());
    }

    @Test
    void repairsDoubleEncodedBaselineName() throws Exception {
        Component c = StatusParser.parse(fixture("status_clean.json"))
                .get(0)
                .components()
                .get(0);
        assertTrue(c.baseline().contains("Construção"), c.baseline());
    }

    @Test
    void parsesUnresolvedAddition() throws Exception {
        Component c = StatusParser.parse(fixture("status_unresolved.json"))
                .get(0)
                .components()
                .get(0);
        assertEquals(1, c.unresolved().size());
        var change = c.unresolved().get(0);
        assertEquals("/src/main/java/Sample6459.java", change.path());
        assertTrue(change.added());
        assertFalse(change.deleted());
        assertFalse(change.contentChanged());
    }

    @Test
    void parsesChangeSetList() throws Exception {
        var list = StatusParser.parseChangeSets(fixture("list_changesets.json"));
        assertEquals(3, list.size());
        assertEquals("change set comment 5314", list.get(0).comment());
        assertEquals("_FAKEUUID97891906039091", list.get(0).uuid());
        assertFalse(list.get(0).conflict());
    }

    @Test
    void parsesWorkItemsStatesAndNormalizedPathsFromHistory() throws Exception {
        var changeSets = StatusParser.parseChangeSets(fixture("show_history.json"));
        assertEquals(3, changeSets.size());
        var first = changeSets.get(0);
        assertEquals(1, first.workItems().size());
        assertTrue(first.workItems().get(0).id() >= 9000000, first.workItems().toString());
        assertTrue(first.workItems().get(0).summary().startsWith("Sample work item"));
        assertTrue(first.complete());
        assertFalse(first.current());
        assertFalse(first.changes().isEmpty());
        var change = first.changes().get(0);
        assertTrue(change.path().startsWith("/"), change.path());
        assertFalse(change.path().contains("\\"), change.path());
        assertTrue(change.contentChanged());
        assertTrue(change.stateId() != null && change.beforeStateId() != null);
    }

    @Test
    void parsesFlowTargets() throws Exception {
        var targets = StatusParser.parseFlowTargets(fixture("list_flowtargets.json"));
        assertEquals(1, targets.size());
        assertEquals("STREAM", targets.get(0).type());
        assertTrue(targets.get(0).currentIncoming() && targets.get(0).currentOutgoing());
    }

    @Test
    void findsTheUuidOfANewChangeSet() {
        assertEquals("_abcdefghijklmnopqrstuv", StatusParser.firstUuid("{\"uuid\": \"_abcdefghijklmnopqrstuv\"}"));
        assertEquals(
                "_abcdefghijklmnopqrstuv", StatusParser.firstUuid("Change set (1234) _abcdefghijklmnopqrstuv created"));
        assertNull(StatusParser.firstUuid("nothing here"));
    }

    @Test
    void mojibakeRepairLeavesNormalTextAlone() {
        assertEquals("Área", TextRepair.repairMojibake("Área"));
        assertEquals("Construção", TextRepair.repairMojibake("ConstruÃ§Ã£o"));
        assertEquals("abc", TextRepair.repairMojibake("abc"));
    }
}
