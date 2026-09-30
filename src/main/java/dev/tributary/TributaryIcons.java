package dev.tributary;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

public final class TributaryIcons {

    public static final Icon TOOL_WINDOW = load("toolWindow");
    public static final Icon WORKSPACE = load("workspace");
    public static final Icon STREAM = load("stream");
    public static final Icon COMPONENT = load("component");
    public static final Icon CHANGE_SET = load("changeSet");
    public static final Icon CHANGE_SET_CURRENT = load("changeSetCurrent");
    public static final Icon BASELINE = load("baseline");
    public static final Icon SNAPSHOT = load("snapshot");
    public static final Icon WORK_ITEM = load("workItem");
    public static final Icon INCOMING = load("incoming");
    public static final Icon OUTGOING = load("outgoing");
    public static final Icon CONFLICT = load("conflict");
    public static final Icon SUSPENDED = load("suspended");

    private TributaryIcons() {}

    private static Icon load(String name) {
        return IconLoader.getIcon("/icons/" + name + ".svg", TributaryIcons.class);
    }
}
