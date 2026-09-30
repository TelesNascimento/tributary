package dev.tributary.cli;

import java.util.List;

public final class Model {

    private Model() {}

    public record PathChange(
            String path,
            boolean added,
            boolean deleted,
            boolean moved,
            boolean contentChanged,
            boolean propertyChanged,
            String uuid,
            String stateId,
            String beforeStateId) {

        public PathChange(
                String path,
                boolean added,
                boolean deleted,
                boolean moved,
                boolean contentChanged,
                boolean propertyChanged) {
            this(path, added, deleted, moved, contentChanged, propertyChanged, null, null, null);
        }
    }

    public record WorkItemRef(long id, String summary) {}

    public record ChangeSet(
            String uuid,
            String comment,
            String author,
            String modified,
            boolean conflict,
            boolean current,
            boolean complete,
            List<WorkItemRef> workItems,
            List<PathChange> changes) {

        public ChangeSet(
                String uuid,
                String comment,
                String author,
                String modified,
                boolean conflict,
                List<PathChange> changes) {
            this(uuid, comment, author, modified, conflict, false, true, List.of(), changes);
        }
    }

    public record Component(
            String name,
            String uuid,
            String baseline,
            boolean loaded,
            List<PathChange> unresolved,
            List<ChangeSet> incoming,
            List<ChangeSet> outgoing,
            List<ChangeSet> suspended) {}

    public record Conflict(
            String path,
            String uuid,
            boolean contentConflict,
            boolean propertyConflict,
            String outgoingType,
            String proposedType) {}

    public enum ConflictSide {
        MINE("-m"),
        PROPOSED("-p"),
        ANCESTOR("-a");

        private final String flag;

        ConflictSide(String flag) {
            this.flag = flag;
        }

        public String flag() {
            return flag;
        }
    }

    public enum Resolution {
        PROPOSED("-p"),
        CHECKED_IN("-c"),
        AUTO_MERGE("-a");

        private final String flag;

        Resolution(String flag) {
            this.flag = flag;
        }

        public String flag() {
            return flag;
        }
    }

    public record CliConnection(String nickname, String uri, String userName, boolean passwordStored) {}

    public record FlowTarget(String name, String uuid, String type, boolean currentIncoming, boolean currentOutgoing) {}

    public record RemoteWorkspace(String name, String owner, String uuid) {}

    public record Workspace(String name, String uuid, String flowTarget, String userId, List<Component> components) {}
}
