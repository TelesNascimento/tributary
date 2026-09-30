package dev.tributary.workitem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class BridgeLocator {

    private static final Pattern JDK8_NAME = Pattern.compile("(?i).*(jdk|jre)[-_]?(1\\.)?8([._-].*)?");
    private static final List<String> JAVA_ROOTS = List.of(
            "C:/Program Files/Eclipse Adoptium",
            "C:/Program Files/Java",
            "C:/Program Files/Zulu",
            "C:/Program Files/Amazon Corretto",
            "C:/Program Files/Microsoft");
    private static final List<String> IBM_PLUGIN_DIRS =
            List.of("C:/Program Files/IBM/IBMIMShared/plugins", "C:/Program Files (x86)/IBM/IBMIMShared/plugins");

    private BridgeLocator() {}

    public record Launch(List<String> command) {}

    public static Optional<Path> findJava8(String configured, List<String> extraRoots) {
        if (configured != null && !configured.isBlank()) {
            return javaExecutable(Path.of(configured.trim()));
        }
        List<String> roots = new ArrayList<>(extraRoots);
        roots.addAll(JAVA_ROOTS);
        for (String root : roots) {
            Path base = Path.of(root);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> children = Files.list(base)) {
                Optional<Path> found = children.filter(p ->
                                JDK8_NAME.matcher(p.getFileName().toString()).matches())
                        .sorted()
                        .map(BridgeLocator::javaExecutable)
                        .flatMap(Optional::stream)
                        .findFirst();
                if (found.isPresent()) {
                    return found;
                }
            } catch (IOException ignored) {
                continue;
            }
        }
        return Optional.empty();
    }

    public static Optional<Path> findIbmPlugins(String configured, List<String> extraCandidates) {
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured.trim());
            return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
        }
        return Stream.concat(extraCandidates.stream(), IBM_PLUGIN_DIRS.stream())
                .map(Path::of)
                .filter(Files::isDirectory)
                .findFirst();
    }

    public static Optional<Launch> launch(Path java, Path bridgeJar, Path ibmPlugins) throws IOException {
        Path classpathFile = bridgeJar.resolveSibling("classpath.txt");
        if (!Files.isRegularFile(bridgeJar) || !Files.isRegularFile(classpathFile)) {
            return Optional.empty();
        }
        List<String> entries = new ArrayList<>();
        entries.add(bridgeJar.toString());
        for (String name : Files.readAllLines(classpathFile)) {
            if (!name.isBlank()) {
                Path jar = ibmPlugins.resolve(name.trim());
                if (!Files.isRegularFile(jar)) {
                    return Optional.empty();
                }
                entries.add(jar.toString());
            }
        }
        return Optional.of(new Launch(List.of(
                java.toString(), "-cp", String.join(File.pathSeparator, entries), "dev.tributary.bridge.Main")));
    }

    private static Optional<Path> javaExecutable(Path home) {
        Path exe = home.resolve("bin")
                .resolve(System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        return Files.isRegularFile(exe) ? Optional.of(exe) : Optional.empty();
    }
}
