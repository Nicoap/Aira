# Заявка в SignPath Foundation (бесплатная подпись для open-source)

Аккаунт: https://signpath.io/ → «Sign up» → затем https://signpath.io/signpath-foundation
Проект в SignPath: `aira`

Перед заявкой нужно, чтобы проект был публичным (сейчас репозиторий private).

---

## Текст заявки (английский, вставлять как есть)

**Project name**
Aira

**Repository URL**
https://github.com/Nicoap/Aira

**Project description (short, for the form)**
Aira is a free, open-source YouTube Music client for Windows (desktop, Compose
Multiplatform) and Android. It plays music from YouTube Music without ads or
tracking, supports synchronized lyrics, crossfade, a custom theme system,
Last.fm/ListenBrainz scrobbling and offline downloads.

**How the project is distributed**
Windows: an `.msi` installer built with `jpackage` in GitHub Actions
(`gradlew :desktopApp:packageMsi`) and published as a GitHub Release asset.
Android: signed APKs (per-ABI) built in the same workflow.

**Why we need code signing**
The Windows launcher produced by `jpackage` is unsigned, so Microsoft Defender
repeatedly flags it as `Trojan:Win32/Beafloos.A!ml` (a machine-learning
heuristic false positive; the same detection fires on the upstream project this
fork is based on, and on the launcher stub generated during packaging itself).
SmartScreen also shows "Unknown publisher". We cannot afford a commercial
code-signing certificate as a hobby project, so we are applying to the
SignPath Foundation programme for open-source projects.

**Licence**
GNU General Public License v3.0 (`LICENSE` in the repository). Aira is a fork of
Wavora (https://github.com/Wavora-dev/Wavora), which itself grew out of
SimpMusic; all upstream copyright notices are preserved.

**Build system**
GitHub Actions, `.github/workflows/release.yml`. All jobs run on GitHub-hosted
runners (`ubuntu-latest`, `windows-latest`). The signing request is submitted
from the `desktop` job with
`signpath/github-action-submit-signing-request@v3`
(`artifact-configuration-slug: msi-deep`, `skip-decompress: true`).

**Artifacts to sign**
`Aira-<version>.msi` — and, inside it, the launcher `Aira.exe` (deep signing,
see `docs/signpath/msi-deep.xml`).

**Contact**
GitHub: @Nicoap · e-mail: lolneknikita@gmail.com

---

## Что сделать после одобрения

1. В SignPath: Organization → Trusted Build Systems → добавить **GitHub.com**
   и подключить его к проекту `aira`.
2. Установить [SignPath GitHub App](https://github.com/apps/signpath) на
   репозиторий (нужно для аудита и для приватных репозиториев).
3. Создать Artifact Configuration со slug `msi-deep`
   (содержимое — `docs/signpath/msi-deep.xml`).
4. Signing Policy со slug `release-signing`.
5. Получить API-токен пользователя с правом submitter и положить в секреты
   репозитория:
   - `SIGNPATH_API_TOKEN` — сам токен
   - `SIGNPATH_ORGANIZATION_ID` — id организации из SignPath (Settings → Organization)
6. Перезапустить `release.yml`. В логе шага «Выбрать MSI для релиза» должно
   появиться `подписанный MSI: Aira-<version>.msi, статус подписи: Valid`.
   Пока секретов нет — шаг подписи пропускается и уходит неподписанный MSI.
