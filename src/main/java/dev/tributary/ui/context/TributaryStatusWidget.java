package dev.tributary.ui.context;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.messages.MessageBusConnection;
import dev.tributary.TributaryBundle;
import dev.tributary.cli.Model.Component;
import dev.tributary.cli.Model.Workspace;
import dev.tributary.context.ContextService;
import dev.tributary.context.ContextState.Entry;
import dev.tributary.core.RtcProjectService;
import dev.tributary.core.TributaryTopics;
import dev.tributary.workitem.WorkItem;
import java.nio.file.Path;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TributaryStatusWidget implements StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

    public static final String ID = "dev.tributary.status";
    private static final int MAX_SUMMARY = 32;

    private final Project project;
    private StatusBar statusBar;
    private MessageBusConnection connection;

    public TributaryStatusWidget(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String ID() {
        return ID;
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public void install(@NotNull StatusBar bar) {
        this.statusBar = bar;
        connection = project.getMessageBus().connect(this);
        connection.subscribe(TributaryTopics.CONTEXT_CHANGED, () -> bar.updateWidget(ID));
        connection.subscribe(TributaryTopics.STATUS_CHANGED, sandbox -> bar.updateWidget(ID));
    }

    @Override
    public void dispose() {
        if (connection != null) {
            connection.disconnect();
        }
    }

    @Override
    public @Nullable String getSelectedValue() {
        Optional<Entry> active = ContextService.getInstance(project).active();
        StringBuilder text = new StringBuilder("RTC: ");
        text.append(active.map(TributaryStatusWidget::describe).orElse(TributaryBundle.message("widget.no.card")));
        int[] counts = counts();
        if (counts[0] > 0) {
            text.append("  ⇡").append(counts[0]);
        }
        if (counts[1] > 0) {
            text.append("  ⇣").append(counts[1]);
        }
        return text.toString();
    }

    @Override
    public @Nullable String getTooltipText() {
        int[] counts = counts();
        return TributaryBundle.message("widget.tooltip", counts[0], counts[1]);
    }

    @Override
    public @Nullable JBPopup getPopup() {
        DefaultActionGroup group = new DefaultActionGroup();
        AnAction start = ActionManager.getInstance().getAction("Tributary.StartWork");
        if (start != null) {
            group.add(start);
        }
        ContextService service = ContextService.getInstance(project);
        Optional<Entry> active = service.active();
        boolean separated = false;
        for (Entry entry : service.recent()) {
            if (active.isPresent() && active.get().workItemId == entry.workItemId) {
                continue;
            }
            if (!separated) {
                group.addSeparator(TributaryBundle.message("widget.recent"));
                separated = true;
            }
            group.add(new AnAction(entry.label()) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent event) {
                    StartWorkAction.begin(project, new WorkItem(entry.workItemId, entry.summary, "", false, "", ""));
                }
            });
        }
        group.addSeparator();
        for (String id : new String[] {"Tributary.Deliver", "Tributary.Accept", "Tributary.Refresh"}) {
            AnAction action = ActionManager.getInstance().getAction(id);
            if (action != null) {
                group.add(action);
            }
        }
        return JBPopupFactory.getInstance()
                .createActionGroupPopup(
                        TributaryBundle.message("widget.popup.title"),
                        group,
                        DataManager.getInstance().getDataContext(statusBar.getComponent()),
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                        false);
    }

    private int[] counts() {
        int outgoing = 0;
        int incoming = 0;
        Optional<Path> sandbox = RtcProjectService.getInstance(project).sandbox();
        if (sandbox.isPresent()) {
            var snapshot = RtcProjectService.getInstance(project).snapshot(sandbox.get());
            if (snapshot.isPresent()) {
                for (Workspace workspace : snapshot.get().workspaces()) {
                    for (Component component : workspace.components()) {
                        outgoing += component.outgoing().size();
                        incoming += component.incoming().size();
                    }
                }
            }
        }
        return new int[] {outgoing, incoming};
    }

    private static String describe(Entry entry) {
        String summary =
                entry.summary.length() > MAX_SUMMARY ? entry.summary.substring(0, MAX_SUMMARY) + "..." : entry.summary;
        return "#" + entry.workItemId + " " + summary;
    }
}
