# Заявка в SignPath Foundation (бесплатная подпись для open-source)

Заявка подаётся **не в панели signpath.io**, а на сайте фонда: <https://signpath.org/apply>.
Условия, которые там проверяют: <https://signpath.org/terms.html>.

Проект в SignPath (панель): `aira` (VALID). Репозиторий: <https://github.com/Nicoap/Aira> (public, GPL-3.0).

---

## Что уже сделано в репозитории под их требования

| Требование фонда | Где закрыто |
| --- | --- |
| OSI-лицензия без коммерческого dual-licensing | `LICENSE` — GPL-3.0 |
| Публичный репозиторий, проект выпущен | релизы `v1.0.1`, `v1.0.2` |
| Описание функциональности на странице загрузки | `README.md` |
| «Code signing policy» на главной странице | секция `## Code signing policy` в `README.md` + `docs/CODE_SIGNING_POLICY.md` |
| Сборка проверяемым образом на GitHub-hosted раннерах | `.github/workflows/release.yml` |
| Метаданные в artifact configuration | `docs/signpath/msi-deep.xml` (`product-name="Aira"` на `Aira.exe`) |
| MFA у участников | включить 2FA на GitHub-аккаунте @Nicoap |

## Что нужно понимать до подачи

1. **Издатель в подписи — SignPath Foundation**, не Nicoap. В свойствах файла будет написано
   «SignPath Foundation». Это нормально для их программы и всё равно убирает
   «Unknown publisher» и ложные срабатывания Defender.
2. **Каждый релиз требует ручного одобрения** — их правила: «Every release needs manual approval
   for signing». То есть релизный workflow будет висеть на шаге подписи, пока ты не нажмёшь
   Approve в панели SignPath. Полностью автоматической подписи в этой программе не бывает.
3. **Нужна репутация проекта.** Прямая цитата: «we cannot sign binaries based on source code that
   nobody knows… we require a certain verifiable reputation». Свежий форк без звёзд и упоминаний
   могут отклонить — известны случаи отказа. Чем больше внешних подтверждений (звёзды, упоминания,
   скачивания, issue от сторонних людей), тем выше шанс.
4. Пока заявка не одобрена, релизы выходят без подписи — это ожидаемо и пайплайн не ломается
   (шаг подписи включается переменной репозитория `SIGNPATH_ENABLED=true`).

## Текст заявки (английский, вставлять в форму на signpath.org/apply)

**Project name**
Aira

**Repository URL**
https://github.com/Nicoap/Aira

**Project description**
Aira is a free, open-source YouTube Music client for Windows, macOS, Linux and Android,
built with Compose Multiplatform. It plays music from YouTube Music without ads or tracking,
supports synchronized lyrics, crossfade, a custom theme system, Last.fm/ListenBrainz
scrobbling, offline downloads and Discord presence.

**How the project is distributed**
Windows: an `.msi` installer built with `jpackage` (`gradlew :desktopApp:packageMsi`) on
GitHub-hosted `windows-latest` runners and published as a GitHub Release asset.
macOS: a `.dmg` built on `macos-latest`. Linux: a `.tar.gz` application image built on
`ubuntu-latest`. Android: per-ABI APKs built in the same workflow.

**Why we need code signing**
The Windows launcher produced by `jpackage` is unsigned, so Microsoft Defender repeatedly
flags it as `Trojan:Win32/Beafloos.A!ml` — a machine-learning heuristic false positive (the
same detection fires on the upstream project this fork is based on). SmartScreen also shows
"Unknown publisher". We cannot afford a commercial code-signing certificate as a hobby
project, so we are applying to the SignPath Foundation programme.

**Licence**
GNU General Public License v3.0 (`LICENSE`). Aira is a fork of Wavora
(https://github.com/Wavora-dev/Wavora), which itself grew out of SimpMusic; all upstream
copyright notices are preserved.

**Build system**
GitHub Actions, `.github/workflows/release.yml`. All jobs run on GitHub-hosted runners only.
The signing request is submitted from the `desktop` job with
`signpath/github-action-submit-signing-request@v3`
(`artifact-configuration-slug: msi-deep`, `skip-decompress: true`).

**Artifacts to sign**
`Aira-<version>.msi` and, inside it, the launcher `Aira.exe` (deep signing,
see `docs/signpath/msi-deep.xml`).

**Code signing policy**
https://github.com/Nicoap/Aira#code-signing-policy and
https://github.com/Nicoap/Aira/blob/main/docs/CODE_SIGNING_POLICY.md

**Contact**
GitHub: @Nicoap · e-mail: lolneknikita@gmail.com

---

## Что сделать после одобрения

1. В панели SignPath: **Trusted Build Systems** → Add predefined → **GitHub.com**, затем в
   проекте `aira` привязать его (Link). Без этого коннектор GitHub Actions не авторизуется:
   `Trusted build system is not allowed to log into the organization`.
2. Установить [SignPath GitHub App](https://github.com/apps/signpath) на репозиторий.
3. В политике `release-signing` переключить сертификат на Foundation-овский (сейчас там
   self-signed `CN=nicoqw`), добавить себя в approvers и включить ручное одобрение релизов.
4. В artifact configuration `msi-deep` вставить актуальный `docs/signpath/msi-deep.xml`
   (там уже есть проверка `product-name="Aira"`).
5. Поставить переменную репозитория `SIGNPATH_ENABLED=true`, поднять версию (MSI с тем же
   номером поверх не встаёт) и запустить релиз. В логе шага «Выбрать MSI для релиза» должно
   появиться `подписанный MSI: Aira-<version>.msi, статус подписи: Valid`.
