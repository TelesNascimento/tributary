package dev.tributary.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import dev.tributary.TributaryBundle;
import dev.tributary.backend.RtcBackend;
import dev.tributary.backend.WorkspaceNames;
import dev.tributary.cli.Model.RemoteWorkspace;
import dev.tributary.cli.RtcException;
import dev.tributary.core.RtcProjectService;
import dev.tributary.settings.TributarySettings;
import dev.tributary.ui.common.TributaryNotifier;
import dev.tributary.ui.common.TributaryTasks;
import dev.tributary.vcs.RtcClients;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;

public final class WorkspacesPanel extends SimpleToolWindowPanel {

    private enum Kind {
        GROUP,
        PROJECT_AREA,
        WORKSPACE,
        STREAM,
        MESSAGE,
        LOADING
    }

    private record Node(Kind kind, String label, String hint, RemoteWorkspace item) {}

    private final Project project;
    private final Tree tree =
            new Tree(new DefaultTreeModel(new DefaultMutableTreeNode(TributaryBundle.message("app.title"))));

    public WorkspacesPanel(@NotNull Project project) {
        super(true, true);
        this.project = project;
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                expandLazy(event.getPath());
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {}
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    loadSelected();
                }
            }
        });
        DefaultActionGroup popup = new DefaultActionGroup();
        popup.add(action(
                TributaryBundle.message("workspaces.load.text"),
                TributaryBundle.message("workspaces.load.description"),
                AllIcons.Actions.Download,
                this::loadSelected));
        popup.add(action(
                TributaryBundle.message("workspaces.create.text"),
                TributaryBundle.message("workspaces.create.description"),
                AllIcons.General.Add,
                this::createFromSelected,
                () -> selected() != null));
        popup.add(action(
                TributaryBundle.message("workspaces.unload.text"),
                TributaryBundle.message("workspaces.unload.description"),
                AllIcons.Actions.Cancel,
                this::unloadSelected,
                () -> selectedKind() == Kind.WORKSPACE));
        popup.addSeparator();
        popup.add(action(
                TributaryBundle.message("workspaces.copy.text"),
                TributaryBundle.message("workspaces.copy.description"),
                AllIcons.Actions.Copy,
                this::copyName));
        PopupHandler.installPopupMenu(tree, popup, "RtcWorkspacesPopup");
        setContent(new JBScrollPane(tree));
        DefaultActionGroup toolbarGroup = new DefaultActionGroup();
        toolbarGroup.add(action(
                TributaryBundle.message("workspaces.refresh.text"),
                TributaryBundle.message("workspaces.refresh.description"),
                AllIcons.Actions.Refresh,
                this::refresh));
        toolbarGroup.add(action(
                TributaryBundle.message("workspaces.load.text"),
                TributaryBundle.message("workspaces.load.selected"),
                AllIcons.Actions.Download,
                this::loadSelected));
        var toolbar = ActionManager.getInstance().createActionToolbar("RtcWorkspaces", toolbarGroup, true);
        toolbar.setTargetComponent(tree);
        setToolbar(toolbar.getComponent());
        refresh();
    }

    public void refresh() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(TributaryBundle.message("app.title"));
        root.add(tn(Kind.LOADING, TributaryBundle.message("workspaces.loading"), "", null));
        tree.setModel(new DefaultTreeModel(root));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            DefaultMutableTreeNode result = new DefaultMutableTreeNode(TributaryBundle.message("app.title"));
            try {
                RtcBackend client = RtcClients.global();
                String uri = RtcClients.serverUri();
                List<RemoteWorkspace> all = client.listWorkspaces(uri, 2000);
                String user = TributarySettings.getInstance().userId();
                DefaultMutableTreeNode mine = new DefaultMutableTreeNode(
                        node(Kind.GROUP, TributaryBundle.message("workspaces.group.mine"), "", null));
                DefaultMutableTreeNode others = new DefaultMutableTreeNode(
                        node(Kind.GROUP, TributaryBundle.message("workspaces.group.others"), "", null));
                for (RemoteWorkspace ws : all) {
                    boolean isMine =
                            !user.isBlank() && ws.owner() != null && ws.owner().contains(user);
                    (isMine ? mine : others)
                            .add(new DefaultMutableTreeNode(node(Kind.WORKSPACE, ws.name(), ws.owner(), ws)));
                }
                result.add(mine);
                DefaultMutableTreeNode areas = new DefaultMutableTreeNode(
                        node(Kind.GROUP, TributaryBundle.message("workspaces.group.projects"), "", null));
                for (RemoteWorkspace pa : client.listProjectAreas(uri)) {
                    DefaultMutableTreeNode paNode =
                            new DefaultMutableTreeNode(node(Kind.PROJECT_AREA, pa.name(), "", pa));
                    paNode.add(new DefaultMutableTreeNode(node(Kind.LOADING, "...", "", null)));
                    areas.add(paNode);
                }
                result.add(areas);
                result.add(others);
            } catch (RtcException e) {
                result.add(tn(Kind.MESSAGE, e.getMessage(), "", null));
            }
            SwingUtilities.invokeLater(() -> {
                tree.setModel(new DefaultTreeModel(result));
                tree.expandRow(0);
            });
        });
    }

    private void expandLazy(TreePath path) {
        var treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!(treeNode.getUserObject() instanceof Node n) || n.kind() != Kind.PROJECT_AREA) {
            return;
        }
        if (treeNode.getChildCount() != 1
                || !(((DefaultMutableTreeNode) treeNode.getFirstChild()).getUserObject() instanceof Node c)
                || c.kind() != Kind.LOADING) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<DefaultMutableTreeNode> children = new java.util.ArrayList<>();
            try {
                for (RemoteWorkspace stream : RtcClients.global().listStreams(RtcClients.serverUri(), n.label(), 500)) {
                    children.add(new DefaultMutableTreeNode(node(Kind.STREAM, stream.name(), "", stream)));
                }
                if (children.isEmpty()) {
                    children.add(new DefaultMutableTreeNode(
                            node(Kind.MESSAGE, TributaryBundle.message("workspaces.no.streams"), "", null)));
                }
            } catch (RtcException e) {
                children.add(tn(Kind.MESSAGE, e.getMessage(), "", null));
            }
            SwingUtilities.invokeLater(() -> {
                treeNode.removeAllChildren();
                children.forEach(treeNode::add);
                ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(treeNode);
            });
        });
    }

    private RemoteWorkspace selected() {
        Object o = tree.getLastSelectedPathComponent();
        if (o instanceof DefaultMutableTreeNode n
                && n.getUserObject() instanceof Node node
                && (node.kind() == Kind.WORKSPACE || node.kind() == Kind.STREAM)) {
            return node.item();
        }
        return null;
    }

    private Kind selectedKind() {
        Object o = tree.getLastSelectedPathComponent();
        return o instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof Node node ? node.kind() : null;
    }

    private void loadSelected() {
        RemoteWorkspace ws = selected();
        if (ws == null) {
            Messages.showInfoMessage(
                    project, TributaryBundle.message("workspaces.select"), TributaryBundle.message("app.title"));
            return;
        }
        load(ws);
    }

    private void load(RemoteWorkspace ws) {
        try {
            RtcBackend client = RtcClients.global();
            LoadWorkspaceDialog dialog = new LoadWorkspaceDialog(project, client, RtcClients.serverUri(), ws);
            if (dialog.showAndGet()) {
                var dir = dialog.targetDir();
                var comps = dialog.selectedComponents();
                TributaryTasks.run(
                        project,
                        TributaryBundle.message("load.task.title", ws.name()),
                        true,
                        cancelled -> client.withCancel(cancelled).load(RtcClients.serverUri(), dir, ws.uuid(), comps),
                        output -> TributaryNotifier.info(
                                project, TributaryBundle.message("load.done", ws.name()), output.strip()),
                        null);
            }
        } catch (RtcException e) {
            Messages.showErrorDialog(project, e.getMessage(), TributaryBundle.message("app.title"));
        }
    }

    private void createFromSelected() {
        RemoteWorkspace source = selected();
        if (source == null) {
            return;
        }
        String suggestion =
                WorkspaceNames.suggest(TributarySettings.getInstance().userId(), source.name());
        NewWorkspaceDialog dialog = new NewWorkspaceDialog(project, source.name(), suggestion);
        if (!dialog.showAndGet()) {
            return;
        }
        String name = dialog.workspaceName();
        boolean loadAfter = dialog.loadAfterCreate();
        try {
            RtcBackend client = RtcClients.global();
            TributaryTasks.run(
                    project,
                    TributaryBundle.message("workspaces.create.task", name),
                    true,
                    cancelled ->
                            client.withCancel(cancelled).createWorkspace(RtcClients.serverUri(), source.uuid(), name),
                    created -> {
                        TributaryNotifier.info(project, TributaryBundle.message("workspaces.create.done", name), "");
                        refresh();
                        if (loadAfter) {
                            load(created);
                        }
                    },
                    failure -> TributaryNotifier.error(
                            project, TributaryBundle.message("workspaces.create.task", name), failure));
        } catch (RtcException e) {
            Messages.showErrorDialog(project, e.getMessage(), TributaryBundle.message("app.title"));
        }
    }

    private void unloadSelected() {
        RemoteWorkspace ws = selected();
        if (ws == null || selectedKind() != Kind.WORKSPACE) {
            return;
        }
        var sandbox = RtcProjectService.getInstance(project).sandbox();
        if (sandbox.isEmpty()) {
            Messages.showInfoMessage(
                    project,
                    TributaryBundle.message("workspaces.unload.nosandbox"),
                    TributaryBundle.message("workspaces.unload.title"));
            return;
        }
        int choice = Messages.showYesNoCancelDialog(
                project,
                TributaryBundle.message("workspaces.unload.message", ws.name()),
                TributaryBundle.message("workspaces.unload.title"),
                TributaryBundle.message("workspaces.unload.keep"),
                TributaryBundle.message("workspaces.unload.delete"),
                TributaryBundle.message("workspaces.unload.cancel"),
                Messages.getQuestionIcon());
        if (choice != Messages.YES && choice != Messages.NO) {
            return;
        }
        boolean deleteFiles = choice == Messages.NO;
        try {
            RtcBackend client = RtcClients.forSandbox(sandbox.get());
            TributaryTasks.run(
                    project,
                    TributaryBundle.message("workspaces.unload.task", ws.name()),
                    true,
                    cancelled -> client.withCancel(cancelled).unload(ws.uuid(), deleteFiles),
                    output -> TributaryNotifier.info(
                            project, TributaryBundle.message("workspaces.unload.done", ws.name()), output.strip()),
                    failure -> TributaryNotifier.error(
                            project, TributaryBundle.message("workspaces.unload.title"), failure));
        } catch (RtcException e) {
            Messages.showErrorDialog(project, e.getMessage(), TributaryBundle.message("app.title"));
        }
    }

    private void copyName() {
        RemoteWorkspace ws = selected();
        if (ws != null) {
            com.intellij.openapi.ide.CopyPasteManager.getInstance()
                    .setContents(new java.awt.datatransfer.StringSelection(ws.name()));
        }
    }

    private static Node node(Kind kind, String label, String hint, RemoteWorkspace item) {
        return new Node(kind, label, hint == null ? "" : hint, item);
    }

    private static DefaultMutableTreeNode tn(Kind kind, String label, String hint, RemoteWorkspace item) {
        return new DefaultMutableTreeNode(node(kind, label, hint, item));
    }

    private AnAction action(String text, String description, Icon icon, Runnable body) {
        return action(text, description, icon, body, null);
    }

    private AnAction action(String text, String description, Icon icon, Runnable body, BooleanSupplier enabled) {
        return new AnAction(text, description, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                body.run();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                if (enabled != null) {
                    e.getPresentation().setEnabledAndVisible(enabled.getAsBoolean());
                }
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return enabled == null ? ActionUpdateThread.BGT : ActionUpdateThread.EDT;
            }
        };
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
            if (!(value instanceof DefaultMutableTreeNode n) || !(n.getUserObject() instanceof Node node)) {
                return;
            }
            setIcon(
                    switch (node.kind()) {
                        case GROUP -> AllIcons.Nodes.Folder;
                        case PROJECT_AREA -> AllIcons.Nodes.Package;
                        case WORKSPACE -> AllIcons.Vcs.Branch;
                        case STREAM -> AllIcons.Nodes.Module;
                        default -> null;
                    });
            append(
                    node.label(),
                    node.kind() == Kind.MESSAGE || node.kind() == Kind.LOADING
                            ? SimpleTextAttributes.GRAYED_ATTRIBUTES
                            : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            if (!node.hint().isEmpty()) {
                append("  " + node.hint(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY));
            }
        }
    }
}
