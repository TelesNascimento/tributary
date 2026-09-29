package dev.tributary.workitem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tributary.cli.RtcException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BridgeWorkItems implements WorkItemDirectory {

    public interface Channel {
        JsonElement call(String method, JsonObject params) throws RtcException;
    }

    private final Channel channel;

    public BridgeWorkItems(Channel channel) {
        this.channel = channel;
    }

    @Override
    public List<WorkItem> search(String text, boolean mineOnly, boolean includeResolved, int max) throws RtcException {
        JsonObject params = new JsonObject();
        params.addProperty("text", text == null ? "" : text);
        params.addProperty("mineOnly", mineOnly);
        params.addProperty("includeResolved", includeResolved);
        params.addProperty("max", max);
        return parseList(channel.call("searchWorkItems", params));
    }

    @Override
    public Optional<WorkItem> get(long id) throws RtcException {
        JsonObject params = new JsonObject();
        params.addProperty("id", id);
        try {
            return Optional.of(parse(channel.call("getWorkItem", params).getAsJsonObject()));
        } catch (RtcException e) {
            if (e.kind() == RtcException.Kind.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    static List<WorkItem> parseList(JsonElement result) {
        List<WorkItem> items = new ArrayList<>();
        JsonArray array = result != null && result.isJsonArray() ? result.getAsJsonArray() : new JsonArray();
        for (JsonElement element : array) {
            items.add(parse(element.getAsJsonObject()));
        }
        return items;
    }

    static WorkItem parse(JsonObject json) {
        return new WorkItem(
                json.get("id").getAsLong(),
                text(json, "summary"),
                text(json, "state"),
                json.has("resolved") && json.get("resolved").getAsBoolean(),
                text(json, "owner"),
                text(json, "type"));
    }

    private static String text(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
}
