package dev.tributary.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class CliLocator {

    private static final List<String> DEFAULTS = List.of(
            "C:/Program Files/IBM/TeamConcert/scmtools/eclipse/scm.exe",
            "C:/Program Files (x86)/IBM/TeamConcert/scmtools/eclipse/scm.exe",
            "C:/Program Files/IBM/SDP/scmtools/eclipse/scm.exe");

    private CliLocator() {}

    public static Optional<Path> find(String configured, List<String> extraCandidates) {
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured.trim());
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        return Stream.concat(extraCandidates.stream(), DEFAULTS.stream())
                .map(Path::of)
                .filter(Files::isRegularFile)
                .findFirst();
    }

    public static Optional<Path> find(String configured) {
        return find(configured, List.of());
    }
}
