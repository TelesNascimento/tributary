package dev.tributary.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UnifiedPatch {

    private static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    private UnifiedPatch() {}

    private record Op(char kind, String text) {}

    private record Hunk(int newStart, int newLen, List<Op> ops) {}

    public static String reverse(String local, String patch) {
        String eol = local.contains("\r\n") ? "\r\n" : "\n";
        List<String> localLines = splitKeepingEol(local);
        StringBuilder base = new StringBuilder();
        int index = 0;
        for (Hunk hunk : hunks(patch)) {
            int target = hunk.newLen() == 0 ? hunk.newStart() : hunk.newStart() - 1;
            while (index < target && index < localLines.size()) {
                base.append(localLines.get(index++));
            }
            for (Op op : hunk.ops()) {
                switch (op.kind()) {
                    case ' ' -> {
                        if (index < localLines.size()) {
                            base.append(localLines.get(index++));
                        }
                    }
                    case '+' -> index++;
                    case '-' -> base.append(op.text()).append(eol);
                    default -> {}
                }
            }
        }
        while (index < localLines.size()) {
            base.append(localLines.get(index++));
        }
        return base.toString();
    }

    private static List<Hunk> hunks(String patch) {
        String[] lines = patch.split("\n", -1);
        List<Hunk> hunks = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            Matcher m = HUNK.matcher(stripCr(lines[i]));
            if (!m.matches()) {
                i++;
                continue;
            }
            int oldLen = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
            int newLen = m.group(4) == null ? 1 : Integer.parseInt(m.group(4));
            int newStart = Integer.parseInt(m.group(3));
            i++;
            List<Op> ops = new ArrayList<>();
            int seenOld = 0;
            int seenNew = 0;
            while (i < lines.length && (seenOld < oldLen || seenNew < newLen)) {
                String line = stripCr(lines[i]);
                if (line.startsWith("\\")) {
                    i++;
                    continue;
                }
                char kind = line.isEmpty() ? ' ' : line.charAt(0);
                String text = line.isEmpty() ? "" : line.substring(1);
                if (kind == ' ') {
                    seenOld++;
                    seenNew++;
                } else if (kind == '-') {
                    seenOld++;
                } else if (kind == '+') {
                    seenNew++;
                } else {
                    break;
                }
                ops.add(new Op(kind, text));
                i++;
            }
            hunks.add(new Hunk(newStart, newLen, ops));
        }
        return hunks;
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static List<String> splitKeepingEol(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }
}
