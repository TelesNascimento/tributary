package dev.tributary.ui.common;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public abstract class BusyAction extends AnAction {

    private final AtomicBoolean running = new AtomicBoolean();
    private final Icon idleIcon;

    protected BusyAction(String text, String description, Icon icon) {
        super(text, description, icon);
        this.idleIcon = icon;
    }

    protected abstract void perform(@NotNull AnActionEvent event, @NotNull Runnable done);

    @Override
    public final void actionPerformed(@NotNull AnActionEvent event) {
        if (running.compareAndSet(false, true)) {
            perform(event, () -> running.set(false));
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        boolean busy = running.get();
        event.getPresentation().setEnabled(!busy && isAvailable(event));
        event.getPresentation().setIcon(busy ? AllIcons.Process.Step_passive : idleIcon);
    }

    protected boolean isAvailable(@NotNull AnActionEvent event) {
        return true;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
