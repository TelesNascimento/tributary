package dev.tributary.ui.changes;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import dev.tributary.TributaryBundle;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.Model.ChangeSet;
import dev.tributary.cli.Model.Component;
import dev.tributary.cli.Model.Workspace;
import dev.tributary.cli.RtcException;
import dev.tributary.context.ContextService;
import dev.tributary.core.RtcProjectService;
import dev.tributary.ui.changes.ChangeSetsDialog.Mode;
import dev.tributary.ui.common.TributaryNotifier;
import dev.tributary.ui.common.TributaryTasks;
import dev.tributary.vcs.RtcClients;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

abstract class ChangeSetsAction extends AnAction implements DumbAware {

    private final Mode mode;

    ChangeSetsAction(Mode mode, String text, String description, Icon icon) {
        super(text, description, icon);
        this.mode = mode;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        RtcProjectService service = RtcProjectService.getInstance(project);
        Path sandbox = service.sandbox().orElse(null);
        if (sandbox == null) {
            return;
        }
        TributaryTasks.run(
                project,
                TributaryBundle.message("changesets.refresh.task"),
                true,
                cancelled -> service.refresh(sandbox, true, cancelled),
                snapshot -> open(project, sandbox, snapshot),
                null);
    }

    private void open(Project project, Path sandbox, RtcProjectService.Snapshot snapshot) {
        if (snapshot.failed()) {
            TributaryNotifier.error(project, TributaryBundle.message("changesets.refresh.failed"), snapshot.error());
            return;
        }
        List<ChangeSet> changeSets = new ArrayList<>();
        int unresolved = 0;
        int incoming = 0;
        int outgoing = 0;
        for (Workspace workspace : snapshot.workspaces()) {
            for (Component component : workspace.components()) {
                changeSets.addAll(mode == Mode.DELIVER ? component.outgoing() : component.incoming());
                unresolved += component.unresolved().size();
                incoming += component.incoming().size();
                outgoing += component.outgoing().size();
            }
        }
        if (changeSets.isEmpty()) {
            TributaryNotifier.info(
                    project,
                    TributaryBundle.message(mode == Mode.DELIVER ? "deliver.dialog.title" : "accept.dialog.title"),
                    TributaryBundle.message(mode == Mode.DELIVER ? "deliver.nothing" : "accept.nothing"));
            return;
        }
        RtcBackend backend;
        try {
            backend = RtcClients.forSandbox(sandbox);
        } catch (RtcException e) {
            TributaryNotifier.error(project, TributaryBundle.message("changesets.refresh.failed"), e);
            return;
        }
        List<String> warnings = new ArrayList<>();
        if (mode == Mode.DELIVER && incoming > 0) {
            warnings.add(TributaryBundle.message("deliver.warning.incoming", incoming));
        }
        if (mode == Mode.ACCEPT && unresolved > 0) {
            warnings.add(TributaryBundle.message("accept.warning.unresolved", unresolved));
        }
        ChangeSetsDialog dialog = new ChangeSetsDialog(project, backend, mode, changeSets, warnings);
        if (dialog.showAndGet()) {
            run(project, sandbox, backend, dialog.selectedChangeSets());
        }
    }

    private void run(Project project, Path sandbox, RtcBackend backend, List<String> selected) {
        String title = TributaryBundle.message(mode == Mode.DELIVER ? "deliver.task" : "accept.task", selected.size());
        RtcProjectService service = RtcProjectService.getInstance(project);
        TributaryTasks.run(
                project,
                title,
                false,
                cancelled -> {
                    String output = mode == Mode.DELIVER ? backend.deliver(selected) : backend.accept(selected, null);
                    RtcProjectService.Snapshot snapshot = service.refresh(sandbox, true, cancelled);
                    if (!snapshot.failed() && !snapshot.workspaces().isEmpty()) {
                        ContextService.getInstance(project)
                                .reconcile(snapshot.workspaces().get(0));
                    }
                    return output;
                },
                output -> {
                    TributaryNotifier.info(
                            project,
                            TributaryBundle.message(
                                    mode == Mode.DELIVER ? "deliver.done" : "accept.done", selected.size()),
                            output.strip());
                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                },
                null);
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
}
