package dev.tributary.vcs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VirtualFile;
import dev.tributary.TributaryBundle;
import dev.tributary.cli.RtcException;
import dev.tributary.cli.SandboxDetector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class RtcRollbackEnvironment implements RollbackEnvironment {

    private final RtcVcs vcs;

    public RtcRollbackEnvironment(RtcVcs vcs) {
        this.vcs = vcs;
    }

    @Override
    public @NotNull String getRollbackOperationName() {
        return TributaryBundle.message("rollback.operation");
    }

    @Override
    public void rollbackChanges(
            @NotNull List<? extends Change> changes,
            @NotNull List<VcsException> exceptions,
            @NotNull RollbackProgressListener listener) {
        Map<Path, List<Path>> bySandbox = new LinkedHashMap<>();
        for (Change change : changes) {
            ContentRevision before = change.getBeforeRevision();
            ContentRevision after = change.getAfterRevision();
            if (before == null && after != null) {
                vcs.unscheduleAdd(after.getFile().getIOFile().toPath());
                continue;
            }
            ContentRevision revision = before != null ? before : after;
            if (revision != null) {
                Path file = revision.getFile().getIOFile().toPath();
                SandboxDetector.findRoot(file)
                        .ifPresent(root -> bySandbox
                                .computeIfAbsent(root, k -> new ArrayList<>())
                                .add(file));
            }
        }
        for (Map.Entry<Path, List<Path>> entry : bySandbox.entrySet()) {
            try {
                RtcClients.forSandbox(entry.getKey()).undo(entry.getValue());
            } catch (RtcException e) {
                exceptions.add(new VcsException(e.getMessage(), e));
            }
        }
    }

    @Override
    public void rollbackMissingFileDeletion(
            @NotNull List<? extends FilePath> files,
            @NotNull List<? super VcsException> exceptions,
            @NotNull RollbackProgressListener listener) {
        List<Path> paths = files.stream().map(f -> f.getIOFile().toPath()).toList();
        Map<Path, List<Path>> bySandbox = new LinkedHashMap<>();
        for (Path file : paths) {
            SandboxDetector.findRoot(file)
                    .ifPresent(root -> bySandbox
                            .computeIfAbsent(root, k -> new ArrayList<>())
                            .add(file));
        }
        for (Map.Entry<Path, List<Path>> entry : bySandbox.entrySet()) {
            try {
                RtcClients.forSandbox(entry.getKey()).undo(entry.getValue());
            } catch (RtcException e) {
                exceptions.add(new VcsException(e.getMessage(), e));
            }
        }
    }

    @Override
    public void rollbackModifiedWithoutCheckout(
            @NotNull List<? extends VirtualFile> files,
            @NotNull List<? super VcsException> exceptions,
            @NotNull RollbackProgressListener listener) {}

    @Override
    public void rollbackIfUnchanged(@NotNull VirtualFile file) {}
}
