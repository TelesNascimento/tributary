package dev.tributary.ignore;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.tributary.TributaryBundle;
import dev.tributary.cli.SandboxDetector;
import dev.tributary.ui.common.TributaryNotifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public final class AddToJazzIgnoreAction extends AnAction implements DumbAware {

    public AddToJazzIgnoreAction() {
        super(
                TributaryBundle.message("action.ignore.text"),
                TributaryBundle.message("action.ignore.description"),
                null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (project == null || files == null) {
            return;
        }
        try {
            for (VirtualFile file : files) {
                add(file.toNioPath());
            }
        } catch (IOException e) {
            TributaryNotifier.warn(project, TributaryBundle.message("ignore.failed"), e.getMessage());
            return;
        }
        VirtualFile root = LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(files[0].toNioPath().getParent());
        if (root != null) {
            root.refresh(false, false);
        }
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        boolean inSandbox = files != null
                && files.length > 0
                && java.util.Arrays.stream(files)
                        .allMatch(f -> f.isInLocalFileSystem()
                                && SandboxDetector.findRoot(f.toNioPath()).isPresent());
        event.getPresentation().setEnabledAndVisible(inSandbox);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static void add(Path file) throws IOException {
        Path ignoreFile = file.getParent().resolve(JazzIgnore.FILE_NAME);
        String existing = Files.isRegularFile(ignoreFile) ? Files.readString(ignoreFile) : "";
        Files.writeString(
                ignoreFile, JazzIgnore.add(existing, file.getFileName().toString(), false));
    }
}
