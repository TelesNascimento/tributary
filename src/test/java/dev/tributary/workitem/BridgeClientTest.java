package dev.tributary.workitem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.tributary.cli.RtcException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeClientTest {

    @TempDir
    Path dir;

    private BridgeProcess process;

    private BridgeProcess startFake() throws Exception {
        Path source = dir.resolve("FakeBridge.java");
        try (var in = getClass().getResourceAsStream("/fake-bridge/FakeBridge.java")) {
            Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING);
        }
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path executable =
                Files.isRegularFile(java) ? java : Path.of(System.getProperty("java.home"), "bin", "java.exe");
        process = BridgeProcess.start(List.of(executable.toString(), source.toString()));
        return process;
    }

    @AfterEach
    void stop() {
        if (process != null) {
            process.close();
        }
    }

    @Test
    void searchParsesWorkItems() throws Exception {
        WorkItemDirectory directory = new BridgeWorkItems((method, params) -> {
            try {
                return startFake().call(method, params, Duration.ofSeconds(30));
            } catch (RtcException e) {
                throw e;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        List<WorkItem> items = directory.search("sample", true, false, 10);
        assertEquals(1, items.size());
        assertEquals(9000001, items.get(0).id());
        assertEquals("Sample work item", items.get(0).summary());
        assertFalse(items.get(0).resolved());
        assertEquals("9000001: Sample work item", items.get(0).label());
    }

    @Test
    void unknownWorkItemIsEmptyNotAnError() throws Exception {
        BridgeProcess bridge = startFake();
        WorkItemDirectory directory =
                new BridgeWorkItems((method, params) -> bridge.call(method, params, Duration.ofSeconds(30)));
        assertEquals(Optional.empty(), directory.get(404));
    }

    @Test
    void remoteErrorKindIsPreserved() throws Exception {
        BridgeProcess bridge = startFake();
        RtcException e = assertThrows(
                RtcException.class, () -> bridge.call("whatever", new JsonObject(), Duration.ofSeconds(30)));
        assertEquals(RtcException.Kind.AUTH_REQUIRED, e.kind());
    }

    @Test
    void aBridgeThatNeverAnswersTimesOut() throws Exception {
        BridgeProcess bridge = startFake();
        RtcException e =
                assertThrows(RtcException.class, () -> bridge.call("hang", new JsonObject(), Duration.ofMillis(500)));
        assertEquals(RtcException.Kind.TIMEOUT, e.kind());
    }

    @Test
    void aCrashedBridgeIsReportedAndMarkedDead() throws Exception {
        BridgeProcess bridge = startFake();
        assertThrows(RtcException.class, () -> bridge.call("crash", new JsonObject(), Duration.ofSeconds(10)));
        assertFalse(bridge.isAlive());
    }

    @Test
    void locatorFindsJava8ByFolderNameAndIgnoresNewerJdks() throws IOException {
        Path root = Files.createDirectories(dir.resolve("java-root"));
        for (String name : List.of("jdk-25.0.2", "jdk-8.0.482.8-hotspot", "jdk1.8.0_202")) {
            Path bin = Files.createDirectories(root.resolve(name).resolve("bin"));
            Files.writeString(
                    bin.resolve(System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"), "");
        }
        Optional<Path> java = BridgeLocator.findJava8("", List.of(root.toString()));
        assertTrue(java.isPresent());
        String home = java.get().getParent().getParent().getFileName().toString();
        assertTrue(home.equals("jdk-8.0.482.8-hotspot") || home.equals("jdk1.8.0_202"), home);
    }

    @Test
    void launchBuildsTheClasspathFromTheClasspathFile() throws IOException {
        Path bridge = Files.createDirectories(dir.resolve("bridge"));
        Path jar = Files.writeString(bridge.resolve("tributary-bridge.jar"), "x");
        Files.writeString(bridge.resolve("classpath.txt"), "a.jar\nsub\\b.jar".replace('\\', '/') + "\n");
        Path plugins = Files.createDirectories(dir.resolve("plugins"));
        Files.writeString(plugins.resolve("a.jar"), "x");
        Files.createDirectories(plugins.resolve("sub"));
        Files.writeString(plugins.resolve("sub").resolve("b.jar"), "x");
        var launch = BridgeLocator.launch(Path.of("java"), jar, plugins).orElseThrow();
        String classpath = launch.command().get(2);
        assertTrue(classpath.startsWith(jar.toString()), classpath);
        assertTrue(classpath.contains("a.jar") && classpath.contains("b.jar"), classpath);
        assertEquals("dev.tributary.bridge.Main", launch.command().get(3));
    }

    @Test
    void launchIsRejectedWhenALibraryIsMissing() throws IOException {
        Path bridge = Files.createDirectories(dir.resolve("bridge"));
        Path jar = Files.writeString(bridge.resolve("tributary-bridge.jar"), "x");
        Files.writeString(bridge.resolve("classpath.txt"), "missing.jar\n");
        Path plugins = Files.createDirectories(dir.resolve("plugins"));
        assertTrue(BridgeLocator.launch(Path.of("java"), jar, plugins).isEmpty());
    }
}
