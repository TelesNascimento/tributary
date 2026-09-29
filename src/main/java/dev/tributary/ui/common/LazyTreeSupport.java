package dev.tributary.ui.common;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.treeStructure.Tree;
import java.util.List;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public final class LazyTreeSupport {

    private LazyTreeSupport() {}

    public static void install(Tree tree) {
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                Object last = event.getPath().getLastPathComponent();
                if (last instanceof LazyNode node && !node.isLoaded()) {
                    node.markLoaded();
                    loadAsync(tree, node);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {}
        });
    }

    private static void loadAsync(Tree tree, LazyNode node) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<DefaultMutableTreeNode> children = node.load();
            ApplicationManager.getApplication()
                    .invokeLater(
                            () -> {
                                node.removeAllChildren();
                                children.forEach(node::add);
                                ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
                            },
                            ModalityState.any());
        });
    }
}
