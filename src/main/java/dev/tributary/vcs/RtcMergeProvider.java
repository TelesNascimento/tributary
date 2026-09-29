package dev.tributary.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import dev.tributary.TributaryBundle;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.Model.ChangeSet;
import dev.tributary.cli.Model.Component;
import dev.tributary.cli.Model.ConflictSide;
import dev.tributary.cli.Model.Resolution;
import dev.tributary.cli.Model.Workspace;
import dev.tributary.cli.RtcException;
import dev.tributary.cli.SandboxDetector;
import dev.tributary.core.RtcProjectService;
import dev.tributary.ui.common.TributaryNotifier;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public final class RtcMergeProvider implements MergeProvider {

    private final Project project;

    public RtcMergeProvider(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull MergeData loadRevisions(@NotNull VirtualFile file) throws VcsException {
        try {
            Path path = file.toNioPath();
            RtcBackend backend = backend(path);
            Charset charset = file.getCharset();
            MergeData data = new MergeData();
            data.ORIGINAL = backend.conflictContent(path, ConflictSide.ANCESTOR).getBytes(charset);
            data.CURRENT = backend.conflictContent(path, ConflictSide.MINE).getBytes(charset);
            data.LAST = backend.conflictContent(path, ConflictSide.PROPOSED).getBytes(charset);
            return data;
        } catch (RtcException e) {
            throw new VcsException(e.getMessage(), e);
        }
    }

    @Override
    public void conflictResolvedForFile(@NotNull VirtualFile file) {
        Path path = file.toNioPath();
        try {
            RtcBackend backend = backend(path);
            String changeSet = currentChangeSet(path)
                    .orElseThrow(() ->
                            new RtcException(RtcException.Kind.FAILED, TributaryBundle.message("merge.no.changeset")));
            backend.checkinTo(changeSet, List.of(path), false);
            backend.resolveConflict(path, Resolution.CHECKED_IN);
        } catch (RtcException e) {
            TributaryNotifier.error(project, TributaryBundle.message("merge.failed"), e);
        }
    }

    @Override
    public boolean isBinary(@NotNull VirtualFile file) {
        return file.getFileType().isBinary();
    }

    private RtcBackend backend(Path file) throws RtcException {
        Path sandbox = SandboxDetector.findRoot(file)
                .orElseThrow(() ->
                        new RtcException(RtcException.Kind.NOT_A_SANDBOX, TributaryBundle.message("error.no.sandbox")));
        return RtcClients.forSandbox(sandbox);
    }

    private Optional<String> currentChangeSet(Path file) {
        Optional<Path> sandbox = SandboxDetector.findRoot(file);
        if (sandbox.isEmpty()) {
            return Optional.empty();
        }
        var snapshot = RtcProjectService.getInstance(project).refresh(sandbox.get(), true, () -> false);
        if (snapshot.failed()) {
            return Optional.empty();
        }
        ChangeSet fallback = null;
        for (Workspace workspace : snapshot.workspaces()) {
            for (Component component : workspace.components()) {
                for (ChangeSet changeSet : component.outgoing()) {
                    if (changeSet.current()) {
                        return Optional.of(changeSet.uuid());
                    }
                    fallback = fallback == null ? changeSet : fallback;
                }
            }
        }
        return Optional.ofNullable(fallback).map(ChangeSet::uuid);
    }
}
