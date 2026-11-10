---
title: The app
summary: Where things are in StarLight's screens, by the names users see, for answering how-to questions: tabs, settings, permissions, notification rules, the projects screen, the debug room and the AI tab.
---

Point users to screens by the names they see. Tab and button names follow the phone's language; most setting names
are in Korean in every language, so give them as written here.

## Tabs

| Tab | What it holds |
|---|---|
| Home (홈) | A grid of widgets: bot status, logs, errors, running tasks, message activity, script values (`HomeWidget`). Long-press a widget to edit the grid. |
| Projects (프로젝트) | The projects, with **New project** (새 프로젝트), sorting and search. |
| AI | Conversations with StarLight AI. |
| Plugins (플러그인) | Installed plugins and the plugin store. Not in the Google Play build. |
| Logs (로그) | The app's recent logs, every project's included, with filters. |
| Settings (설정) | The app's settings (below). |

## Settings

The Settings tab opens these screens. Setting names are shown in Korean whatever the phone's language.

| Screen | Items |
|---|---|
| 일반 (general) | **전역 전원** (global power: off, no project receives anything), **알림 접근 권한** (notification access), **배터리 최적화 예외** (battery optimization exemption), **앱 재시작** (restart) |
| 프로젝트, 플러그인 | **컴파일 애니메이션**, **전역 모듈 로드** (`StarLight/modules` for imports and `require`), **안전 모드** (start without plugins) |
| 알림, 이벤트 | **수신 메세지 로그 표시** (log received messages), **패키지 규칙 설정** (notification rules, with **규칙 자동 설정** inside), **onNotificationPosted 이벤트 사용** (deliver every app's notifications to scripts), **문제 해결 도우미** (troubleshooter) |
| 미디어 전송 (media sending) | How `MediaSharer.send` sends files: whether KakaoTalk and **다른 앱 위에 표시** (display over other apps) are ready, **다운로드 제한 시간 (초)**, **최대 파일 크기**, **전송 후** (where to go once KakaoTalk has read the files), **기본으로 캐시 사용**, **캐시 비우기** |
| 로그 | Log file retention, how many logs the Logs tab shows, deleting log files |
| 정보 (about) | **업데이트 확인** (GitHub build only), **앱 정보** (versions, licenses), **개발자 돕기**, and **개발자 모드** once it is unlocked |
| 개발자 모드 (developer mode) | Hidden until the developer entry in **앱 정보** is tapped eight times. **내부 로그 표시** (the app's verbose entries in the Logs tab), **외부 dex 라이브러리 로드** (load libraries from `StarLight/libs`, which gives scripts `LibraryManager`; not in the Google Play build), **동시 실행 수 변경** (adds a per-project setting for how many events run at once), and tools for testing the app. Only for users who need one of these. |

Theme and language follow the phone's settings.

## Permissions

| Permission | Why |
|---|---|
| Notification access | Reading chat notifications, and replying through them. Without it no message arrives. |
| Display over other apps | Required since 1.0.0; the first run asks for it. |
| All-files access | The shared `StarLight` folder; GitHub build only. |
| Battery optimization exemption | Optional; keeps the app from being stopped in the background. |

The first run asks for the required ones. Later, **설정 → 일반** shows their state and opens the system screens.
**문제 해결 도우미** checks permissions, the KakaoTalk version and the notification rules.

## Notification rules

Chat messages come from messenger notifications. By default, with **규칙 자동 설정** (automatic rules) on, StarLight
reads KakaoTalk (`com.kakao.talk`) for the device's main user, which suits most users. KakaoTalk in a dual-messenger
or work profile needs a rule added by hand in **설정 → 알림, 이벤트 → 패키지 규칙 설정**:
- package `com.kakao.talk`;
- parser `kakao_26_7` on Android 11 or later with KakaoTalk 26.7.0 or later, `android_r` with an older KakaoTalk,
  `default` before Android 11;
- user ID `0` for the main user, `10` for a work profile, `11` for a Xiaomi work profile, `95` for Samsung's dual
  messenger.

A bot replies through the notification's reply action, so it can answer only in rooms whose notifications it has
received.

## Projects screen

Each project is a card with its name, language, state and an on/off switch, usable once the project has compiled.
Tapping the card shows its buttons:

| Button | Does |
|---|---|
| Edit (수정) | Opens the code editor. |
| Compile (컴파일) | Compiles the project; a failure has a details button. |
| Debug (디버그) | Opens the debug room, once compiled. |
| AI | Starts a StarLight AI conversation about this project. |
| Config (설정) | The project's settings: **API 방식** (API levels), **받을 이벤트** (events to receive), language settings, **오류 발생시 비활성화** (disable on error), custom buttons (**사용자 지정 버튼**, in the **실험적 기능** category), and under 위험 (danger) stopping its threads, discarding its scope and deleting it. Changes need the save button at the top. |
| Info (정보) | Name, files and state; **파일 → 내보내기** exports it as a zip. |
| Like | Pins it to the top. |

**New project** walks through the name, API levels, language, events and language settings. A zip exported earlier
imports from the same screen. Deleting a project is in its settings, under 위험, and can't be undone.

## Debug room screen

The chat screen that sends messages to one project (guide:testing#the-debug-room). The settings button at the top
opens its settings: room name, app package, group chat, send with Enter, the bot's and the sender's names, profile
pictures and bubble colors, and clearing the chat. Long-pressing a message copies it.

## AI tab

Conversations with StarLight AI: start one from the AI tab, a project card's AI button or the home widget. The agent's
provider, model and sign-in are in the AI tab's settings. Conversations are kept in the StarLight folder (GitHub
build) and can be exported as a zip.
