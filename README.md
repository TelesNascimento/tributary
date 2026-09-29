# Tributary

Tributary brings IBM Engineering Workflow Management (RTC) source control into IntelliJ IDEA, so day-to-day
work does not need the Eclipse client. It works on the sandbox folder directly: no *Team > Share Project* step.

> Early preview. Tributary is not affiliated with or endorsed by IBM.

## What it does

- **Sandbox detection.** Any project inside a folder with a `.jazz5` directory is mapped to the RTC version
  control system automatically.
- **Native Commit window.** Unresolved changes show up in Local Changes and Unversioned Files, with the
  standard side-by-side diff. Check in from the Commit tool window.
- **Work item context.** Pick the card you are working on once (`Alt+Shift+W`). Check-ins go into change
  sets linked to that card, so delivering is never rejected at the last second for a missing work item.
  Switching card can suspend the previous card's checked-in changes and resumes them when you come back.
- **Deliver and Accept with a preview.** Review change sets and their files, open a diff for any file, then
  confirm. Optional *Deliver after check-in* in the commit panel.
- **Overview tool window.** Outgoing, incoming and unresolved changes per component, with expandable change
  sets and diffs.
- **Undo and `.jazzignore`.** Undo through the standard Rollback action; *Add to .jazzignore* from the
  changes context menu.
- **Repository workspaces.** Browse your workspaces and streams and load a workspace into a sandbox.

## Requirements

- IntelliJ IDEA 2025.2 or later (Community or Ultimate).
- The RTC/EWM SCM command line (`scm`), version 6.0 or later, installed locally. It ships with the RTC
  Eclipse client under `scmtools/eclipse`.
- For work item search: a Java 8 runtime and the RTC client libraries from a local RTC client installation.
  Tributary never bundles or redistributes IBM code.

## Install

1. Download `tributary-<version>.zip` from the releases page.
2. In IntelliJ IDEA: *Settings > Plugins > gear icon > Install Plugin from Disk...* and pick the zip.

## Configure

1. *Settings > Version Control > Tributary*: the path to `scm.exe` is detected automatically.
2. Add your connection (server URI, user, password). The password is kept in the IDE password safe.
   Connections already stored by the `scm` command line can be imported.
3. Open a project that lives inside a sandbox. The status bar shows the current card and the number of
   outgoing and incoming change sets.

## Build from source

```
gradlew.bat test buildPlugin
```

The plugin zip is written to `build/distributions`. `gradlew.bat runIde` opens a sandbox IDE with the plugin.
Formatting is enforced with Spotless (`gradlew.bat spotlessApply`).

The build needs a JDK 25 to run Gradle, a local IntelliJ IDEA installation (set `idePath` in
`gradle.properties`) and, for the work item bridge only, the RTC client libraries (`ibmPlugins`).

## Project layout

| Path | Contents |
|---|---|
| `src/main/java/dev/tributary/cli` | `scm` process runner, JSON parsers, error model |
| `src/main/java/dev/tributary/backend` | `RtcBackend` contract and its command line implementation |
| `src/main/java/dev/tributary/context` | work item context engine and service |
| `src/main/java/dev/tributary/vcs` | VCS integration: changes, commit, rollback |
| `src/main/java/dev/tributary/ui` | tool window, dialogs, status bar widget |
| `bridge` | small Java 8 process that talks to the RTC client API for work items |
| `tools` | scripts to capture and sanitize test fixtures |

## License

MIT. See `LICENSE`.
