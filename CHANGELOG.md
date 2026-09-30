# Changelog

All notable changes are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Sandbox detection by the `.jazz5` folder; no *Team > Share Project* step.
- Native Commit window, Local Changes, diff and Rollback for RTC sandboxes.
- Work item context: pick a card once, and check-ins go into change sets linked to it. Switching cards can
  suspend and resume the previous card's changes.
- Deliver and Accept dialogs with a per-file diff preview; optional *Deliver after check-in*.
- Overview tool window with outgoing, incoming, suspended and unresolved changes per component.
- Native three-way merge for conflicts.
- `.jazzignore` support (recursive and non-recursive rules) and an *Add to .jazzignore* action.
- Status bar widget showing the current card and the outgoing and incoming counts.
- Repository workspace browser and load dialog.
- Create a repository workspace from a stream or another workspace, optionally loading it right away, and
  unload a workspace from the open sandbox with or without deleting its files.
- Work item search through a local Java 8 bridge that uses the RTC client libraries installed on the machine.
