package dev.tributary.vcs;

import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.wm.IdeFrame;
import dev.tributary.core.RtcProjectService;
import org.jetbrains.annotations.NotNull;

public final class RefreshOnActivation implements ApplicationActivationListener {

    @Override
    public void applicationActivated(@NotNull IdeFrame ideFrame) {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()
                    && RtcProjectService.getInstance(project).sandbox().isPresent()) {
                VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
            }
        }
    }
}
