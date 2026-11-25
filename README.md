# glitchOS — экспериментальная Android-прошивка

[English overview](#english-overview) · [Релизы](https://github.com/cfifBOOM/glitchOS/releases)

**glitchOS — experimental Android 15 custom ROM for OnePlus 6T (fajita), based on LineageOS 22.2.**

glitchOS — экспериментальная Android-прошивка на базе LineageOS 22.2 (Android 15) для OnePlus 6T, включая McLaren Edition. Кодовое имя устройства — fajita.

В центре проекта — помощница, которая знакомится с владельцем во время первой настройки телефона, а затем появляется на домашнем экране. Интерфейс вдохновлён живыми плитками Windows Mobile. Иногда в нём происходят постановочные сбои, от которых обычные действия на телефоне продолжают работать.

## Состояние проекта

### Базовая прошивка для fajita

LineageOS 22.2 собрана из исходников. Установочный ZIP проверен; запуск этой сборки на устройстве ещё предстоит.

### Домашний экран

Есть нативный прототип с плитками и анимированным портретом. Нажатие на плитку открывает сцену со сценарными репликами.

### Первое знакомство

В разработке. Помощница должна появиться внутри штатного мастера Android, а затем встретить владельца в своей плитке.

### Системные обновления

Есть заготовка канала OTA. Первое обновление между подписанными сборками ещё предстоит проверить на OnePlus.

## Сборки и обновления

Установочные ZIP будут появляться в GitHub Releases. Сейчас канал пуст, и установочного релиза glitchOS здесь пока нет.

fajita.json — список обновлений для встроенного Updater LineageOS. План — подписывать последовательные сборки одним набором ключей, чтобы новые версии glitchOS устанавливались поверх предыдущих с сохранением приложений, настроек и файлов. Переход с официальной LineageOS на первую тестовую сборку будет описан отдельно в примечаниях к релизу.

## Что дальше

Ближайшая цель — проверить запуск базовой прошивки, встроить знакомство и домашний экран, а затем проверить переход между двумя сборками через системное обновление. После этого можно развивать диалог, память и команды, которые выполняются прямо на телефоне без интернета.

## Поддержать разработку

glitchOS — личный некоммерческий проект. Платные функции и подписки не планируются; поддержка разработки исключительно добровольная и не даёт привилегий.

[![Поддержать разработку](https://img.shields.io/badge/DonationAlerts-Поддержать%20разработку-FF8C00?style=for-the-badge)](https://www.donationalerts.com/r/cfifboom)

## English overview

glitchOS is an experimental Android 15 custom ROM based on LineageOS 22.2 for the OnePlus 6T and McLaren Edition (fajita). Its concept centers on an interactive assistant, a Windows Mobile-inspired live tile launcher and scripted visual glitches that do not intentionally interrupt ordinary phone actions.

**Development status:** experimental. The base installation ZIP has been built and checked, but booting that build on the device has not yet been verified according to the project status above. The launcher prototype exists; first-run onboarding and signed OTA updates are still in development. No installable glitchOS release is currently published according to this README. See [Releases](https://github.com/cfifBOOM/glitchOS/releases) for available builds.

This is a personal, non-commercial project with voluntary donations only.
