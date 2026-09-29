package dev.tributary.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tributary.cli.Model.ChangeSet;
import dev.tributary.cli.Model.CliConnection;
import dev.tributary.cli.Model.Component;
import dev.tributary.cli.Model.Conflict;
import dev.tributary.cli.Model.FlowTarget;
import dev.tributary.cli.Model.PathChange;
import dev.tributary.cli.Model.RemoteWorkspace;
import dev.tributary.cli.Model.WorkItemRef;
import dev.tributary.cli.Model.Workspace;
import java.util.ArrayList;
import java.util.List;

public final class StatusParser {

    private StatusParser() {}

    public static List<Workspace> parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Workspace> result = new ArrayList<>();
        for (JsonElement element : array(root, "workspaces")) {
            result.add(workspace(element.getAsJsonObject()));
        }
        return result;
    }

    public static List<ChangeSet> parseChangeSets(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return changeSets(root, "changes");
    }

    public static List<RemoteWorkspace> parseWorkspaceList(String json) {
        List<RemoteWorkspace> list = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject ws = element.getAsJsonObject();
            list.add(new RemoteWorkspace(text(ws, "name"), text(ws, "owner"), text(ws, "uuid")));
        }
        return list;
    }

    public static List<CliConnection> parseConnections(String json) {
        List<CliConnection> list = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject connection = element.getAsJsonObject();
            String password = text(connection, "password");
            boolean stored = password != null
                    && !password.toLowerCase().contains("no password")
                    && !password.toLowerCase().contains("nenhuma");
            list.add(new CliConnection(
                    text(connection, "repoNickName"), text(connection, "url"), text(connection, "userName"), stored));
        }
        return list;
    }

    public static List<FlowTarget> parseFlowTargets(String json) {
        List<FlowTarget> list = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject ft = element.getAsJsonObject();
            list.add(new FlowTarget(
                    text(ft, "name"),
                    text(ft, "uuid"),
                    text(ft, "type"),
                    bool(ft, "current-incoming"),
                    bool(ft, "current-outgoing")));
        }
        return list;
    }

    public static String firstUuid(String output) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"uuid\"\\s*:\\s*\"(_[A-Za-z0-9_-]{22})\"")
                .matcher(output);
        if (m.find()) {
            return m.group(1);
        }
        m = java.util.regex.Pattern.compile("\\b(_[A-Za-z0-9_-]{22})\\b").matcher(output);
        return m.find() ? m.group(1) : null;
    }

    public static List<RemoteWorkspace> parseNamedItems(String json) {
        return parseWorkspaceList(json);
    }

    public static List<String> parseComponentNames(String json) {
        List<String> names = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (JsonElement ws : array(root, "workspaces")) {
            for (JsonElement component : array(ws.getAsJsonObject(), "components")) {
                names.add(text(component.getAsJsonObject(), "name"));
            }
        }
        return names;
    }

    private static Workspace workspace(JsonObject ws) {
        List<Component> components = new ArrayList<>();
        for (JsonElement element : array(ws, "components")) {
            components.add(component(element.getAsJsonObject()));
        }
        return new Workspace(text(ws, "name"), text(ws, "uuid"), flowTargetName(ws), text(ws, "userId"), components);
    }

    private static String flowTargetName(JsonObject owner) {
        JsonElement target = owner.get("flow-target");
        return target != null && target.isJsonObject() ? text(target.getAsJsonObject(), "name") : null;
    }

    private static Component component(JsonObject c) {
        String baseline = null;
        JsonElement base = c.get("baseline");
        if (base != null && base.isJsonObject()) {
            baseline = text(base.getAsJsonObject(), "name");
        }
        boolean loaded = !c.has("is_comp_loaded") || c.get("is_comp_loaded").getAsBoolean();
        return new Component(
                text(c, "name"),
                text(c, "uuid"),
                baseline,
                loaded,
                pathChanges(c, "unresolved"),
                changeSets(c, "incoming-changes"),
                changeSets(c, "outgoing-changes"),
                changeSets(c, "suspended"));
    }

    public static List<Conflict> parseConflicts(String json) {
        List<Conflict> conflicts = new ArrayList<>();
        for (JsonElement element : array(JsonParser.parseString(json).getAsJsonObject(), "conflicts")) {
            JsonObject conflict = element.getAsJsonObject();
            JsonObject state = conflict.has("state") && conflict.get("state").isJsonObject()
                    ? conflict.getAsJsonObject("state")
                    : new JsonObject();
            conflicts.add(new Conflict(
                    normalizePath(text(conflict, "path-hint")),
                    text(conflict, "uuid"),
                    bool(state, "content_conflict"),
                    bool(state, "property_conflict"),
                    text(conflict, "type-outgoing"),
                    text(conflict, "type-proposed")));
        }
        return conflicts;
    }

    public static String parseConflictContent(String output) {
        int marker = output.indexOf("Content:");
        if (marker < 0) {
            return "";
        }
        String rest = output.substring(marker + "Content:".length());
        if (rest.startsWith("\r\n")) {
            rest = rest.substring(2);
        } else if (rest.startsWith("\n")) {
            rest = rest.substring(1);
        }
        if (rest.startsWith("  ")) {
            rest = rest.substring(2);
        }
        return rest.endsWith("\n\n") ? rest.substring(0, rest.length() - 1) : rest;
    }

    private static List<ChangeSet> changeSets(JsonObject owner, String key) {
        List<ChangeSet> list = new ArrayList<>();
        for (JsonElement element : array(owner, key)) {
            JsonObject cs = element.getAsJsonObject();
            JsonObject state =
                    cs.has("state") && cs.get("state").isJsonObject() ? cs.getAsJsonObject("state") : new JsonObject();
            list.add(new ChangeSet(
                    text(cs, "uuid"),
                    text(cs, "comment"),
                    text(cs, "author"),
                    text(cs, "modified"),
                    bool(state, "conflict"),
                    bool(state, "current"),
                    !state.has("complete") || bool(state, "complete"),
                    workItems(cs),
                    pathChanges(cs, "changes")));
        }
        return list;
    }

    private static List<WorkItemRef> workItems(JsonObject changeSet) {
        List<WorkItemRef> refs = new ArrayList<>();
        JsonElement element = changeSet.get("workitems");
        if (element == null || element.isJsonNull()) {
            return refs;
        }
        JsonArray items = element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
        if (element.isJsonObject()) {
            items.add(element);
        }
        for (JsonElement item : items) {
            JsonObject wi = item.getAsJsonObject();
            JsonElement id = wi.has("workitem-number") ? wi.get("workitem-number") : wi.get("id");
            if (id != null && !id.isJsonNull()) {
                refs.add(new WorkItemRef(id.getAsLong(), text(wi, "workitem-label")));
            }
        }
        return refs;
    }

    private static List<PathChange> pathChanges(JsonObject owner, String key) {
        List<PathChange> list = new ArrayList<>();
        for (JsonElement element : array(owner, key)) {
            JsonObject change = element.getAsJsonObject();
            JsonObject state = change.has("state") && change.get("state").isJsonObject()
                    ? change.getAsJsonObject("state")
                    : new JsonObject();
            list.add(new PathChange(
                    normalizePath(text(change, "path")),
                    bool(state, "add"),
                    bool(state, "delete"),
                    bool(state, "move"),
                    bool(state, "content_change"),
                    bool(state, "property_change"),
                    text(change, "uuid"),
                    text(change, "state-id"),
                    text(change, "before-state")));
        }
        return list;
    }

    static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static JsonArray array(JsonObject owner, String key) {
        JsonElement element = owner.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static String text(JsonObject owner, String key) {
        JsonElement element = owner.get(key);
        return element == null || element.isJsonNull() ? null : TextRepair.repairMojibake(element.getAsString());
    }

    private static boolean bool(JsonObject owner, String key) {
        JsonElement element = owner.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }
}
