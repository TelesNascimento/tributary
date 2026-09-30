package dev.tributary.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.Nullable;

public final class Credentials {

    private Credentials() {}

    private static CredentialAttributes attributes(String uri, String userId) {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName("Tributary", uri + "|" + userId), userId);
    }

    public static void save(String uri, String userId, String password) {
        PasswordSafe.getInstance()
                .set(attributes(uri, userId), new com.intellij.credentialStore.Credentials(userId, password));
    }

    public static @Nullable String password(String uri, String userId) {
        return PasswordSafe.getInstance().getPassword(attributes(uri, userId));
    }

    public static void forget(String uri, String userId) {
        PasswordSafe.getInstance().set(attributes(uri, userId), null);
    }
}
