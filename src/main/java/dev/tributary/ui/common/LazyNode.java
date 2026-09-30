package dev.tributary.ui.common;

import java.util.List;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

public class LazyNode extends DefaultMutableTreeNode {

    private final Supplier<List<DefaultMutableTreeNode>> loader;
    private boolean loaded;

    public LazyNode(Object userObject, Supplier<List<DefaultMutableTreeNode>> loader) {
        super(userObject);
        this.loader = loader;
        add(new LoadingNode());
    }

    public boolean isLoaded() {
        return loaded;
    }

    void markLoaded() {
        loaded = true;
    }

    List<DefaultMutableTreeNode> load() {
        return loader.get();
    }

    @Override
    public boolean isLeaf() {
        return false;
    }
}
