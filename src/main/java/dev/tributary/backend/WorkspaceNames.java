package dev.tributary.backend;

public final class WorkspaceNames {

    public enum Problem {
        NONE,
        EMPTY,
        AT_SIGN,
        MULTILINE
    }

    private WorkspaceNames() {}

    public static Problem check(String name) {
        if (name == null || name.isBlank()) {
            return Problem.EMPTY;
        }
        if (name.contains("@")) {
            return Problem.AT_SIGN;
        }
        if (name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            return Problem.MULTILINE;
        }
        return Problem.NONE;
    }

    public static String suggest(String userId, String sourceName) {
        String source = sourceName == null ? "" : sourceName.replace('@', '_').strip();
        String user = userId == null ? "" : userId.replace('@', '_').strip();
        if (user.isEmpty() || source.startsWith(user + "_")) {
            return source;
        }
        return user + "_" + source;
    }
}
