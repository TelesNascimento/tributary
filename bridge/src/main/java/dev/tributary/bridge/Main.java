package dev.tributary.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class Main {

    private static final Gson GSON = new Gson();

    private Main() {}

    public static void main(String[] args) throws Exception {
        PrintStream protocol = new PrintStream(System.out, true, "UTF-8");
        System.setOut(System.err);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Handlers handlers = new Handlers();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            JsonElement id = null;
            try {
                JsonObject request = JsonParser.parseString(line).getAsJsonObject();
                id = request.get("id");
                String method = request.get("method").getAsString();
                JsonObject params =
                        request.has("params") && request.get("params").isJsonObject()
                                ? request.getAsJsonObject("params")
                                : new JsonObject();
                if ("shutdown".equals(method)) {
                    protocol.println(result(id, GSON.toJsonTree("bye")));
                    handlers.close();
                    return;
                }
                protocol.println(result(id, handlers.handle(method, params)));
            } catch (BridgeException e) {
                protocol.println(error(id, e.kind(), e.getMessage()));
            } catch (Throwable t) {
                protocol.println(error(id, "FAILED", t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        }
        handlers.close();
    }

    private static String result(JsonElement id, JsonElement value) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", value);
        return GSON.toJson(response);
    }

    private static String error(JsonElement id, String kind, String message) {
        JsonObject data = new JsonObject();
        data.addProperty("kind", kind);
        JsonObject err = new JsonObject();
        err.addProperty("code", -32000);
        err.addProperty("message", message);
        err.add("data", data);
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("error", err);
        return GSON.toJson(response);
    }
}
