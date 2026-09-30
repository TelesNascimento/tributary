package dev.tributary.workitem;

public record WorkItem(long id, String summary, String state, boolean resolved, String owner, String type) {

    public String label() {
        return id + ": " + summary;
    }
}
