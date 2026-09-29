package dev.tributary.ui.context;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import dev.tributary.core.RtcProjectService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class TributaryStatusWidgetFactory implements StatusBarWidgetFactory {

    @Override
    public @NotNull String getId() {
        return TributaryStatusWidget.ID;
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
        return "Tributary";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return RtcProjectService.getInstance(project).sandbox().isPresent();
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new TributaryStatusWidget(project);
    }
}
