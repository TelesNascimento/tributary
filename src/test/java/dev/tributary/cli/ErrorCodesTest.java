package dev.tributary.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ErrorCodesTest {

    private static RtcException classify(int exit, String stderr) {
        return ErrorCodes.classify(new CliResult(exit, "", stderr, false), "scm status");
    }

    @Test
    void lockedSandboxIsClassifiedByExitCode() {
        RtcException e = classify(
                27,
                "Problem running 'status':\nCould not initialize data area. Could not lock "
                        + "C:\\sb\\app - it is locked by another process.");
        assertEquals(RtcException.Kind.LOCKED, e.kind());
    }

    @Test
    void notASandbox() {
        RtcException e = classify(
                5, "Problem running 'status':\nCould not initialize data area. Directory is not shared: C:\\tmp");
        assertEquals(RtcException.Kind.NOT_A_SANDBOX, e.kind());
        assertTrue(e.getMessage().contains("Directory is not shared"), e.getMessage());
    }

    @Test
    void unmatchedItemUsesTheServerReason() {
        RtcException e = classify(25, "Problem running 'list components':\nUnmatched workspace \"_x\"");
        assertEquals(RtcException.Kind.NOT_FOUND, e.kind());
        assertEquals("Unmatched workspace \"_x\"", e.getMessage());
    }

    @Test
    void passwordPromptMeansLoginRequired() {
        assertEquals(
                RtcException.Kind.AUTH_REQUIRED,
                classify(3, "Password (U1 @ https://rtc.example.com/ccm/):").kind());
        assertEquals(
                RtcException.Kind.AUTH_REQUIRED,
                classify(1, "Password (U1 @ https://rtc.example.com/ccm/):").kind());
    }

    @Test
    void wordLoginInAnUnrelatedMessageIsNotAnAuthError() {
        RtcException e =
                classify(9, "Problem running 'deliver':\nThe change set comment mentions login and password rules.");
        assertEquals(RtcException.Kind.FAILED, e.kind());
        assertTrue(e.getMessage().contains("mentions login"), e.getMessage());
    }

    @Test
    void timeoutAndCancelAreDistinct() {
        assertEquals(
                RtcException.Kind.TIMEOUT,
                ErrorCodes.classify(new CliResult(-1, "", "", true, false), "scm")
                        .kind());
        assertEquals(
                RtcException.Kind.CANCELLED,
                ErrorCodes.classify(new CliResult(-1, "", "", false, true), "scm")
                        .kind());
    }

    @Test
    void detailsAreRedacted() {
        RtcException e = ErrorCodes.classify(
                new CliResult(9, "", "boom -P hunter2 password=abc", false), "scm login -P hunter2");
        assertFalse(e.details().contains("hunter2"), e.details());
        assertFalse(e.details().contains("abc"), e.details());
    }
}
