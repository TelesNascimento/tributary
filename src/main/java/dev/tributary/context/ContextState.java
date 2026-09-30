package dev.tributary.context;

import java.util.ArrayList;
import java.util.List;

public final class ContextState {

    public static final class Entry {
        public long workItemId;
        public String summary = "";
        public String workspace = "";
        public List<String> changeSets = new ArrayList<>();
        public String currentChangeSet = "";
        public String currentComment = "";
        public boolean suspended;
        public long lastUsedMillis;

        public Entry() {}

        public Entry(long workItemId, String summary, String workspace) {
            this.workItemId = workItemId;
            this.summary = summary;
            this.workspace = workspace;
        }

        public String label() {
            return workItemId + ": " + summary;
        }
    }

    public List<Entry> contexts = new ArrayList<>();
    public long activeWorkItem;
}
