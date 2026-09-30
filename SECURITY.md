# Security policy

## Supported versions

Only the latest release receives security fixes.

## Reporting a vulnerability

Please report vulnerabilities privately through
[GitHub security advisories](https://github.com/TelesNascimento/tributary/security/advisories/new).
Do not open a public issue. You can expect an initial answer within a week.

## How Tributary handles credentials

- Passwords are stored only in the IDE password safe.
- The `scm login` command receives the password on standard input, never as a command line argument.
- Error details shown to the user and written to logs have passwords and tokens redacted.
- Tributary does not send telemetry and only contacts the RTC servers you configure.
- The work item bridge is a local child process; it is started with the client libraries already installed on
  your machine and nothing from IBM is bundled or downloaded.
