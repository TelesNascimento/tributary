package dev.tributary.ignore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JazzIgnore {

    public static final String FILE_NAME = ".jazzignore";

    private static final Pattern RULE_LINE =
            Pattern.compile("^\\s*core\\.ignore(\\.recursive)?\\s*=\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP = Pattern.compile("\\{([^}]*)}");

    public record Rule(boolean recursive, List<Pattern> patterns) {

        boolean matches(String name) {
            return patterns.stream().anyMatch(p -> p.matcher(name).matches());
        }
    }

    private JazzIgnore() {}

    public static List<Rule> parse(String content) {
        List<Rule> rules = new ArrayList<>();
        for (String line : logicalLines(content)) {
            Matcher rule = RULE_LINE.matcher(line);
            if (!rule.matches()) {
                continue;
            }
            List<Pattern> patterns = new ArrayList<>();
            Matcher group = GROUP.matcher(rule.group(2));
            while (group.find()) {
                for (String glob : group.group(1).trim().split("\\s+")) {
                    if (!glob.isEmpty()) {
                        patterns.add(Pattern.compile(globToRegex(glob)));
                    }
                }
            }
            rules.add(new Rule(rule.group(1) != null, patterns));
        }
        return rules;
    }

    public static boolean isIgnored(Path root, Path file, Function<Path, Optional<String>> readIgnoreFile) {
        Path base = root.toAbsolutePath().normalize();
        Path target = file.toAbsolutePath().normalize();
        if (!target.startsWith(base) || target.equals(base)) {
            return false;
        }
        Path relative = base.relativize(target);
        Path directory = base;
        for (int i = 0; i < relative.getNameCount(); i++) {
            String name = relative.getName(i).toString();
            Path ancestor = base;
            for (int j = 0; j <= i; j++) {
                for (Rule rule :
                        parse(readIgnoreFile.apply(ancestor.resolve(FILE_NAME)).orElse(""))) {
                    if ((ancestor.equals(directory) || rule.recursive()) && rule.matches(name)) {
                        return true;
                    }
                }
                if (j < i) {
                    ancestor = ancestor.resolve(relative.getName(j));
                }
            }
            directory = directory.resolve(name);
        }
        return false;
    }

    public static String add(String existing, String pattern, boolean recursive) {
        String key = recursive ? "core.ignore.recursive" : "core.ignore";
        String entry = "{" + pattern + "}";
        List<String> lines = new ArrayList<>(List.of(existing.split("\\R", -1)));
        for (int i = 0; i < lines.size(); i++) {
            Matcher rule = RULE_LINE.matcher(lines.get(i));
            if (rule.matches()
                    && (rule.group(1) != null) == recursive
                    && !lines.get(i).endsWith("\\")) {
                if (lines.get(i).contains(entry)) {
                    return existing;
                }
                lines.set(i, lines.get(i) + " " + entry);
                return String.join("\n", lines);
            }
        }
        String prefix = existing.isEmpty() || existing.endsWith("\n") ? existing : existing + "\n";
        return prefix + key + " = " + entry + "\n";
    }

    private static List<String> logicalLines(String content) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = null;
        for (String raw : content.split("\\R")) {
            String line = current == null ? raw : raw.stripLeading();
            boolean continued = line.endsWith("\\");
            String text = continued ? line.substring(0, line.length() - 1) : line;
            current = current == null
                    ? new StringBuilder(text)
                    : current.append(' ').append(text);
            if (!continued) {
                lines.add(current.toString());
                current = null;
            }
        }
        if (current != null) {
            lines.add(current.toString());
        }
        return lines.stream().filter(l -> !l.stripLeading().startsWith("#")).toList();
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append("[^/]*");
                case '?' -> regex.append("[^/]");
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return regex.toString();
    }
}
