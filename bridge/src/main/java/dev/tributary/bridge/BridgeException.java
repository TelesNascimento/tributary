package dev.tributary.bridge;

final class BridgeException extends Exception {

    private final String kind;

    BridgeException(String kind, String message) {
        super(message);
        this.kind = kind;
    }

    String kind() {
        return kind;
    }
}
