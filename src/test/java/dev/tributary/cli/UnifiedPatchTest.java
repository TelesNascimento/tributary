package dev.tributary.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UnifiedPatchTest {

    private static String fixture(String name) throws IOException {
        try (var in = UnifiedPatchTest.class.getResourceAsStream("/fixtures/" + name)) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.startsWith("﻿") ? text.substring(1) : text;
        }
    }

    @Test
    void reversesRealPatchToRecoverBase() throws Exception {
        String base = UnifiedPatch.reverse(fixture("diff_patch_local.txt"), fixture("diff_patch.txt"));
        assertEquals("package br.example.acme.p100.servico.sample_app.v1.dto;\n", base);
    }

    @Test
    void reversesModificationAndDeletionHunks() {
        String local = "a\nB\nc\nd\n";
        String patch = "@@ -1,4 +1,3 @@\n a\n-b\n+B\n c\n-x\n d\n";
        assertEquals("a\nb\nc\nx\nd\n", UnifiedPatch.reverse(local, patch));
    }

    @Test
    void recoversDeletedFileFromMinusLines() {
        String patch = "@@ -1,2 +0,0 @@\n-um\n-dois\n";
        assertEquals("um\ndois\n", UnifiedPatch.reverse("", patch));
    }
}
