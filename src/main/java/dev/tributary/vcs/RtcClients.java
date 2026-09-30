package dev.tributary.vcs;

import dev.tributary.TributaryBundle;
import dev.tributary.backend.CliBackend;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.CliLocator;
import dev.tributary.cli.CliRunner;
import dev.tributary.cli.RtcException;
import dev.tributary.settings.TributarySettings;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class RtcClients {

    private RtcClients() {}

    public static RtcBackend forSandbox(Path sandboxRoot) throws RtcException {
        TributarySettings.Data settings = TributarySettings.getInstance().getState();
        Path cli = CliLocator.find(settings.cliPath)
                .orElseThrow(() ->
                        new RtcException(RtcException.Kind.CLI_MISSING, TributaryBundle.message("error.cli.missing")));
        return new CliBackend(new CliRunner(cli, charset(settings.cliCharset)), sandboxRoot);
    }

    public static RtcBackend global() throws RtcException {
        return forSandbox(Path.of(System.getProperty("user.home")));
    }

    public static String serverUri() {
        return TributarySettings.getInstance().serverUri();
    }

    private static Charset charset(String name) {
        try {
            return Charset.forName(name);
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }
}
