package dev.tributary.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommitMessage {

    private static final Pattern LEADING_WORK_ITEM = Pattern.compile(
            "^\\s*(?:#|WI\\s*|RTC\\s*)?(\\d{5,8})\\s*[:\\-]?\\s*(.*)$", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public record Parsed(String comment, String workItem) {}

    private CommitMessage() {}

    public static Parsed parse(String message) {
        String text = message == null ? "" : message.trim();
        Matcher m = LEADING_WORK_ITEM.matcher(text);
        if (m.matches() && !m.group(2).isBlank()) {
            return new Parsed(m.group(2).trim(), m.group(1));
        }
        return new Parsed(text, null);
    }
}
