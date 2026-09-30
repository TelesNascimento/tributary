package dev.tributary.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliRunnerTest {

    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

    @TempDir
    Path dir;

    private Path script(String name, String windowsBody, String unixBody) throws Exception {
        Path script = dir.resolve(WINDOWS ? name + ".cmd" : name);
        Files.writeString(script, WINDOWS ? "@echo off\r\n" + windowsBody + "\r\n" : "#!/bin/sh\n" + unixBody + "\n");
        script.toFile().setExecutable(true);
        return script;
    }

    private Path fakeCli(String stdout, int exit) throws Exception {
        Path payload = dir.resolve("payload.txt");
        Files.writeString(payload, stdout, StandardCharsets.UTF_8);
        return script("scm", "type \"" + payload + "\"\r\nexit /b " + exit, "cat '" + payload + "'\nexit " + exit);
    }

    @Test
    void capturesStdoutAndExitCode() throws Exception {
        CliRunner runner = new CliRunner(fakeCli("{\"ok\":true}", 0), StandardCharsets.UTF_8);
        CliResult result = runner.run(dir, Duration.ofSeconds(20), "status", "-j");
        assertTrue(result.isSuccess());
        assertTrue(result.stdout().contains("\"ok\":true"));
    }

    @Test
    void reportsNonZeroExit() throws Exception {
        CliRunner runner = new CliRunner(fakeCli("error", 27), StandardCharsets.UTF_8);
        CliResult result = runner.run(dir, Duration.ofSeconds(20), "status");
        assertFalse(result.isSuccess());
        assertEquals(27, result.exitCode());
    }

    @Test
    void locatorPrefersConfiguredPathAndRejectsMissing() throws Exception {
        Path cli = fakeCli("", 0);
        assertEquals(Optional.of(cli), CliLocator.find(cli.toString()));
        assertTrue(CliLocator.find(dir.resolve("missing.exe").toString()).isEmpty());
        assertEquals(Optional.of(cli), CliLocator.find("", List.of(cli.toString())));
    }

    @Test
    void detectsSandboxByJazz5FromNestedFile() throws Exception {
        Path root = Files.createDirectories(dir.resolve("sandbox/app"));
        Files.createDirectories(root.resolve(".jazz5"));
        Path file = Files.createDirectories(root.resolve("src/main/java")).resolve("A.java");
        Files.writeString(file, "class A {}");
        assertEquals(Optional.of(root.toAbsolutePath().normalize()), SandboxDetector.findRoot(file));
        assertTrue(SandboxDetector.findRoot(dir.resolve("outside")).isEmpty());
    }

    @Test
    void cancelKillsTheProcessQuickly() throws Exception {
        Path slow = script("slow", "ping -n 20 127.0.0.1 >nul", "sleep 20");
        long start = System.nanoTime();
        long cancelAt = start + 400_000_000L;
        CliResult result = new CliRunner(slow, StandardCharsets.UTF_8)
                .run(CliRunner.Request.of(dir, Duration.ofSeconds(60), "status")
                        .withCancel(() -> System.nanoTime() > cancelAt));
        assertTrue(result.cancelled());
        assertFalse(result.isSuccess());
        assertTrue(System.nanoTime() - start < 5_000_000_000L);
    }

    @Test
    void runnerPrefixesEnglishAndNonInteractiveFlags() throws Exception {
        Path log = dir.resolve("args.log");
        Path echo = script("echoargs", "echo %* > \"" + log + "\"", "echo \"$@\" > '" + log + "'");
        new CliRunner(echo, StandardCharsets.UTF_8).run(dir, Duration.ofSeconds(20), "status", "-j");
        assertTrue(Files.readString(log).trim().startsWith("-nl en --non-interactive status -j"));
    }
}
