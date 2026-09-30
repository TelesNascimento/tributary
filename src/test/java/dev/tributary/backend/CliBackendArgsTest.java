package dev.tributary.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tributary.cli.CliRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliBackendArgsTest {

    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

    @TempDir
    Path dir;

    private CliBackend backend(String stdout) throws Exception {
        Path payload = dir.resolve("payload.txt");
        Files.writeString(payload, stdout, StandardCharsets.UTF_8);
        Path log = dir.resolve("args.log");
        Path script = dir.resolve(WINDOWS ? "scm.cmd" : "scm");
        String body = WINDOWS
                ? "@echo off\r\necho %* >> \"" + log + "\"\r\ntype \"" + payload + "\"\r\n"
                : "#!/bin/sh\necho \"$@\" >> '" + log + "'\ncat '" + payload + "'\n";
        Files.writeString(script, body);
        script.toFile().setExecutable(true);
        Files.createDirectories(dir.resolve("sb/.jazz5"));
        return new CliBackend(new CliRunner(script, StandardCharsets.UTF_8), dir.resolve("sb"));
    }

    private List<String> calls() throws IOException {
        return Files.readAllLines(dir.resolve("args.log")).stream()
                .map(l -> l.replace("\"", "").trim())
                .toList();
    }

    private String lastCall() throws IOException {
        List<String> calls = calls();
        return calls.get(calls.size() - 1);
    }

    private static String fixture(String name) throws IOException {
        try (var in = CliBackendArgsTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void checkinBuildsArgumentsWithCommentAndWorkItem() throws Exception {
        backend("ok").checkin(List.of(Path.of("a.java")), "line1\nline2", "9000002", false);
        String call = lastCall();
        assertTrue(call.startsWith("-nl en --non-interactive checkin -d"), call);
        assertTrue(call.contains("--comment line1 line2"), call);
        assertTrue(call.contains("-W 9000002"), call);
        assertTrue(call.endsWith("a.java"), call);
    }

    @Test
    void checkinToTargetsAnExistingChangeSetAndIncludesDeletions() throws Exception {
        backend("ok").checkinTo("_cs1", List.of(Path.of("a.java")), true);
        String call = lastCall();
        assertTrue(call.contains("checkin -d"), call);
        assertTrue(call.contains("-c _cs1 -D"), call);
    }

    @Test
    void createChangeSetReturnsTheUuidFromJson() throws Exception {
        String uuid = backend("{\"uuid\": \"_abcdefghijklmnopqrstuv\"}").createChangeSet("9000001: card", true);
        assertEquals("_abcdefghijklmnopqrstuv", uuid);
        assertTrue(lastCall().contains("create changeset -j -d"), lastCall());
        assertTrue(lastCall().endsWith("9000001: card"), lastCall());
    }

    @Test
    void createChangeSetWithoutCurrentPassesNoCurrentFlag() throws Exception {
        backend("{\"uuid\": \"_abcdefghijklmnopqrstuv\"}").createChangeSet("x", false);
        assertTrue(lastCall().contains(" -n "), lastCall());
    }

    @Test
    void changeSetAttributeCommands() throws Exception {
        CliBackend backend = backend("ok");
        backend.setCurrentChangeSet("_cs1");
        assertTrue(lastCall().contains("set changeset --current -d"), lastCall());
        backend.setChangeSetComment("_cs1", "new text");
        assertTrue(lastCall().contains("set changeset --comment new text -d"), lastCall());
        backend.completeChangeSet("_cs1");
        assertTrue(lastCall().contains("set changeset --complete -d"), lastCall());
    }

    @Test
    void workItemAssociation() throws Exception {
        CliBackend backend = backend("ok");
        backend.addWorkItem("_ws", "_cs1", 9000001);
        assertTrue(lastCall().endsWith("add workitem -w _ws _cs1 9000001"), lastCall());
        backend.removeWorkItem("_ws", "_cs1", 9000001);
        assertTrue(lastCall().endsWith("remove workitem -w _ws _cs1 9000001"), lastCall());
    }

    @Test
    void suspendResumeDiscard() throws Exception {
        CliBackend backend = backend("ok");
        backend.suspend(List.of("_a", "_b"));
        assertTrue(lastCall().contains("suspend -d") && lastCall().endsWith("_a _b"), lastCall());
        backend.resume(List.of("_a"));
        assertTrue(lastCall().contains("resume changeset -d") && lastCall().endsWith("_a"), lastCall());
        backend.discard("_ws", List.of("_a"));
        assertTrue(lastCall().endsWith("discard -w _ws _a"), lastCall());
    }

    @Test
    void deliverAllOrSelected() throws Exception {
        CliBackend backend = backend("ok");
        backend.deliver(List.of());
        assertTrue(lastCall().endsWith("--non-interactive deliver"), lastCall());
        backend.deliver(List.of("_a", "_b"));
        assertTrue(lastCall().endsWith("deliver -c _a _b"), lastCall());
    }

    @Test
    void acceptAllComponentOrSelected() throws Exception {
        CliBackend backend = backend("ok");
        backend.accept(List.of(), null);
        assertTrue(
                lastCall().contains("accept -d")
                        && !lastCall().contains(" -c ")
                        && !lastCall().contains(" -C "),
                lastCall());
        backend.accept(List.of(), "Comp");
        assertTrue(lastCall().endsWith("-C Comp"), lastCall());
        backend.accept(List.of("_a"), "Comp");
        assertTrue(lastCall().endsWith("-c _a"), lastCall());
    }

    @Test
    void undoFiles() throws Exception {
        backend("ok").undo(List.of(Path.of("x.java")));
        assertTrue(lastCall().endsWith("undo change x.java"), lastCall());
    }

    @Test
    void readsListsFromRealFixtures() throws Exception {
        assertEquals(
                5,
                backend(fixture("list_workspaces.json"))
                        .listWorkspaces("https://x/ccm/", 10)
                        .size());
        assertEquals(
                List.of("P100_sample_service"),
                backend(fixture("list_components.json")).listComponents("https://x/ccm/", "uuid"));
        assertEquals(
                3,
                backend(fixture("list_changesets.json"))
                        .listChangeSets("_ws", 3)
                        .size());
    }

    @Test
    void loginSendsThePasswordOnStdinNeverOnTheCommandLine() throws Exception {
        Path argsLog = dir.resolve("login-args.log");
        Path stdinLog = dir.resolve("login-stdin.log");
        Path script = dir.resolve(WINDOWS ? "scm.cmd" : "scm");
        Files.writeString(
                script,
                WINDOWS
                        ? "@echo off\r\necho %* > \"" + argsLog + "\"\r\nset /p PW=\r\necho %PW%> \"" + stdinLog
                                + "\"\r\n"
                        : "#!/bin/sh\necho \"$@\" > '" + argsLog + "'\nread PW\necho \"$PW\" > '" + stdinLog + "'\n");
        script.toFile().setExecutable(true);
        Files.createDirectories(dir.resolve("sb/.jazz5"));
        CliBackend backend = new CliBackend(new CliRunner(script, StandardCharsets.UTF_8), dir.resolve("sb"));
        backend.login("https://rtc.example.com/ccm/", "U000001", "nick", "s3cret-pw".toCharArray());
        String args = Files.readString(argsLog);
        assertTrue(args.contains("--no-mask login -r https://rtc.example.com/ccm/ -u U000001 -c -n nick"), args);
        assertTrue(!args.contains("s3cret-pw") && !args.contains("--non-interactive") && !args.contains("-P "), args);
        assertEquals("s3cret-pw", Files.readString(stdinLog).trim());
    }

    @Test
    void importsConnectionsFromTheCommandLineCache() throws Exception {
        var connections = backend(fixture("list_connections.json")).listConnections();
        assertEquals(1, connections.size());
        assertEquals("https://rtc.example.com/ccm/", connections.get(0).uri());
        assertTrue(connections.get(0).passwordStored());
    }

    @Test
    void conflictOperations() throws Exception {
        var conflicts = backend(fixture("show_conflicts.json")).conflicts();
        assertEquals(1, conflicts.size());
        assertTrue(lastCall().endsWith("show conflicts -j"), lastCall());
        String content = backend("Content:\n  first\nsecond\n\n")
                .conflictContent(Path.of("a.java"), dev.tributary.cli.Model.ConflictSide.MINE);
        assertEquals("first\nsecond\n", content);
        assertTrue(lastCall().endsWith("show conflicts --content -m a.java"), lastCall());
        backend("ok").resolveConflict(Path.of("a.java"), dev.tributary.cli.Model.Resolution.CHECKED_IN);
        assertTrue(lastCall().contains("resolve conflict -c -d") && lastCall().endsWith("a.java"), lastCall());
    }

    @Test
    void createWorkspaceReadsTheUuidFromJson() throws Exception {
        var created = backend("{\"uuid\": \"_abcdefghijklmnopqrstuv\"}")
                .createWorkspace("https://x/ccm/", "_stream", "dev_orders");
        assertEquals("_abcdefghijklmnopqrstuv", created.uuid());
        assertEquals("dev_orders", created.name());
        assertTrue(lastCall().endsWith("create workspace -r https://x/ccm/ -j -s _stream dev_orders"), lastCall());
    }

    @Test
    void createWorkspaceFallsBackToTheListWhenTheOutputHasNoUuid() throws Exception {
        Path list = dir.resolve("list.json");
        Files.writeString(
                list,
                "[{\"name\":\"other\",\"uuid\":\"_aaaaaaaaaaaaaaaaaaaaaa\"},"
                        + "{\"name\":\"dev_orders\",\"uuid\":\"_bbbbbbbbbbbbbbbbbbbbbb\"}]");
        Path script = dir.resolve(WINDOWS ? "scm.cmd" : "scm");
        Files.writeString(
                script,
                WINDOWS
                        ? "@echo off\r\necho %* | findstr /C:\"list workspaces\" >nul\r\n"
                                + "if %errorlevel%==0 (type \"" + list + "\") else (echo created)\r\n"
                        : "#!/bin/sh\ncase \"$*\" in *'list workspaces'*) cat '" + list
                                + "';; *) echo created;; esac\n");
        script.toFile().setExecutable(true);
        Files.createDirectories(dir.resolve("sb/.jazz5"));
        CliBackend backend = new CliBackend(new CliRunner(script, StandardCharsets.UTF_8), dir.resolve("sb"));
        var created = backend.createWorkspace("https://x/ccm/", "_stream", "dev_orders");
        assertEquals("_bbbbbbbbbbbbbbbbbbbbbb", created.uuid());
    }

    @Test
    void loadAndUnload() throws Exception {
        CliBackend backend = backend("ok");
        backend.load("https://x/ccm/", dir.resolve("dest"), "_uuid", List.of("CompA", "CompB"));
        assertTrue(lastCall().endsWith("_uuid CompA CompB"), lastCall());
        backend.unload("_uuid", true);
        assertTrue(lastCall().contains("unload -w _uuid -d") && lastCall().endsWith("-D"), lastCall());
    }
}
