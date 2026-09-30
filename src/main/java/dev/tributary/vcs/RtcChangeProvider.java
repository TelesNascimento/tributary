package dev.tributary.vcs;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManagerGate;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.ChangelistBuilder;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.Model.ChangeSet;
import dev.tributary.cli.Model.Component;
import dev.tributary.cli.Model.Conflict;
import dev.tributary.cli.Model.PathChange;
import dev.tributary.cli.Model.Workspace;
import dev.tributary.cli.RtcException;
import dev.tributary.cli.SandboxDetector;
import dev.tributary.core.RtcProjectService;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public final class RtcChangeProvider implements ChangeProvider {

    private final Project project;

    public RtcChangeProvider(Project project) {
        this.project = project;
    }

    @Override
    public void getChanges(
            @NotNull VcsDirtyScope dirtyScope,
            @NotNull ChangelistBuilder builder,
            @NotNull ProgressIndicator progress,
            @NotNull ChangeListManagerGate addGate)
            throws VcsException {
        Set<Path> roots = new LinkedHashSet<>();
        for (VirtualFile root :
                ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(RtcVcsHolder.get(project))) {
            SandboxDetector.findRoot(root.toNioPath()).ifPresent(roots::add);
        }
        for (Path root : roots) {
            collect(root, builder, progress);
        }
    }

    private void collect(Path root, ChangelistBuilder builder, ProgressIndicator progress) throws VcsException {
        RtcProjectService.Snapshot snapshot =
                RtcProjectService.getInstance(project).refresh(root, true, progress::isCanceled);
        if (snapshot.failed()) {
            throw new VcsException(snapshot.error().getMessage(), snapshot.error());
        }
        RtcBackend client;
        try {
            client = RtcClients.forSandbox(root);
        } catch (RtcException e) {
            throw new VcsException(e.getMessage(), e);
        }
        boolean conflicted = false;
        for (Workspace workspace : snapshot.workspaces()) {
            for (Component component : workspace.components()) {
                for (PathChange change : component.unresolved()) {
                    report(root, change, client, builder);
                }
                conflicted |= component.outgoing().stream().anyMatch(ChangeSet::conflict);
            }
        }
        if (conflicted) {
            reportConflicts(root, client, builder);
        }
    }

    private void reportConflicts(Path root, RtcBackend client, ChangelistBuilder builder) throws VcsException {
        try {
            for (Conflict conflict : client.conflicts()) {
                FilePath filePath = VcsContextFactory.getInstance()
                        .createFilePath(root.resolve(conflict.path().replaceFirst("^/", "")), false);
                ContentRevision current = CurrentContentRevision.create(filePath);
                builder.processChange(
                        new Change(current, current, FileStatus.MERGED_WITH_CONFLICTS), RtcVcsHolder.key());
            }
        } catch (RtcException e) {
            throw new VcsException(e.getMessage(), e);
        }
    }

    private void report(Path root, PathChange change, RtcBackend client, ChangelistBuilder builder) {
        Path absolute = root.resolve(change.path().replaceFirst("^/", ""));
        FilePath filePath = VcsContextFactory.getInstance().createFilePath(absolute, false);
        if (change.added()) {
            if (RtcVcsHolder.get(project) instanceof RtcVcs vcs && vcs.isScheduledAdd(absolute)) {
                builder.processChange(new Change(null, CurrentContentRevision.create(filePath)), RtcVcsHolder.key());
            } else {
                builder.processUnversionedFile(filePath);
            }
            return;
        }
        RtcBaseRevision before = new RtcBaseRevision(filePath, client);
        if (change.deleted()) {
            builder.processChange(new Change(before, null), RtcVcsHolder.key());
        } else {
            builder.processChange(new Change(before, CurrentContentRevision.create(filePath)), RtcVcsHolder.key());
        }
    }

    @Override
    public boolean isModifiedDocumentTrackingRequired() {
        return true;
    }
}
