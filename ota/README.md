> Обновление статуса 15.11: пользователь уже установил собственную подписанную
> сборку и OTA-test. Ключи и настройку ленты повторно не создавать.
> Ниже сохранено руководство процесса OTA; пустой шаблон `fajita.json`
> не заменяет действующую ленту GitHub.

# Обновления glitchOS для OnePlus 6T

Встроенный Updater LineageOS 22.2 проверяет список обновлений и показывает
новую сборку в системных настройках. Для первого испытания владелец запускает
загрузку и установку из Updater, затем перезагружает телефон. Автоматическая
установка по расписанию — отдельный этап после успешного испытания.

## 1. Первый подписанный образ

Контрольную сборку держим в `G:\glitchOS\baseline-fajita`. Она собрана до
настройки канала. Первую устанавливаемую glitchOS собираем с собственными
ключами. С ними же собираем все последующие OTA. Для перехода с официальной
LineageOS планируем одну чистую установку экспериментального OnePlus.

Адрес `cfifBOOM/glitchOS-ota` ниже предложен для публичного канала.
В Ubuntu из корня checkout, после выбора адреса репозитория:

```bash
export PATH="$HOME/bin:$PATH"
export OUT_DIR="$HOME/android/out/fajita"
cd "$HOME/android/lineage"
tools=/mnt/c/Users/aucab/Documents/Codex/2026-11-12/new-chat/work/contour-os/tools
repo_name=cfifBOOM/glitchOS-ota
python3 "$tools/Configure-Ota.py" "$PWD" "$repo_name"
```

Создать ключи один раз. Если папка уже существует, остановиться и проверить
существующий набор. Резервную копию ключей хранить локально в защищённом месте.

```bash
(
  set -e
  cd "$HOME/android/lineage"
  umask 077
  mkdir -p vendor/lineage-priv
  mkdir vendor/lineage-priv/keys
  cp -R lineage/scripts/lineage-priv-template/. vendor/lineage-priv/keys/
  cd vendor/lineage-priv/keys
  bash -e ./keys.sh
  test -s testkey.pk8 && test -s testkey.x509.pem
)
```

После генерации проверь вывод скрипта. Если он остановился на ошибке,
сначала разберись с частично созданным набором. LineageOS 22.2
подключает `vendor/lineage-priv/keys/keys.mk` при сборке. Ключи этого шаблона
хранятся без пароля. Для GitHub готовим только OTA ZIP и JSON. Набор ключей,
каталог исходников Android и содержимое `target_files` хранятся локально.

Собери выбранное состояние glitchOS с тем же `OUT_DIR`:

```bash
source build/envsetup.sh &&
breakfast fajita &&
m -j2 bacon
```

Использование первого собственного ключа может потребовать
пересборки части уже собранных модулей. Сохраняй артефакты первой и второй
сборок в разных каталогах. Сборки одного дня могут получить одинаковое имя
ZIP, поэтому для каждой версии используй отдельный тег GitHub и проверяй
возрастающий `post-timestamp`.

## 2. Канал и проверка обновления

Схема для публичного репозитория: `main/fajita.json` и отдельный Release с ZIP.
Репозиторий и публикация согласуются с владельцем. Начальный JSON из этого
каталога имеет пустой список. Updater читает его по HTTPS без токенов.
GitHub принимает assets размером строго меньше 2 ГиБ.

Перед публикацией проверь целостность ZIP, затем его подпись официальным
[проверяющим скриптом LineageOS](https://github.com/LineageOS/update_verifier)
с открытым ключом текущего набора. Из корня дерева Android:

```bash
cert=vendor/lineage-priv/keys/testkey.x509.pem
openssl x509 -in "$cert" -pubkey -noout > "$HOME/android/glitchOS-ota-public.pem"
# В окружении с установленными зависимостями update_verifier:
# python3 /путь/к/update_verifier.py "$HOME/android/glitchOS-ota-public.pem" "$zip"
```

Для проверенного ZIP подготовь список. Укажи фактический путь к новой
подписанной сборке в переменной `zip`:

```bash
zip=/путь/к/lineage-22.2-ДАТА-UNOFFICIAL-fajita.zip
python3 "$tools/Make-OtaFeed.py" "$zip" "$cert" "$repo_name" fajita-v0.1.1 \
  > "$HOME/android/fajita.json"
```

Генератор сверяет сертификат из ZIP с указанным публичным сертификатом и
записывает SHA-256, размер и время сборки. Подпись всего файла проверяет
отдельная команда выше, а на телефоне её проверяет Updater.

Сначала загрузи ZIP с его исходным именем в Release с выбранным тегом.
Убедись, что ZIP скачивается по URL из JSON. Затем обнови `main/fajita.json`.
Для первой проверки положи на телефон более раннюю сборку, а в список
добавь вторую. В системных настройках запусти проверку обновлений, установи
новую сборку, перезагрузись и сверь версию и активный слот. Убедись, что
созданные перед обновлением тестовый файл, приложение и пользовательская
настройка сохранились. Magisk и GApps проверяются отдельно, если входят в
тестовую конфигурацию.

## Источники

- [Формат Updater LineageOS 22.2](https://github.com/LineageOS/android_packages_apps_Updater/blob/lineage-22.2/README.md)
- [Подпись LineageOS](https://lineageos.github.io/lineage_wiki/signing_builds.html)
- [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)
