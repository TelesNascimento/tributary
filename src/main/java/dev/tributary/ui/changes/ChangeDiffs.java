package dev.tributary.ui.changes;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import dev.tributary.TributaryBundle;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.Model.PathChange;
import dev.tributary.cli.RtcException;
import dev.tributary.ui.common.TributaryNotifier;
import dev.tributary.ui.common.TributaryTasks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ChangeDiffs {

    private ChangeDiffs() {}

    public static void show(Project project, RtcBackend backend, PathChange change) {
        TributaryTasks.run(
                project,
                TributaryBundle.message("diff.loading", change.path()),
                true,
                cancelled -> load(backend, change),
                contents -> open(project, change, contents[0], contents[1]),
                failure -> TributaryNotifier.error(project, TributaryBundle.message("diff.failed"), failure));
    }

    private static String[] load(RtcBackend backend, PathChange change) throws RtcException {
        if (change.stateId() == null) {
            return unresolved(backend, change);
        }
        return new String[] {before(backend, change), after(backend, change)};
    }

    private static String[] unresolved(RtcBackend backend, PathChange change) throws RtcException {
        Path file = backend.sandboxRoot().resolve(change.path().replaceFirst("^/", ""));
        String before = change.added() ? "" : backend.baseContent(file);
        String after = "";
        if (!change.deleted()) {
            try {
                after = Files.readString(file);
            } catch (IOException e) {
                throw new RtcException(
                        RtcException.Kind.FAILED, TributaryBundle.message("error.read.file", file, e.getMessage()));
            }
        }
        return new String[] {before, after};
    }

    private static String before(RtcBackend backend, PathChange change) throws RtcException {
        if (change.added() || change.beforeStateId() == null || change.uuid() == null) {
            return "";
        }
        return backend.fileContent(change.uuid(), change.beforeStateId());
    }

    private static String after(RtcBackend backend, PathChange change) throws RtcException {
        if (change.deleted() || change.uuid() == null) {
            return "";
        }
        return backend.fileContent(change.uuid(), change.stateId());
    }

    private static void open(Project project, PathChange change, String before, String after) {
        String name = change.path().substring(change.path().lastIndexOf('/') + 1);
        FileType type = FileTypeManager.getInstance().getFileTypeByFileName(name);
        DiffContentFactory factory = DiffContentFactory.getInstance();
        SimpleDiffRequest request = new SimpleDiffRequest(
                change.path(),
                factory.create(project, before, type),
                factory.create(project, after, type),
                TributaryBundle.message("diff.title.before"),
                TributaryBundle.message("diff.title.after"));
        DiffManager.getInstance().showDiff(project, request);
    }
}
