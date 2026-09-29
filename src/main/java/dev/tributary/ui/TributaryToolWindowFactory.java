package dev.tributary.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import dev.tributary.TributaryBundle;
import org.jetbrains.annotations.NotNull;

public final class TributaryToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentFactory factory = ContentFactory.getInstance();
        Content changes =
                factory.createContent(new OverviewPanel(project), TributaryBundle.message("toolwindow.changes"), false);
        Content workspaces = factory.createContent(
                new WorkspacesPanel(project), TributaryBundle.message("toolwindow.workspaces"), false);
        changes.setCloseable(false);
        workspaces.setCloseable(false);
        toolWindow.getContentManager().addContent(changes);
        toolWindow.getContentManager().addContent(workspaces);
    }
}
