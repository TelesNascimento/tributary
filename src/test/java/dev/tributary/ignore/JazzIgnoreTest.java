package dev.tributary.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class JazzIgnoreTest {

    private static final Path ROOT = Path.of("sandbox", "app").toAbsolutePath();

    private static Function<Path, Optional<String>> files(Map<String, String> byRelativeDir) {
        return path -> byRelativeDir.entrySet().stream()
                .filter(e -> ROOT.resolve(e.getKey())
                        .resolve(JazzIgnore.FILE_NAME)
                        .normalize()
                        .equals(path.normalize()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    @Test
    void nonRecursiveRulesOnlyApplyToTheirOwnFolder() {
        var reader = files(Map.of("", "core.ignore = {*.iml} {notes.txt}\n"));
        assertTrue(JazzIgnore.isIgnored(ROOT, ROOT.resolve("app.iml"), reader));
        assertTrue(JazzIgnore.isIgnored(ROOT, ROOT.resolve("notes.txt"), reader));
        assertFalse(JazzIgnore.isIgnored(ROOT, ROOT.resolve("sub").resolve("app.iml"), reader));
        assertFalse(JazzIgnore.isIgnored(ROOT, ROOT.resolve("Main.java"), reader));
    }

    @Test
    void recursiveRulesApplyToSubfolders() {
        var reader = files(Map.of("", "core.ignore.recursive = {*.class} {target}\n"));
        assertTrue(JazzIgnore.isIgnored(ROOT, ROOT.resolve("a").resolve("B.class"), reader));
        assertTrue(
                JazzIgnore.isIgnored(ROOT, ROOT.resolve("x").resolve("target").resolve("y.txt"), reader));
        assertFalse(JazzIgnore.isIgnored(ROOT, ROOT.resolve("x").resolve("y.txt"), reader));
    }

    @Test
    void rulesFromNestedIgnoreFilesApplyBelowThem() {
        var reader = files(Map.of("sub", "core.ignore = {generated}\n"));
        assertTrue(JazzIgnore.isIgnored(ROOT, ROOT.resolve("sub").resolve("generated"), reader));
        assertFalse(JazzIgnore.isIgnored(ROOT, ROOT.resolve("generated"), reader));
    }

    @Test
    void continuationLinesAreJoined() {
        var rules = JazzIgnore.parse("core.ignore = {a.txt} \\\n\t{b.txt}\ncore.ignore.recursive = {c.txt}\n");
        assertEquals(2, rules.size());
        assertEquals(2, rules.get(0).patterns().size());
        assertTrue(rules.get(1).recursive());
    }

    @Test
    void commentsAndUnknownLinesAreIgnored() {
        assertTrue(JazzIgnore.parse("# core.ignore = {x}\nsomething = {y}\n").isEmpty());
    }

    @Test
    void addAppendsToTheMatchingRuleOrCreatesOne() {
        assertEquals("core.ignore = {a} {b}", JazzIgnore.add("core.ignore = {a}", "b", false));
        assertEquals(
                "core.ignore = {a}\ncore.ignore.recursive = {b}\n", JazzIgnore.add("core.ignore = {a}\n", "b", true));
        assertEquals("core.ignore = {a}", JazzIgnore.add("core.ignore = {a}", "a", false));
        assertEquals("core.ignore = {x}\n", JazzIgnore.add("", "x", false));
    }

    @Test
    void filesOutsideTheSandboxAreNeverIgnored() {
        var reader = files(Map.of("", "core.ignore.recursive = {*}\n"));
        assertFalse(JazzIgnore.isIgnored(ROOT, ROOT.getParent().resolve("other.txt"), reader));
    }
}
