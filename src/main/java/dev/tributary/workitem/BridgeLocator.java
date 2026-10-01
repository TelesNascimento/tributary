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
        return findJava8(configured, extraRoots, JAVA_ROOTS, System.getenv("JAVA_HOME"), System.getenv("PATH"));
    }

    static Optional<Path> findJava8(
            String configured, List<String> extraRoots, List<String> systemRoots, String javaHome, String pathEnv) {
        if (configured != null && !configured.isBlank()) {
            return javaExecutable(Path.of(configured.trim()));
        }
        if (javaHome != null && !javaHome.isBlank()) {
            Path home = Path.of(javaHome.trim());
            if (isJava8Home(home)) {
                Optional<Path> exe = javaExecutable(home);
                if (exe.isPresent()) {
                    return exe;
                }
            }
        }
        Optional<Path> installed = findInRoots(extraRoots, systemRoots);
        return installed.isPresent() ? installed : findOnPath(pathEnv);
    }

    private static Optional<Path> findOnPath(String pathEnv) {
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        for (String entry : pathEnv.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                Path bin = Path.of(entry.trim());
                Path home = bin.getParent();
                if (home != null && isJava8Home(home)) {
                    Optional<Path> exe = javaExecutable(home);
                    if (exe.isPresent()) {
                        return exe;
                    }
                }
            } catch (java.nio.file.InvalidPathException ignored) {
                continue;
            }
        }
        return Optional.empty();
    }

    static boolean isJava8Home(Path home) {
        if (declaresJava8(home)) {
            return true;
        }
        Path parent = home.getParent();
        return parent != null
                && home.getFileName() != null
                && "jre".equalsIgnoreCase(home.getFileName().toString())
                && declaresJava8(parent);
    }

    private static boolean declaresJava8(Path home) {
        Path release = home.resolve("release");
        if (Files.isRegularFile(release)) {
            try {
                for (String line : Files.readAllLines(release)) {
                    if (line.startsWith("JAVA_VERSION")) {
                        return line.contains("\"1.8");
                    }
                }
            } catch (IOException ignored) {
                return false;
            }
        }
        return home.getFileName() != null
                && JDK8_NAME.matcher(home.getFileName().toString()).matches();
    }

    private static Optional<Path> findInRoots(List<String> extraRoots, List<String> systemRoots) {
        List<String> roots = new ArrayList<>(extraRoots);
        roots.addAll(systemRoots);
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
