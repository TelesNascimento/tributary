package dev.tributary.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CommitMessageTest {

    @Test
    void extractsLeadingWorkItem() {
        assertEquals(
                new CommitMessage.Parsed("ajusta Jenkinsfile", "9000002"),
                CommitMessage.parse("#9000002 ajusta Jenkinsfile"));
        assertEquals("9000002", CommitMessage.parse("9000002: corrige").workItem());
        assertEquals("corrige", CommitMessage.parse("WI 9000002 - corrige").comment());
    }

    @Test
    void keepsPlainMessageAndIgnoresShortNumbers() {
        assertNull(CommitMessage.parse("corrige bug").workItem());
        assertNull(CommitMessage.parse("2026 ajuste").workItem());
        assertEquals("2026 ajuste", CommitMessage.parse("2026 ajuste").comment());
        assertNull(CommitMessage.parse("9000002").workItem());
    }
}
