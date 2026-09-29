package dev.tributary.ignore;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor;
import com.intellij.openapi.vcs.changes.IgnoredFileProvider;
import dev.tributary.TributaryBundle;
import dev.tributary.cli.SandboxDetector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public final class RtcIgnoredFileProvider implements IgnoredFileProvider {

    @Override
    public boolean isIgnoredFile(@NotNull Project project, @NotNull FilePath filePath) {
        Path file = filePath.getIOFile().toPath();
        return SandboxDetector.findRoot(file)
                .map(root -> JazzIgnore.isIgnored(root, file, RtcIgnoredFileProvider::read))
                .orElse(false);
    }

    @Override
    public @NotNull Set<IgnoredFileDescriptor> getIgnoredFiles(@NotNull Project project) {
        return Set.of();
    }

    @Override
    public @NotNull String getIgnoredGroupDescription() {
        return TributaryBundle.message("ignore.group.description");
    }

    private static Optional<String> read(Path path) {
        try {
            return Files.isRegularFile(path) ? Optional.of(Files.readString(path)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
