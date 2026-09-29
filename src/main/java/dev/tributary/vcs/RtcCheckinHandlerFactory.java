package dev.tributary.vcs;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import dev.tributary.TributaryBundle;
import dev.tributary.context.ContextService;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RtcCheckinHandlerFactory extends VcsCheckinHandlerFactory {

    public RtcCheckinHandlerFactory() {
        super(RtcVcs.KEY);
    }

    @Override
    protected @NotNull CheckinHandler createVcsHandler(
            @NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
        return new Handler(panel.getProject());
    }

    private static final class Handler extends CheckinHandler {

        private final Project project;
        private final JBCheckBox deliverAfter = new JBCheckBox(TributaryBundle.message("commit.deliver.after"));

        Handler(Project project) {
            this.project = project;
        }

        @Override
        public @Nullable RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
            JPanel root = new JPanel(new BorderLayout());
            root.setBorder(JBUI.Borders.emptyTop(2));
            JBLabel card = new JBLabel();
            var active = ContextService.getInstance(project).active();
            if (active.isPresent()) {
                card.setText(TributaryBundle.message("commit.card", active.get().label()));
            } else {
                card.setText(TributaryBundle.message("commit.no.card"));
                card.setForeground(JBColor.namedColor("Component.warningFocusColor", JBColor.ORANGE));
            }
            root.add(card, BorderLayout.NORTH);
            root.add(deliverAfter, BorderLayout.SOUTH);
            return new RefreshableOnComponent() {
                @Override
                public JComponent getComponent() {
                    return root;
                }

                @Override
                public void refresh() {}

                @Override
                public void saveState() {}

                @Override
                public void restoreState() {}
            };
        }

        @Override
        public void checkinSuccessful() {
            if (!deliverAfter.isSelected()) {
                return;
            }
            AnAction deliver = ActionManager.getInstance().getAction("Tributary.Deliver");
            if (deliver != null) {
                ApplicationManager.getApplication()
                        .invokeLater(() -> com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction(
                                deliver, SimpleDataContext.getProjectContext(project), "Tributary.Commit", null, null));
            }
        }
    }
}
