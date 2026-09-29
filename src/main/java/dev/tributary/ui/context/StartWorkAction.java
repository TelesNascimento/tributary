package dev.tributary.ui.context;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import dev.tributary.TributaryBundle;
import dev.tributary.TributaryIcons;
import dev.tributary.context.ContextService;
import dev.tributary.context.ContextState.Entry;
import dev.tributary.core.RtcProjectService;
import dev.tributary.ui.common.TributaryNotifier;
import dev.tributary.ui.common.TributaryTasks;
import dev.tributary.workitem.WorkItem;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public final class StartWorkAction extends AnAction implements DumbAware {

    public StartWorkAction() {
        super(
                TributaryBundle.message("action.start.work.text"),
                TributaryBundle.message("action.start.work.description"),
                TributaryIcons.WORK_ITEM);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project != null) {
            WorkItemPicker.show(project, workItem -> begin(project, workItem));
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        event.getPresentation()
                .setEnabledAndVisible(project != null
                        && RtcProjectService.getInstance(project).sandbox().isPresent());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    public static void begin(Project project, WorkItem workItem) {
        ContextService service = ContextService.getInstance(project);
        boolean suspend = askToSuspend(project, service, workItem);
        TributaryTasks.run(
                project,
                TributaryBundle.message("start.work.task", workItem.id()),
                true,
                cancelled -> service.start(workItem, suspend),
                entry -> {
                    TributaryNotifier.info(project, TributaryBundle.message("start.work.done"), entry.label());
                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                },
                null);
    }

    private static boolean askToSuspend(Project project, ContextService service, WorkItem next) {
        Optional<Entry> previous = service.active();
        if (previous.isEmpty()
                || previous.get().workItemId == next.id()
                || previous.get().changeSets.isEmpty()) {
            return false;
        }
        return Messages.showYesNoDialog(
                        project,
                        TributaryBundle.message(
                                "switch.suspend.confirm", previous.get().label(), next.id()),
                        TributaryBundle.message("switch.suspend.title"),
                        Messages.getQuestionIcon())
                == Messages.YES;
    }
}
