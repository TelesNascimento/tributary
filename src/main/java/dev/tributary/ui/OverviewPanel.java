package dev.tributary.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import dev.tributary.TributaryBundle;
import dev.tributary.TributaryIcons;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.Model.ChangeSet;
import dev.tributary.cli.Model.Component;
import dev.tributary.cli.Model.PathChange;
import dev.tributary.cli.Model.Workspace;
import dev.tributary.cli.RtcException;
import dev.tributary.context.ContextService;
import dev.tributary.core.RtcProjectService;
import dev.tributary.core.TributaryTopics;
import dev.tributary.ui.changes.ChangeDiffs;
import dev.tributary.ui.common.LazyNode;
import dev.tributary.ui.common.LazyTreeSupport;
import dev.tributary.ui.common.TributaryTasks;
import dev.tributary.vcs.RtcClients;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.jetbrains.annotations.NotNull;

public final class OverviewPanel extends SimpleToolWindowPanel {

    private enum Kind {
        WORKSPACE,
        COMPONENT,
        GROUP,
        CHANGE_SET,
        FILE,
        MESSAGE
    }

    private record Node(Kind kind, String label, String hint, Icon icon, PathChange file, RtcBackend backend) {}

    private final Project project;
    private final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    private final JBLabel card = new JBLabel();

