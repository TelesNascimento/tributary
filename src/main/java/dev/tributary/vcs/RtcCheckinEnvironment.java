package dev.tributary.vcs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import dev.tributary.TributaryBundle;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.CommitMessage;
import dev.tributary.cli.RtcException;
import dev.tributary.cli.SandboxDetector;
import dev.tributary.context.ContextService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RtcCheckinEnvironment implements CheckinEnvironment {

    private final RtcVcs vcs;

    public RtcCheckinEnvironment(RtcVcs vcs) {
        this.vcs = vcs;
    }

    @Override
    public @Nullable String getHelpId() {
        return null;
    }

    @Override
    public @NotNull String getCheckinOperationName() {
        return TributaryBundle.message("checkin.operation");
    }

    @Override
    public @Nullable List<VcsException> commit(
            @NotNull List<? extends Change> changes,
            @NotNull String commitMessage,
            @NotNull com.intellij.openapi.vcs.changes.CommitContext commitContext,
            @NotNull Set<? super String> feedback) {
        List<VcsException> errors = new ArrayList<>();
        CommitMessage.Parsed parsed = CommitMessage.parse(commitMessage);
        Map<Path, List<Path>> bySandbox = new LinkedHashMap<>();
        Map<Path, Boolean> hasDeleted = new LinkedHashMap<>();
        for (Change change : changes) {
            ContentRevision revision =
                    change.getAfterRevision() != null ? change.getAfterRevision() : change.getBeforeRevision();
            if (revision == null) {
                continue;
            }
            Path file = revision.getFile().getIOFile().toPath();
            SandboxDetector.findRoot(file).ifPresent(root -> {
                bySandbox.computeIfAbsent(root, k -> new ArrayList<>()).add(file);
                if (change.getAfterRevision() == null) {
                    hasDeleted.put(root, true);
                }
            });
        }
        for (Map.Entry<Path, List<Path>> entry : bySandbox.entrySet()) {
            try {
                boolean deleted = hasDeleted.containsKey(entry.getKey());
                ContextService contexts = ContextService.getInstance(vcs.getProject());
                if (contexts.active().isPresent()) {
                    contexts.checkIn(entry.getValue(), commitMessage, deleted);
                } else {
                    RtcBackend client = RtcClients.forSandbox(entry.getKey());
                    client.checkin(entry.getValue(), parsed.comment(), parsed.workItem(), deleted);
                }
                entry.getValue().forEach(vcs::unscheduleAdd);
            } catch (RtcException e) {
                errors.add(new VcsException(e.getMessage(), e));
            }
        }
        return errors;
    }

    @Override
    public @Nullable List<VcsException> scheduleUnversionedFilesForAddition(
            @NotNull List<? extends VirtualFile> files) {
        files.forEach(f -> vcs.scheduleAdd(f.toNioPath()));
        return new ArrayList<>();
    }

    @Override
    public @Nullable List<VcsException> scheduleMissingFileForDeletion(@NotNull List<? extends FilePath> files) {
        return new ArrayList<>();
    }

    @Override
    public boolean isRefreshAfterCommitNeeded() {
        return true;
    }

    public static Path toPath(FilePath filePath) {
        return filePath.getIOFile().toPath();
    }
}
