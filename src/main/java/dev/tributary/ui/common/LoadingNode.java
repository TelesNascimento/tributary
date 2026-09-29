package dev.tributary.ui.common;

import dev.tributary.TributaryBundle;
import javax.swing.tree.DefaultMutableTreeNode;

public final class LoadingNode extends DefaultMutableTreeNode {

    public LoadingNode() {
        this(TributaryBundle.message("tree.loading"));
    }

    public LoadingNode(String text) {
        super(text);
    }

    @Override
    public boolean isLeaf() {
        return true;
    }
}
