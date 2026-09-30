package dev.tributary.cli;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class TextRepair {

    private TextRepair() {}

    public static String repairMojibake(String text) {
        if (text == null || text.chars().noneMatch(c -> c == 0xC3 || c == 0xC2)) {
            return text;
        }
        if (text.chars().anyMatch(c -> c > 0xFF)) {
            return text;
        }
        try {
            String repaired = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(text.getBytes(StandardCharsets.ISO_8859_1)))
                    .toString();
            return repaired;
        } catch (CharacterCodingException e) {
            return text;
        }
    }
}
