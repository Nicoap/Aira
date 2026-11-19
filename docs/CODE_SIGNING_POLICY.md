# Code signing policy

**Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by [SignPath Foundation](https://signpath.org).**

## What is signed

Windows releases only:

| Artifact | What it is | Signed |
| --- | --- | --- |
| `Aira-<version>.msi` | the installer produced by `jpackage` (`:desktopApp:packageMsi`) | yes |
| `Aira.exe` (inside the MSI) | the app launcher generated during packaging | yes (deep signing) |
| `Aira-*.apk` | Android builds, self-signed with the project's release keystore | no (Android signature, not Authenticode) |
| `Aira-<version>.dmg` | macOS build | no |
| `Aira-<version>-linux-x64.tar.gz` | Linux build | no |

## How it is built and signed

1. A release is triggered by pushing a `v*.*.*` tag (or manually via `workflow_dispatch`).
2. [`.github/workflows/release.yml`](.github/workflows/release.yml) builds everything on GitHub-hosted runners (`windows-latest`, `macos-latest`, `ubuntu-latest`). No pre-built binaries are uploaded by hand: every artifact in a release is produced from the tagged commit.
3. The MSI is uploaded from the same workflow run and submitted to SignPath, whose artifact configuration (`docs/signpath/msi-deep.xml`) signs the launcher inside the MSI and then the MSI itself.
4. Every signing request requires manual approval from a project member. CI waits for that approval before publishing.

## Roles

- **Authors** (may push to the repository): [Nicoap](https://github.com/Nicoap)
- **Reviewers** (review changes from non-committers): [Nicoap](https://github.com/Nicoap)
- **Approvers** (decide whether a release may be signed): [Nicoap](https://github.com/Nicoap)

All team members use multi-factor authentication for both GitHub and SignPath.

## Privacy

This program will not transfer any information to other networked systems unless specifically requested by the user or the person installing or operating it.

Details, including the optional Sentry crash reporting and the opt-in lyrics contribution: [README → Privacy](../README.md#privacy).

## Verifying a signature

```powershell
# Windows PowerShell
$a = Get-AuthenticodeSignature .\Aira-<version>.msi
$a.Status          # Valid
$a.SignerCertificate.Subject
```

or with `signtool`:

```cmd
signtool verify /pa /v Aira-<version>.msi
```

Note that until SignPath Foundation approves this project, releases are shipped unsigned (the CI signing step is disabled), so verification fails by design.

## Reporting

If you believe a file signed with a SignPath Foundation certificate violates the policy above, contact [support@signpath.io](mailto:support@signpath.io).
