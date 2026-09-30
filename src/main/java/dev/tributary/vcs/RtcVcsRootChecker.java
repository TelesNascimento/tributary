package dev.tributary.vcs;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import dev.tributary.cli.SandboxDetector;
import org.jetbrains.annotations.NotNull;

public final class RtcVcsRootChecker extends VcsRootChecker {

    @Override
    public boolean isRoot(@NotNull VirtualFile file) {
        return file.isDirectory() && file.findChild(SandboxDetector.METADATA_DIR) != null;
    }

    @Override
    public @NotNull VcsKey getSupportedVcs() {
        return RtcVcs.KEY;
    }

    @Override
    public boolean isVcsDir(@NotNull String path) {
        return path.endsWith("/" + SandboxDetector.METADATA_DIR) || path.equals(SandboxDetector.METADATA_DIR);
    }
}
