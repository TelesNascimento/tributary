package dev.tributary.ui.context;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import dev.tributary.TributaryBundle;
import dev.tributary.TributaryIcons;
import dev.tributary.context.ContextService;
import dev.tributary.context.ContextState.Entry;
import dev.tributary.ui.common.TributaryTasks;
import dev.tributary.workitem.WorkItem;
import dev.tributary.workitem.WorkItemService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;

public final class WorkItemPicker {

    private static final int DEBOUNCE_MS = 300;

    private final Project project;
    private final Consumer<WorkItem> onChosen;
    private final SearchTextField search = new SearchTextField();
    private final DefaultListModel<WorkItem> model = new DefaultListModel<>();
    private final JBList<WorkItem> list = new JBList<>(model);
    private final JBCheckBox mineOnly = new JBCheckBox(TributaryBundle.message("picker.mine.only"), true);
    private final JBCheckBox showResolved = new JBCheckBox(TributaryBundle.message("picker.show.resolved"));
    private final JBLabel status = new JBLabel(" ");
    private final Alarm alarm;
    private JBPopup popup;
    private int generation;

    private WorkItemPicker(Project project, Consumer<WorkItem> onChosen) {
        this.project = project;
        this.onChosen = onChosen;
        this.alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
    }

    public static void show(Project project, Consumer<WorkItem> onChosen) {
        new WorkItemPicker(project, onChosen).open();
    }

    private void open() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.getEmptyText().setText(TributaryBundle.message("picker.empty"));
        search.getTextEditor().getEmptyText().setText(TributaryBundle.message("picker.search.hint"));
        search.addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent event) {
                schedule();
            }
        });
        mineOnly.addActionListener(event -> schedule());
        showResolved.addActionListener(event -> schedule());
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    choose();
                }
            }
        });
        search.getTextEditor().addActionListener(event -> choose());

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        options.add(mineOnly);
        options.add(showResolved);
        JPanel top = new JPanel(new BorderLayout());
        top.add(search, BorderLayout.NORTH);
        top.add(options, BorderLayout.CENTER);
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(JBUI.Borders.empty(6));
        root.add(top, BorderLayout.NORTH);
        root.add(new JBScrollPane(list), BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        root.setPreferredSize(new Dimension(JBUI.scale(620), JBUI.scale(360)));

        popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(root, search.getTextEditor())
                .setTitle(TributaryBundle.message("picker.title"))
                .setFocusable(true)
                .setRequestFocus(true)
                .setResizable(true)
                .setMovable(true)
                .setDimensionServiceKey(project, "Tributary.WorkItemPicker", false)
                .createPopup();
        showRecent();
        popup.showCenteredInCurrentWindow(project);
    }

    private void showRecent() {
        model.clear();
        for (Entry entry : ContextService.getInstance(project).recent()) {
            model.addElement(new WorkItem(entry.workItemId, entry.summary, "", false, "", ""));
        }
        status.setText(model.isEmpty() ? " " : TributaryBundle.message("picker.recent"));
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void schedule() {
        alarm.cancelAllRequests();
        String text = search.getText().trim();
        if (text.isEmpty() && mineOnly.isSelected() && !showResolved.isSelected()) {
            showRecent();
            return;
        }
        alarm.addRequest(this::runSearch, DEBOUNCE_MS);
    }

    private void runSearch() {
        int current = ++generation;
        status.setText(TributaryBundle.message("picker.searching"));
        String text = search.getText().trim();
        boolean mine = mineOnly.isSelected();
        boolean resolved = showResolved.isSelected();
        TributaryTasks.background(
                cancelled -> WorkItemService.getInstance().directory().search(text, mine, resolved, 30),
                items -> {
                    if (current == generation) {
                        show(items);
                    }
                },
                failure -> {
                    if (current == generation) {
                        model.clear();
                        addTypedId();
                        status.setText("<html>" + failure.getMessage() + "</html>");
                    }
                });
    }

    private void show(List<WorkItem> items) {
        model.clear();
        items.forEach(model::addElement);
        if (items.isEmpty()) {
            addTypedId();
        }
        status.setText(TributaryBundle.message("picker.found", items.size()));
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void addTypedId() {
        WorkItem typed = typedIdOnly();
        if (typed != null) {
            model.addElement(typed);
            list.setSelectedIndex(0);
        }
    }

    private void choose() {
        WorkItem selected = list.getSelectedValue();
        if (selected == null) {
            selected = typedIdOnly();
        }
        if (selected != null) {
            popup.cancel();
            onChosen.accept(selected);
        }
    }

    private WorkItem typedIdOnly() {
        String text = search.getText().trim();
        if (text.matches("\\d{4,9}")) {
            return new WorkItem(
                    Long.parseLong(text), TributaryBundle.message("picker.unknown.summary"), "", false, "", "");
        }
        return null;
    }

    private static final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
            WorkItem item = (WorkItem) value;
            String state = item.state().isEmpty() ? "" : "  [" + item.state() + "]";
            super.getListCellRendererComponent(list, item.label() + state, index, selected, focus);
            setIcon(TributaryIcons.WORK_ITEM);
            setBorder(JBUI.Borders.empty(3, 6));
            return this;
        }
    }
}
