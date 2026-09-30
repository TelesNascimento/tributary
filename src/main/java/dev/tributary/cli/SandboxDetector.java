package dev.tributary.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class SandboxDetector {

    public static final String METADATA_DIR = ".jazz5";

    private SandboxDetector() {}

    public static Optional<Path> findRoot(Path start) {
        Path current = start == null ? null : start.toAbsolutePath().normalize();
        if (current != null && !Files.isDirectory(current)) {
            current = current.getParent();
        }
        while (current != null) {
            if (isSandboxRoot(current)) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    public static boolean isSandboxRoot(Path dir) {
        return dir != null && Files.isDirectory(dir.resolve(METADATA_DIR));
    }
}
