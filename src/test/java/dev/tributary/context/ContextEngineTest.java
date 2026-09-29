package dev.tributary.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tributary.cli.RtcException;
import dev.tributary.context.ContextState.Entry;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContextEngineTest {

    private final ContextState state = new ContextState();
    private final RecordingBackend recording = new RecordingBackend();
    private final ContextEngine engine =
            new ContextEngine(state, Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void startCreatesAChangeSetAssociatedWithTheCard() throws Exception {
        Entry entry = engine.start(recording.backend(), "_ws", 9000001, "Migrate service");
        assertEquals(
                List.of("createChangeSet(9000001: Migrate service,true)", "addWorkItem(_ws,_cs1,9000001)"),
                recording.calls());
        assertEquals("_cs1", entry.currentChangeSet);
        assertEquals(9000001, engine.active().orElseThrow().workItemId);
    }

    @Test
    void startingAKnownCardOnlyMakesItCurrentAgain() throws Exception {
        engine.start(recording.backend(), "_ws", 9000001, "Migrate service");
        recording.calls().clear();
        engine.start(recording.backend(), "_ws", 9000001, "Migrate service");
        assertEquals(List.of("setCurrentChangeSet(_cs1)"), recording.calls());
    }

    @Test
    void checkInWithANewMessageOpensAnotherChangeSetOnTheSameCard() throws Exception {
        engine.start(recording.backend(), "_ws", 9000001, "Migrate service");
        recording.calls().clear();
        String changeSet = engine.checkIn(recording.backend(), List.of(Path.of("A.java")), "fix logging", false);
        assertEquals("_cs2", changeSet);
        assertEquals(
                List.of(
                        "createChangeSet(fix logging,true)",
                        "addWorkItem(_ws,_cs2,9000001)",
                        "checkinTo(_cs2,[A.java],false)"),
                recording.calls());
        assertEquals(List.of("_cs1", "_cs2"), engine.active().orElseThrow().changeSets);
    }

    @Test
    void checkInWithTheSameOrEmptyMessageAddsToTheCurrentChangeSet() throws Exception {
        engine.start(recording.backend(), "_ws", 9000001, "Migrate service");
        recording.calls().clear();
        engine.checkIn(recording.backend(), List.of(Path.of("A.java")), "", false);
        engine.checkIn(recording.backend(), List.of(Path.of("B.java")), "9000001: migrate service", true);
        assertEquals(List.of("checkinTo(_cs1,[A.java],false)", "checkinTo(_cs1,[B.java],true)"), recording.calls());
    }

    @Test
    void checkInWithoutAContextIsRejected() {
        assertThrows(
                IllegalStateException.class,
                () -> engine.checkIn(recording.backend(), List.of(Path.of("A.java")), "x", false));
    }

    @Test
    void switchingCardsSuspendsOnlyOutgoingChangeSetsOfThePreviousCard() throws Exception {
        engine.start(recording.backend(), "_ws", 9000001, "First");
        engine.checkIn(recording.backend(), List.of(Path.of("A.java")), "second work", false);
        recording.calls().clear();
        engine.switchTo(recording.backend(), "_ws", 9000002, "Second", true, Set.of("_cs2"));
        assertEquals("suspend([_cs2])", recording.calls().get(0));
        assertTrue(engine.find(9000001).orElseThrow().suspended);
        assertEquals(9000002, engine.active().orElseThrow().workItemId);
    }

    @Test
    void switchingBackResumesTheSuspendedCard() throws Exception {
        engine.start(recording.backend(), "_ws", 9000001, "First");
        engine.switchTo(recording.backend(), "_ws", 9000002, "Second", true, Set.of("_cs1"));
        recording.calls().clear();
        engine.switchTo(recording.backend(), "_ws", 9000001, "First", true, Set.of());
        assertEquals("resume([_cs1])", recording.calls().get(0));
        assertFalse(engine.find(9000001).orElseThrow().suspended);
    }

    @Test
    void reassociateMovesTheChangeSetToAnotherCard() throws Exception {
        engine.start(recording.backend(), "_ws", 9000001, "First");
        recording.calls().clear();
        engine.reassociate(recording.backend(), "_cs1", 9000001, 9000002, "Second");
        assertEquals(List.of("removeWorkItem(_ws,_cs1,9000001)", "addWorkItem(_ws,_cs1,9000002)"), recording.calls());
        assertTrue(engine.find(9000001).orElseThrow().changeSets.isEmpty());
        assertEquals(List.of("_cs1"), engine.find(9000002).orElseThrow().changeSets);
    }

    @Test
    void reconcileDropsDeliveredChangeSetsAndEmptyInactiveContexts() throws Exception {
        engine.start(recording.backend(), "_ws", 9000001, "First");
        engine.switchTo(recording.backend(), "_ws", 9000002, "Second", false, Set.of());
        engine.reconcile(Set.of("_cs2"));
        assertTrue(engine.find(9000001).isEmpty());
        assertEquals(List.of("_cs2"), engine.find(9000002).orElseThrow().changeSets);
    }

    @Test
    void aFailedAssociationKeepsTheChangeSetSoItCanBeRepaired() {
        RecordingBackend failing = new RecordingBackend().failOn("addWorkItem");
        assertThrows(RtcException.class, () -> engine.start(failing.backend(), "_ws", 9000003, "Third"));
        assertEquals(List.of("_cs1"), state.contexts.get(0).changeSets);
        assertEquals(0, state.activeWorkItem);
    }
}
