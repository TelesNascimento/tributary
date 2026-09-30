# Contributing

Thanks for helping. Tributary is a small project; a short issue before a large change saves everyone time.

## Build

Requirements: JDK 25 to run Gradle (the plugin is compiled with `--release 21`), and Git.

```
./gradlew spotlessCheck test buildPlugin
```

- Without further setup the build downloads IntelliJ IDEA Community 2025.2 and produces a plugin without the
  work item bridge.
- To build against your own IDE, set `idePath` in `gradle.properties` (or pass `-PidePath=...`).
- To build the work item bridge, set `ibmPlugins` to the `plugins` folder of a local RTC client installation.
  The bridge is compiled against those libraries and never redistributes them.
- `./gradlew runIde` opens a sandbox IDE with the plugin.

## Code style

- Formatting is Palantir Java Format, enforced by Spotless: run `./gradlew spotlessApply` before committing.
- Every user-facing string lives in `TributaryBundle.properties`; the UI is in English.
- The IDE thread never calls the backend. Use `TributaryTasks` for background work and report failures with
  `TributaryNotifier`.
- Classify command line failures by exit code (`ErrorCodes`), never by matching message text.
- Keep comments for the non-obvious "why"; do not restate the code.

## Tests and fixtures

- Automated tests never contact a server. They run against recorded output and fake `scm` scripts.
- Fixtures under `src/test/resources/fixtures` must be sanitized. Capture with `tools/capture-fixtures.ps1` and
  run `tools/sanitize-fixtures.py`; keep your site-specific rules in the untracked
  `tools/sanitize-rules.local.json` (see `tools/sanitize-rules.example.json`).
- Never commit real server output, host names, user ids or work item numbers.

## Commits and pull requests

- Small, focused commits with an imperative subject line ("Add merge provider for conflicts").
- Describe the behaviour change and how you verified it in the pull request.

## Releasing

Releases are built on a machine that has the RTC client libraries, so that the work item bridge is included:

1. Update `CHANGELOG.md` and the version in `build.gradle.kts`.
2. `./gradlew spotlessCheck test buildPlugin verifyPlugin`.
3. Sign the zip (`signPlugin`) with your own certificate chain and private key supplied through the
   `CERTIFICATE_CHAIN`, `PRIVATE_KEY` and `PRIVATE_KEY_PASSWORD` environment variables.
4. Tag `vX.Y.Z` and attach the signed zip to the GitHub release.
