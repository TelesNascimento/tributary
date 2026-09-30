import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FakeBridge {

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        Pattern idPattern = Pattern.compile("\"id\":(\\d+)");
        String line;
        while ((line = in.readLine()) != null) {
            Matcher matcher = idPattern.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String id = matcher.group(1);
            if (line.contains("\"shutdown\"")) {
                System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":\"bye\"}");
                return;
            }
            if (line.contains("\"searchWorkItems\"")) {
                System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":[{\"id\":9000001,"
                        + "\"summary\":\"Sample work item\",\"state\":\"In Progress\",\"resolved\":false,"
                        + "\"owner\":\"User One\",\"type\":\"task\"}]}");
            } else if (line.contains("\"getWorkItem\"") && line.contains("404")) {
                System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id
                        + ",\"error\":{\"code\":-32000,\"message\":\"not found\",\"data\":{\"kind\":\"NOT_FOUND\"}}}");
            } else if (line.contains("\"crash\"")) {
                System.exit(3);
            } else if (line.contains("\"hang\"")) {
                continue;
            } else {
                System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id
                        + ",\"error\":{\"code\":-32000,\"message\":\"failed\",\"data\":{\"kind\":\"AUTH_REQUIRED\"}}}");
            }
            System.out.flush();
        }
    }
}
