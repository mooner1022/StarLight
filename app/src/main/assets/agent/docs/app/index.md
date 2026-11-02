---
title: StarLight 앱 사용법
description: 메신저 알림을 읽고 스크립트로 답장하는 봇을 StarLight로 만들고 관리하는 방법이에요.
section: start
order: 0
---

## StarLight란

StarLight는 **메신저 알림을 읽고, 스크립트로 답장하는 봇**을 만드는 안드로이드 앱이에요. 카카오톡에 메시지가 와서 알림이 뜨면 StarLight가 그 알림을 읽어 스크립트의 함수를 부르고, 스크립트는 알림의 답장 기능으로 그 방에 메시지를 보내요.

```javascript title="hello-bot.js"
export function onMessage(event) {
    if (event.message === "/안녕")
        event.room.send(`${event.sender.name}님 안녕하세요!`);
}
```

> [!IMPORTANT]
> 이 문서는 **스냅샷 채널(StarLight 1.0.0 개발 빌드)** 을 기준으로 해요. 베타(0.3.4b)와 정식 채널의 앱에는 NeonJS가 없고, 일부 화면과 기능이 이 문서와 다를 수 있어요. [다운로드](/#download)에서 스냅샷 채널을 고르면 같은 앱을 받을 수 있어요.

## 알아 둘 말

| 말 | 뜻 |
|---|---|
| **프로젝트** | 봇 하나예요. 스크립트 파일과 설정을 담은 폴더이고, 프로젝트마다 따로 켜고 끌 수 있어요. |
| **언어** | 스크립트를 실행하는 엔진이에요. 기본으로 [JS(NeonJS)](/docs/neonjs)와 JS(RhinoJS)가 있어요. |
| **이벤트** | 메시지 수신처럼 스크립트의 함수를 부르는 일이에요. `onMessage` 같은 함수 이름이 정해져 있어요. |
| **컴파일** | 스크립트를 읽어 실행할 준비를 하는 단계예요. 코드를 고친 뒤에는 다시 컴파일해야 반영돼요. |
| **디버그 룸** | 카카오톡 없이 앱 안에서 메시지를 보내 봇을 시험하는 채팅 화면이에요. |
| **플러그인** | 새 API, 이벤트, 언어, 위젯을 더하는 확장 파일(`.slp`)이에요. |

## 화면 구성

아래 탭으로 다섯 화면을 오가요. 좌우로 밀어서 넘길 수도 있어요.

| 탭 | 하는 일 |
|---|---|
| **홈** | 봇 상태, 로그, 오류 같은 위젯을 한눈에 봐요. → [홈과 위젯](/docs/app/guides/home) |
| **프로젝트** | 봇을 만들고, 고치고, 켜고 꺼요. → [프로젝트 관리](/docs/app/guides/projects) |
| **플러그인** | 플러그인을 설치하고 관리해요. → [플러그인 설치와 관리](/docs/app/guides/plugins) |
| **로그** | 앱과 프로젝트가 남긴 기록을 봐요. → [로그](/docs/app/guides/logs) |
| **설정** | 전원, 알림, 패키지 규칙 같은 앱 설정을 바꿔요. → [설정](/docs/app/guides/settings) |

## 문서 읽는 순서

1. **시작하기** — [앱을 설치](/docs/app/start/install)하고 [첫 봇을 만들어](/docs/app/start/first-bot) 카카오톡에서 답장을 받아 봐요.
2. **가이드** — 프로젝트, 에디터, 디버그 룸, 플러그인, 설정을 하나씩 살펴봐요. 봇이 답하지 않으면 [문제 해결](/docs/app/guides/troubleshooting)을 보세요.
3. **레퍼런스** — [이벤트 함수](/docs/app/reference/events), [스크립트 API](/docs/app/reference/script-api), [폴더 구조](/docs/app/reference/folders)를 찾아볼 때 써요.

스크립트 언어를 자세히 알고 싶다면 [NeonJS 문서](/docs/neonjs)를, 앱 기능을 직접 넓히고 싶다면 [플러그인 개발 문서](/docs/plugins)를 보세요.
