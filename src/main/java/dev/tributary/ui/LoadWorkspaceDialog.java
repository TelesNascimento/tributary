package dev.tributary.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import dev.tributary.TributaryBundle;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.Model.RemoteWorkspace;
import dev.tributary.cli.RtcException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import org.jetbrains.annotations.Nullable;

public final class LoadWorkspaceDialog extends DialogWrapper {

    private final RtcBackend client;
    private final String repositoryUri;
    private final JBTextField filter = new JBTextField();
    private final DefaultListModel<RemoteWorkspace> workspaceModel = new DefaultListModel<>();
    private final JBList<RemoteWorkspace> workspaces = new JBList<>(workspaceModel);
    private final DefaultListModel<String> componentModel = new DefaultListModel<>();
    private final JBList<String> components = new JBList<>(componentModel);
    private final TextFieldWithBrowseButton target = new TextFieldWithBrowseButton();
    private final List<RemoteWorkspace> all = new ArrayList<>();

    public LoadWorkspaceDialog(Project project, RtcBackend client, String repositoryUri, RemoteWorkspace preselected) {
        super(project);
        this.client = client;
        this.repositoryUri = repositoryUri;
        setTitle(TributaryBundle.message("load.dialog.title"));
        workspaces.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        components.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        workspaces.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                    javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
                RemoteWorkspace ws = (RemoteWorkspace) value;
                return super.getListCellRendererComponent(
                        list, ws.name() + "  [" + ws.owner() + "]", index, selected, focus);
            }
        });
        target.addBrowseFolderListener(
                project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle(TributaryBundle.message("load.target.chooser")));
        filter.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                applyFilter();
            }
        });
        workspaces.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadComponents();
            }
        });
        init();
        if (preselected != null) {
            all.add(preselected);
            applyFilter();
            workspaces.setSelectedIndex(0);
        } else {
            loadWorkspaces();
        }
    }

    public RemoteWorkspace workspace() {
        return workspaces.getSelectedValue();
    }

    public List<String> selectedComponents() {
        return components.getSelectedValuesList();
    }

    public Path targetDir() {
        return Path.of(target.getText().trim());
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBScrollPane wsPane = new JBScrollPane(workspaces);
        wsPane.setPreferredSize(new java.awt.Dimension(640, 200));
        JBScrollPane cPane = new JBScrollPane(components);
        cPane.setPreferredSize(new java.awt.Dimension(640, 110));
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(TributaryBundle.message("load.filter.label"), filter)
                .addComponent(wsPane)
                .addLabeledComponent(TributaryBundle.message("load.components.label"), cPane, true)
                .addLabeledComponent(TributaryBundle.message("load.target.label"), target)
                .getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (workspace() == null) {
            return new ValidationInfo(TributaryBundle.message("load.error.workspace"));
        }
        if (selectedComponents().isEmpty()) {
            return new ValidationInfo(TributaryBundle.message("load.error.component"), components);
        }
        if (target.getText().isBlank()) {
            return new ValidationInfo(TributaryBundle.message("load.error.target"), target);
        }
        return null;
    }

    private void loadWorkspaces() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                List<RemoteWorkspace> list = client.listWorkspaces(repositoryUri, 1000);
                SwingUtilities.invokeLater(() -> {
                    all.clear();
                    all.addAll(list);
                    applyFilter();
                });
            } catch (RtcException e) {
                SwingUtilities.invokeLater(() -> setErrorText(e.getMessage()));
            }
        });
    }

    private void applyFilter() {
        String text = filter.getText().trim().toLowerCase();
        workspaceModel.clear();
        all.stream()
                .filter(w -> text.isEmpty()
                        || w.name().toLowerCase().contains(text)
                        || (w.owner() != null && w.owner().toLowerCase().contains(text)))
                .forEach(workspaceModel::addElement);
    }

    private void loadComponents() {
        RemoteWorkspace ws = workspace();
        componentModel.clear();
        if (ws == null) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                List<String> names = client.listComponents(repositoryUri, ws.uuid());
                SwingUtilities.invokeLater(() -> names.forEach(componentModel::addElement));
            } catch (RtcException e) {
                SwingUtilities.invokeLater(() -> setErrorText(e.getMessage()));
            }
        });
    }
}
