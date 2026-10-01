package dev.tributary.workitem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeLocatorTest {

    private static final String EXE =
            System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";

    @TempDir
    Path dir;

    private Path fakeJava(String folder, String version) throws IOException {
        Path home = dir.resolve(folder);
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("bin").resolve(EXE), "");
        if (version != null) {
            Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\n");
        }
        return home;
    }

    @Test
    void usesTheConfiguredFolderFirst() throws IOException {
        Path home = fakeJava("chosen", "17.0.1");
        Optional<Path> found = BridgeLocator.findJava8(home.toString(), List.of(), List.of(), null, null);
        assertEquals(home.resolve("bin").resolve(EXE), found.orElseThrow());
    }

    @Test
    void usesJavaHomeWhenItIsJava8() throws IOException {
        Path home = fakeJava("corretto", "1.8.0_492");
        Optional<Path> found = BridgeLocator.findJava8("", List.of(), List.of(), home.toString(), null);
        assertEquals(home.resolve("bin").resolve(EXE), found.orElseThrow());
    }

    @Test
    void ignoresJavaHomeWhenItIsNotJava8() throws IOException {
        Path home = fakeJava("jdk25", "25.0.2");
        assertTrue(BridgeLocator.findJava8("", List.of(), List.of(), home.toString(), null)
                .isEmpty());
    }

    @Test
    void findsJava8OnThePath() throws IOException {
        Path home = fakeJava("somewhere", "1.8.0_492");
        Path other = fakeJava("elsewhere", "21.0.1");
        String path = other.resolve("bin") + File.pathSeparator + home.resolve("bin");
        Optional<Path> found = BridgeLocator.findJava8("", List.of(), List.of(), null, path);
        assertEquals(home.resolve("bin").resolve(EXE), found.orElseThrow());
    }

    @Test
    void recognizesJava8ByFolderNameWhenThereIsNoReleaseFile() throws IOException {
        Path home = fakeJava("jdk1.8.0_492", null);
        assertTrue(BridgeLocator.isJava8Home(home));
        assertFalse(BridgeLocator.isJava8Home(fakeJava("jdk-21", null)));
    }

    @Test
    void recognizesAJreFolderInsideAJava8Jdk() throws IOException {
        Path jdk = fakeJava("jdk1.8.0_492", "1.8.0_492");
        Path jre = jdk.resolve("jre");
        Files.createDirectories(jre.resolve("bin"));
        assertTrue(BridgeLocator.isJava8Home(jre));
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        assertTrue(BridgeLocator.findJava8("", List.of(), List.of(), null, "").isEmpty());
    }
}