    public OverviewPanel(@NotNull Project project) {
        super(true, true);
        this.project = project;
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());
        LazyTreeSupport.install(tree);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    openSelectedDiff();
                }
            }
        });
        card.setBorder(JBUI.Borders.empty(4, 8));
        JBScrollPane scroll = new JBScrollPane(tree);
        javax.swing.JPanel content = new javax.swing.JPanel(new BorderLayout());
        content.add(card, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        setContent(content);
        buildToolbar();
        var connection = project.getMessageBus().connect(project);
        connection.subscribe(TributaryTopics.STATUS_CHANGED, sandbox -> SwingUtilities.invokeLater(this::render));
        connection.subscribe(TributaryTopics.CONTEXT_CHANGED, () -> SwingUtilities.invokeLater(this::updateCard));
        updateCard();
        render();
        refresh();
    }

    private void buildToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(
                new AnAction(
                        TributaryBundle.message("overview.refresh"),
                        TributaryBundle.message("action.refresh.description"),
                        AllIcons.Actions.Refresh) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent event) {
                        refresh();
                    }

                    @Override
                    public @NotNull ActionUpdateThread getActionUpdateThread() {
                        return ActionUpdateThread.BGT;
                    }
                });
        for (String id : new String[] {"Tributary.StartWork", "Tributary.Deliver", "Tributary.Accept"}) {
            AnAction action = ActionManager.getInstance().getAction(id);
            if (action != null) {
                group.add(action);
            }
        }
        var toolbar = ActionManager.getInstance().createActionToolbar("TributaryOverview", group, true);
        toolbar.setTargetComponent(tree);
        setToolbar(toolbar.getComponent());
    }

    private void refresh() {
        RtcProjectService service = RtcProjectService.getInstance(project);
        Optional<Path> sandbox = service.sandbox();
        if (sandbox.isEmpty()) {
            render();
            return;
        }
        setModel(message(TributaryBundle.message("overview.refreshing")));
        TributaryTasks.run(
                project,
                TributaryBundle.message("changesets.refresh.task"),
                true,
                cancelled -> service.refresh(sandbox.get(), true, cancelled),
                snapshot -> VcsDirtyScopeManager.getInstance(project).markEverythingDirty(),
                null);
    }

    private void updateCard() {
        Optional<dev.tributary.context.ContextState.Entry> active =
                ContextService.getInstance(project).active();
        card.setText(active.map(entry -> TributaryBundle.message("overview.card", entry.label()))
                .orElse(TributaryBundle.message("overview.no.card")));
        card.setForeground(active.isPresent() ? JBColor.foreground() : JBColor.GRAY);
    }

    private void render() {
        RtcProjectService service = RtcProjectService.getInstance(project);
        Optional<Path> sandbox = service.sandbox();
        if (sandbox.isEmpty()) {
            setModel(message(TributaryBundle.message("changes.no.sandbox", project.getBasePath())));
            return;
        }
        Optional<RtcProjectService.Snapshot> snapshot = service.snapshot(sandbox.get());
        if (snapshot.isEmpty()) {
            setModel(message(TributaryBundle.message("overview.refreshing")));
            return;
        }
        if (snapshot.get().failed()) {
            setModel(message(snapshot.get().error().getMessage()));
            return;
        }
        RtcBackend backend;
        try {
            backend = RtcClients.forSandbox(sandbox.get());
        } catch (RtcException e) {
            setModel(message(e.getMessage()));
            return;
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (Workspace workspace : snapshot.get().workspaces()) {
            DefaultMutableTreeNode workspaceNode = node(
                    Kind.WORKSPACE,
                    workspace.name(),
                    workspace.flowTarget() == null ? "" : "→ " + workspace.flowTarget(),
                    TributaryIcons.WORKSPACE);
            for (Component component : workspace.components()) {
                workspaceNode.add(componentNode(component, backend, sandbox.get()));
            }
            root.add(workspaceNode);
        }
        setModel(root);
        for (int row = 0; row < Math.min(tree.getRowCount(), 12); row++) {
            tree.expandRow(row);
        }
    }

    private DefaultMutableTreeNode componentNode(Component component, RtcBackend backend, Path sandbox) {
        DefaultMutableTreeNode node = node(Kind.COMPONENT, component.name(), "", TributaryIcons.COMPONENT);
        node.add(group("overview.outgoing", TributaryIcons.OUTGOING, component.outgoing(), backend));
        node.add(group("overview.incoming", TributaryIcons.INCOMING, component.incoming(), backend));
        if (!component.suspended().isEmpty()) {
            node.add(group("overview.suspended", TributaryIcons.SUSPENDED, component.suspended(), backend));
        }
        DefaultMutableTreeNode unresolved = node(
                Kind.GROUP,
                TributaryBundle.message(
                        "overview.unresolved", component.unresolved().size()),
                "",
                AllIcons.Vcs.Changelist);
        for (PathChange change : component.unresolved()) {
            unresolved.add(new DefaultMutableTreeNode(
                    new Node(Kind.FILE, change.path(), kindOf(change), null, change, backend)));
        }
        node.add(unresolved);
        return node;
    }

    private DefaultMutableTreeNode group(String key, Icon icon, List<ChangeSet> changeSets, RtcBackend backend) {
        DefaultMutableTreeNode group = node(Kind.GROUP, TributaryBundle.message(key, changeSets.size()), "", icon);
        for (ChangeSet changeSet : changeSets) {
            String comment = changeSet.comment() == null || changeSet.comment().isBlank()
                    ? TributaryBundle.message("changes.no.comment")
                    : changeSet.comment();
            String hint = (changeSet.conflict() ? TributaryBundle.message("changes.conflict.prefix") : "")
                    + (changeSet.author() == null ? "" : changeSet.author()) + " "
                    + (changeSet.modified() == null ? "" : changeSet.modified());
            group.add(new LazyNode(
                    new Node(Kind.CHANGE_SET, comment, hint.strip(), TributaryIcons.CHANGE_SET, null, backend),
                    () -> filesOf(changeSet, backend)));
        }
        return group;
    }

    private static List<DefaultMutableTreeNode> filesOf(ChangeSet changeSet, RtcBackend backend) {
        List<DefaultMutableTreeNode> nodes = new ArrayList<>();
        try {
            for (ChangeSet loaded : backend.listChanges(changeSet.uuid())) {
                for (PathChange change : loaded.changes()) {
                    nodes.add(new DefaultMutableTreeNode(
                            new Node(Kind.FILE, change.path(), kindOf(change), null, change, backend)));
                }
            }
        } catch (RtcException e) {
            nodes.add(node(Kind.MESSAGE, e.getMessage(), "", null));
        }
        if (nodes.isEmpty()) {
            nodes.add(node(Kind.MESSAGE, TributaryBundle.message("changesets.files.empty"), "", null));
        }
        return nodes;
    }

    private static String kindOf(PathChange change) {
        if (change.added()) {
            return TributaryBundle.message("changes.kind.added");
        }
        if (change.deleted()) {
            return TributaryBundle.message("changes.kind.deleted");
        }
        return change.moved()
                ? TributaryBundle.message("changes.kind.moved")
                : TributaryBundle.message("changes.kind.modified");
    }

    private void openSelectedDiff() {
        Object selected = tree.getLastSelectedPathComponent();
        if (selected instanceof DefaultMutableTreeNode treeNode
                && treeNode.getUserObject() instanceof Node node
                && node.kind() == Kind.FILE
                && node.file() != null) {
            ChangeDiffs.show(project, node.backend(), node.file());
        }
    }

    private void setModel(DefaultMutableTreeNode root) {
        tree.setModel(new DefaultTreeModel(root));
    }

    private static DefaultMutableTreeNode message(String text) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        root.add(node(Kind.MESSAGE, text, "", null));
        return root;
    }

    private static DefaultMutableTreeNode node(Kind kind, String label, String hint, Icon icon) {
        return new DefaultMutableTreeNode(new Node(kind, label, hint, icon, null, null));
    }

    private static final class Renderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(
                @NotNull JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode treeNode)
                    || !(treeNode.getUserObject() instanceof Node node)) {
                return;
            }
            setIcon(node.icon());
            append(
                    node.label(),
                    node.kind() == Kind.MESSAGE
                            ? SimpleTextAttributes.GRAYED_ATTRIBUTES
                            : node.kind() == Kind.WORKSPACE
                                    ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                                    : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            if (!node.hint().isEmpty()) {
                append("  " + node.hint(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }
    }
}
