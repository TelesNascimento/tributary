package dev.tributary.ui.context;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import dev.tributary.TributaryBundle;
import dev.tributary.core.RtcProjectService;
import org.jetbrains.annotations.NotNull;

public final class RefreshAction extends AnAction implements DumbAware {

    public RefreshAction() {
        super(
                TributaryBundle.message("action.refresh.text"),
                TributaryBundle.message("action.refresh.description"),
                null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() != null) {
            VcsDirtyScopeManager.getInstance(event.getProject()).markEverythingDirty();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation()
                .setEnabledAndVisible(event.getProject() != null
                        && RtcProjectService.getInstance(event.getProject())
                                .sandbox()
                                .isPresent());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
