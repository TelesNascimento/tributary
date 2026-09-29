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
import dev.tributary.ui.common.TributaryNotifier;
import dev.tributary.ui.common.TributaryTasks;

public final class ChangeDiffs {

    private ChangeDiffs() {}

    public static void show(Project project, RtcBackend backend, PathChange change) {
        TributaryTasks.run(
                project,
                TributaryBundle.message("diff.loading", change.path()),
                true,
                cancelled -> new String[] {before(backend, change), after(backend, change)},
                contents -> open(project, change, contents[0], contents[1]),
                failure -> TributaryNotifier.error(project, TributaryBundle.message("diff.failed"), failure));
    }

    private static String before(RtcBackend backend, PathChange change) throws dev.tributary.cli.RtcException {
        if (change.added() || change.beforeStateId() == null || change.uuid() == null) {
            return "";
        }
        return backend.fileContent(change.uuid(), change.beforeStateId());
    }

    private static String after(RtcBackend backend, PathChange change) throws dev.tributary.cli.RtcException {
        if (change.deleted() || change.stateId() == null || change.uuid() == null) {
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
