---
title: NeonJS
description: StarLight의 새 자바스크립트 언어인 NeonJS로 최신 문법, 모듈, fetch를 쓰는 봇 스크립트를 작성해 보세요.
section: start
order: 0
---

## NeonJS란

[NeonJS](https://github.com/mooner1022/neon-js)는 StarLight 개발자가 Kotlin으로 만든 **JVM용 자바스크립트 엔진**이에요. StarLight에서는 프로젝트를 만들 때 고르는 언어 중 하나인 **JS(NeonJS)** 로 쓸 수 있어요.

```javascript title="main.js"
import { greet } from "./greet.js";

export function onMessage(event) {
    if (event.message === "/안녕")
        event.room.send(greet(event.sender.name));
}
```

> [!IMPORTANT]
> NeonJS는 **StarLight nightly 브랜치**에만 들어 있어요. 현재 내려받을 수 있는 정식 앱(StarLight 0.3.4b)에서는 쓸 수 없어요. 이 문서는 NeonJS 0.1.6을 기준으로 해요.

## Rhino와 무엇이 다른가요

StarLight에는 오래전부터 쓰던 **JS(RhinoJS)** 언어도 있어요. 두 언어 모두 자바스크립트지만 실행 방식이 꽤 달라요.

| | JS(RhinoJS) | JS(NeonJS) |
|---|---|---|
| 문법 | ES5와 일부 ES6 | **ECMAScript 2025** 전체 (클래스, async/await, 구조 분해, 옵셔널 체이닝 등) |
| 파일 형식 | 스크립트 | **ES 모듈** — 이벤트 함수를 `export` 해야 해요 |
| 다른 파일 불러오기 | `require()` | `import` / `export` |
| HTTP 요청 | `Utils.getWebText()` (응답이 올 때까지 멈춰요) | `fetch`, `WebSocket` (기다리는 동안 다른 이벤트를 처리해요) |
| 비동기 | 타이머 | 타이머, `Promise`, `async`/`await` |
| 동시 실행 | 이벤트를 세 개까지 동시에 | 한 번에 하나씩 차례로 |
| Java 접근 | `Packages.java.util.ArrayList`, `importClass()` | `Java.type("java.util.ArrayList")` |
| 실행 | 인터프리터 또는 최적화 컴파일 | 인터프리터, 또는 자주 쓰는 함수만 골라 컴파일하는 Adaptive 모드 |

NeonJS는 Test262(자바스크립트 표준 적합성 테스트)의 본 테스트를 모두 통과해요. Android 에뮬레이터에서 잰 성능은 기본(Adaptive) 모드가 컴파일된 Rhino 1.7보다 1.8배쯤 빨라요.

## 언제 NeonJS를 고르나요

- **새 프로젝트**라면 NeonJS를 추천해요. 최신 문법과 `fetch`, 모듈 덕분에 코드가 짧고 읽기 쉬워져요.
- **이미 Rhino로 잘 돌아가는 프로젝트**는 그대로 둬도 괜찮아요. 옮기고 싶다면 [Rhino 프로젝트 옮기기](/docs/neonjs/guides/migrate-from-rhino)를 보세요.
- 인터넷에서 찾은 예전 봇 예제 코드는 대부분 Rhino 기준이라 NeonJS에서는 손봐야 해요.

## 문서 읽는 순서

1. [첫 NeonJS 스크립트](/docs/neonjs/start/first-script) — 프로젝트를 만들고 메시지에 답장해 봐요.
2. **가이드** — [모듈](/docs/neonjs/guides/modules), [Java와 Android API](/docs/neonjs/guides/java), [비동기와 네트워크](/docs/neonjs/guides/async)를 차례로 익혀요.
3. **레퍼런스** — [전역 객체](/docs/neonjs/reference/globals)와 [언어 설정](/docs/neonjs/reference/settings)을 찾아볼 때 써요.

앱을 처음 쓴다면 [앱 사용법](/docs/app)에서 설치와 프로젝트 만들기부터 보세요.
