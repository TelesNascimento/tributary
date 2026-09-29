package dev.tributary.ui.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import dev.tributary.TributaryBundle;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.Model.ChangeSet;
import dev.tributary.cli.Model.PathChange;
import dev.tributary.ui.common.TributaryTasks;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.Nullable;

public final class ChangeSetsDialog extends DialogWrapper {

    public enum Mode {
        DELIVER,
        ACCEPT
    }

    private final Project project;
    private final RtcBackend backend;
    private final Mode mode;
    private final List<ChangeSet> changeSets;
    private final List<String> warnings;
    private final Map<String, ChangeSet> details = new HashMap<>();
    private final CheckBoxList<ChangeSet> list = new CheckBoxList<>();
    private final DefaultListModel<PathChange> filesModel = new DefaultListModel<>();
    private final JBList<PathChange> files = new JBList<>(filesModel);
    private final JBLabel header = new JBLabel(" ");
    private final JBLabel warning = new JBLabel(" ");
    private volatile boolean closed;

    public ChangeSetsDialog(
            Project project, RtcBackend backend, Mode mode, List<ChangeSet> changeSets, List<String> warnings) {
        super(project);
        this.project = project;
        this.backend = backend;
        this.mode = mode;
        this.changeSets = changeSets;
        this.warnings = warnings;
        setTitle(TributaryBundle.message(mode == Mode.DELIVER ? "deliver.dialog.title" : "accept.dialog.title"));
        setOKButtonText(TributaryBundle.message(mode == Mode.DELIVER ? "deliver.dialog.ok" : "accept.dialog.ok"));
        init();
        loadDetails();
    }

    public List<String> selectedChangeSets() {
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < list.getItemsCount(); i++) {
            if (list.isItemSelected(i)) {
                selected.add(list.getItemAt(i).uuid());
            }
        }
        return selected;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        for (ChangeSet changeSet : changeSets) {
            list.addItem(changeSet, label(changeSet), true);
        }
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelected();
            }
        });
        list.setCheckBoxListListener(
                (index, value) -> getOKAction().setEnabled(!selectedChangeSets().isEmpty()));
        files.setCellRenderer(new FileRenderer());
        files.getEmptyText().setText(TributaryBundle.message("changesets.files.empty"));
        files.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                PathChange selected = files.getSelectedValue();
                if (event.getClickCount() == 2 && selected != null) {
                    ChangeDiffs.show(project, backend, selected);
                }
            }
        });

        JPanel left = new JPanel(new BorderLayout());
        left.add(new JBScrollPane(list), BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout());
        header.setBorder(JBUI.Borders.empty(0, 0, 4, 0));
        right.add(header, BorderLayout.NORTH);
        right.add(new JBScrollPane(files), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.45);

        warning.setForeground(JBColor.namedColor("Component.warningFocusColor", JBColor.ORANGE));
        warning.setText(warnings.isEmpty() ? " " : "<html>" + String.join("<br>", warnings) + "</html>");
        JPanel root = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        root.add(warning, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(JBUI.scale(860), JBUI.scale(420)));
        if (!changeSets.isEmpty()) {
            list.setSelectedIndex(0);
        }
        return root;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        return selectedChangeSets().isEmpty()
                ? new ValidationInfo(TributaryBundle.message("changesets.none.selected"))
                : null;
    }

    @Override
    protected void dispose() {
        closed = true;
        super.dispose();
    }

    private void loadDetails() {
        for (ChangeSet changeSet : changeSets) {
            TributaryTasks.background(
                    cancelled -> backend.listChanges(changeSet.uuid()),
                    result -> {
                        if (!closed && !result.isEmpty()) {
                            details.put(changeSet.uuid(), result.get(0));
                            refreshRow(changeSet);
                            showSelected();
                        }
                    },
                    failure -> {});
        }
    }

    private void refreshRow(ChangeSet changeSet) {
        ChangeSet loaded = details.get(changeSet.uuid());
        if (loaded != null && loaded.workItems().isEmpty() && mode == Mode.DELIVER) {
            warning.setText("<html>"
                    + TributaryBundle.message("deliver.warning.no.workitem", changeSet.comment())
                    + "<br>" + String.join("<br>", warnings) + "</html>");
        }
    }

    private void showSelected() {
        int index = list.getSelectedIndex();
        ChangeSet selected = index < 0 ? null : list.getItemAt(index);
        filesModel.clear();
        if (selected == null) {
            header.setText(" ");
            return;
        }
        ChangeSet loaded = details.get(selected.uuid());
        String workItems = loaded == null || loaded.workItems().isEmpty()
                ? TributaryBundle.message("changesets.no.workitem")
                : loaded.workItems().stream().map(w -> "#" + w.id()).collect(Collectors.joining(", "));
        header.setText("<html><b>" + escape(selected.comment()) + "</b><br>" + escape(selected.author()) + " • "
                + escape(selected.modified()) + " • " + workItems + "</html>");
        if (loaded != null) {
            loaded.changes().forEach(filesModel::addElement);
        }
    }

    private static String label(ChangeSet changeSet) {
        String comment = changeSet.comment() == null || changeSet.comment().isBlank()
                ? TributaryBundle.message("changes.no.comment")
                : changeSet.comment();
        return comment + (changeSet.conflict() ? "  (" + TributaryBundle.message("changesets.conflict") + ")" : "");
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;");
    }

    private static final class FileRenderer extends ColoredListCellRenderer<PathChange> {
        @Override
        protected void customizeCellRenderer(
                JList<? extends PathChange> list, PathChange change, int index, boolean selected, boolean focused) {
            SimpleTextAttributes attributes = change.added()
                    ? new SimpleTextAttributes(
                            SimpleTextAttributes.STYLE_PLAIN,
                            JBColor.namedColor("VersionControl.FileStatus.added", JBColor.GREEN))
                    : change.deleted()
                            ? new SimpleTextAttributes(
                                    SimpleTextAttributes.STYLE_PLAIN,
                                    JBColor.namedColor("VersionControl.FileStatus.deleted", JBColor.GRAY))
                            : SimpleTextAttributes.REGULAR_ATTRIBUTES;
            append(change.path(), attributes);
        }
    }
}
