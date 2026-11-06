---
title: 전역 객체
description: NeonJS 프로젝트에서 바로 쓸 수 있는 전역 객체와 함수, 그리고 쓸 수 없는 것을 정리했어요.
order: 1
---

## 언어 기능

NeonJS는 **ECMAScript 2025** 전체와 ES2026에 추가되는 기능(`using`/`await using`, `Array.fromAsync`, `Error.isError`, `Uint8Array`의 base64·hex 변환 등)을 지원해요. Test262에서 Stage 3로 추적하는 `Temporal`, 데코레이터, `ShadowRealm`도 들어 있어요.

```javascript
const today = Temporal.Now.plainDateISO();
const grouped = Object.groupBy([1, 2, 3, 4], (n) => (n % 2 ? "odd" : "even"));
const unique = new Set([1, 2, 3]).union(new Set([3, 4]));
```

## StarLight가 더하는 전역

| 이름 | 설명 |
|---|---|
| `setTimeout(fn, ms, ...args)` | `ms` 밀리초 뒤에 `fn`을 한 번 불러요. 타이머 ID(숫자)를 돌려줘요. |
| `setInterval(fn, ms, ...args)` | `fn`이 끝날 때마다 `ms` 밀리초를 기다렸다가 다시 불러요. |
| `clearTimeout(id)`, `clearInterval(id)` | 타이머를 취소해요. |
| `toast(message, duration?)` | 화면 아래에 짧은 안내 메시지를 띄워요. `duration`은 `0`(짧게) 또는 `1`(길게, 기본값)이에요. |
| `Java` | Java·Android 클래스에 접근해요. [Java와 Android API 쓰기](/docs/neonjs/guides/java)를 보세요. |

타이머는 StarLight가 프로젝트의 작업으로 실행해요. 콜백에서 난 오류는 `Exception on timer <ID>` 로그로 남아요. 타이머는 프로젝트를 다시 컴파일하거나 스코프를 폐기하면 취소되고, **프로젝트 스위치를 끄는 것만으로는 멈추지 않아요.**

StarLight의 기본 API(`Api`, `Utils`, `Log`, `FileStream` 등)와 켜져 있는 플러그인이 등록한 API도 전역으로 들어와요. 목록은 [스크립트 API](/docs/app/reference/script-api)를 보세요.

## console

`console`의 출력은 프로젝트 로그에 남아요. 로그 화면에서 단계별로 걸러 볼 수 있어요.

| 메서드 | 로그 단계 |
|---|---|
| `console.log`, `console.info`, `console.dir`, `console.table`, `console.group`, `console.count`, `console.timeEnd` | 정보 |
| `console.debug` | 디버그 |
| `console.warn` | 경고 |
| `console.error`, `console.trace`, `console.assert` (실패할 때) | 오류 |

`console.log("%s님 안녕하세요", name)`처럼 서식 지정자(`%s`, `%d`, `%o` 등)도 쓸 수 있어요.

## 웹 표준 전역

브라우저나 Node.js용으로 작성된 코드가 흔히 기대하는 전역이 들어 있어요.

| 분류 | 전역 |
|---|---|
| 네트워크 | `fetch`, `WebSocket`, `Headers`, `Request`, `Response`, `FormData` |
| URL | `URL`, `URLSearchParams` |
| 인코딩 | `TextEncoder`, `TextDecoder`(UTF-8만), `atob`, `btoa` |
| 이벤트와 취소 | `Event`, `CustomEvent`, `EventTarget`, `AbortController`, `AbortSignal` |
| 데이터 | `Blob`, `File`, `structuredClone`, `DOMException` |
| 그 밖에 | `queueMicrotask`, `performance.now()`, `crypto.getRandomValues()`, `crypto.randomUUID()` |

`fetch`와 `WebSocket`의 동작과 제한은 [비동기와 네트워크](/docs/neonjs/guides/async#fetch)에 정리했어요.

## 없는 것

| 없는 것 | 대신 |
|---|---|
| `Intl`, 로케일에 맞춘 `toLocaleString()` | `toLocaleString()`은 로케일과 무관한 기본 형식을 돌려줘요(`(1234567).toLocaleString()`은 `"1234567"`). 자릿수 구분 같은 서식은 직접 만들거나 Java의 `NumberFormat`, `DateTimeFormatter`를 쓰세요. |
| `require()`, `module.exports` | `import` / `export`를 써요. [모듈 나누기](/docs/neonjs/guides/modules)를 보세요. |
| `process`, `Buffer`, `node:` 모듈 | Node.js API는 들어 있지 않아요. 바이트는 `Uint8Array`, 환경 값은 StarLight API로 다뤄요. |
| `window`, `document` 같은 DOM | 화면이 없는 환경이에요. |
| `Packages`, `java`, `importClass()`, `importPackage()` | `Java.type()`을 써요. |
| `print()` | `console.log()`를 써요. |
| `f.caller`, `f.arguments` | 표준이 아니라서 지원하지 않아요. |

> [!NOTE]
> `Temporal`은 ISO 달력만 쓸 수 있어요. 음력 같은 다른 달력은 `Intl`과 함께 들어오는 기능이라 StarLight에서는 쓸 수 없어요.
