package dev.tributary.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tributary.backend.WorkspaceNames.Problem;
import org.junit.jupiter.api.Test;

class WorkspaceNamesTest {

    @Test
    void acceptsOrdinaryNames() {
        assertEquals(Problem.NONE, WorkspaceNames.check("U000001_orders_DEV1"));
    }

    @Test
    void rejectsEmptyAtSignAndMultilineNames() {
        assertEquals(Problem.EMPTY, WorkspaceNames.check("  "));
        assertEquals(Problem.EMPTY, WorkspaceNames.check(null));
        assertEquals(Problem.AT_SIGN, WorkspaceNames.check("dev@orders"));
        assertEquals(Problem.MULTILINE, WorkspaceNames.check("a\nb"));
    }

    @Test
    void suggestsUserPrefixedNames() {
        assertEquals("U000001_orders-stream", WorkspaceNames.suggest("U000001", "orders-stream"));
    }

    @Test
    void doesNotRepeatAPrefixOrKeepAtSigns() {
        assertEquals("U000001_orders", WorkspaceNames.suggest("U000001", "U000001_orders"));
        assertEquals("orders", WorkspaceNames.suggest("", "orders"));
        assertEquals("U1_dev_repo", WorkspaceNames.suggest("U1", "dev@repo"));
    }
}
