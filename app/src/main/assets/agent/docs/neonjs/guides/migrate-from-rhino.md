---
title: Rhino 프로젝트 옮기기
description: JS(RhinoJS) 프로젝트의 코드를 NeonJS로 옮길 때 바꿔야 할 것들을 정리했어요.
order: 4
---

프로젝트의 언어는 만든 뒤에 바꿀 수 없어요. **새 NeonJS 프로젝트를 만들고**, 기존 프로젝트의 파일을 복사한 뒤 아래 항목을 차례로 고치세요. 기존 Rhino 프로젝트는 옮기기가 끝날 때까지 남겨 두면 비교하기 좋아요.

## 한눈에 보기

```javascript title="before.js (Rhino)"
importClass(Packages.java.text.SimpleDateFormat);
const words = require("words");

function onMessage(event) {
    if (event.message == "/시간") {
        var fmt = new SimpleDateFormat("HH:mm");
        event.room.send(fmt.format(new java.util.Date()));
    }
    lastRoom = event.room.name;   // 선언 없이 전역에 대입
}
```

```javascript title="after.js (NeonJS)"
import words from "./words.js";

const SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
const JDate = Java.type("java.util.Date");
let lastRoom;

export function onMessage(event) {
    if (event.message === "/시간") {
        const fmt = new SimpleDateFormat("HH:mm");
        event.room.send(fmt.format(new JDate()));
    }
    lastRoom = event.room.name;
}
```

## 1. 이벤트 함수에 export 붙이기

`onMessage`, `response`, `onMessageDeleted` 같은 이벤트 함수 앞에 `export`를 붙여요. `export`가 없는 함수는 StarLight가 부르지 못해요.

## 2. require를 import로

| Rhino | NeonJS |
|---|---|
| `const lib = require("lib");` | `import lib from "./lib.js";` |
| `module.exports = { a, b };` | `export { a, b };` 또는 `export default { a, b };` |
| `exports.a = a;` | `export const a = …;` |

모듈 안에서도 `module.exports` 대신 `export`를 써요. 이름만 적은 경로(`"lib"`)는 Rhino와 반대로 **프로젝트 폴더를 먼저**, 그다음 `StarLight/modules`를 찾아요. 자세한 내용은 [모듈 나누기](/docs/neonjs/guides/modules)를 보세요.

## 3. Java 접근을 Java.type으로

| Rhino | NeonJS |
|---|---|
| `Packages.java.util.ArrayList` | `Java.type("java.util.ArrayList")` |
| `java.lang.System.currentTimeMillis()` | `Java.type("java.lang.System").currentTimeMillis()` |
| `importClass(Packages.android.widget.Toast)` | `const Toast = Java.type("android.widget.Toast");` |
| `importPackage(java.io)` | 쓰는 클래스마다 `Java.type()` |
| `new java.lang.Runnable({ run() { … } })` | 함수를 그대로 넘기거나 `Java.extend()` |
| `new JavaAdapter(Base, { … })` | `new (Java.extend(Base, { … }))()` |

자주 쓰는 클래스는 파일 맨 위에 모아 두면 편해요. [Java와 Android API 쓰기](/docs/neonjs/guides/java)를 보세요.

> [!NOTE]
> Rhino 프로젝트의 `Java.type()`은 StarLight가 제공하는 API라서 `java.lang.Class`를 돌려줘요. NeonJS의 `Java.type()`은 `new`로 바로 객체를 만들 수 있는 클래스 객체를 돌려줘요.

## 4. 엄격 모드에 맞추기

ES 모듈은 언제나 엄격 모드로 실행돼요. Rhino에서 그냥 넘어가던 코드가 오류가 날 수 있어요.

- 선언하지 않은 변수에 대입하면 `ReferenceError`가 나요. `let`/`const`로 먼저 선언하세요.
- 최상위의 `this`는 `undefined`예요. 전역 객체가 필요하면 `globalThis`를 써요.
- `with` 문, `arguments.callee`, `010` 같은 8진수 리터럴은 쓸 수 없어요.
- 최상위에 선언한 함수와 변수는 그 파일 안에서만 보여요. 다른 파일에서 쓰려면 `export` 하세요.

## 5. 값의 형식 확인하기

- Java 메서드가 돌려준 문자열은 항상 JS 문자열이에요. Rhino에서 `String(x)`나 `x + ""`로 바꾸던 코드는 그대로 둬도 괜찮아요.
- 2⁵³보다 큰 `long` 값(`event.chatLogId` 등)은 BigInt로 들어와요. 문자열로 바꿔 비교하세요.
- `event.room.isGroupChat`처럼 `is`로 시작하는 값은 `isGroupChat()`으로 불러요. 괄호가 없으면 항상 참이 돼요.

## 6. 동시 실행 생각하기

Rhino 프로젝트는 기본으로 이벤트를 **세 개까지 동시에** 실행해요. NeonJS 프로젝트는 **한 번에 하나씩** 실행해요.

- 여러 이벤트가 같은 변수를 동시에 바꾸는 문제는 없어져요.
- 대신 오래 걸리는 처리는 다른 메시지를 기다리게 해요. 웹 요청은 `Utils.getWebText()` 대신 [`fetch`](/docs/neonjs/guides/async#fetch)와 `await`를 쓰면, 기다리는 동안 다른 이벤트를 처리할 수 있어요.

## 7. 그 밖에

| Rhino | NeonJS |
|---|---|
| `Log.i("…")`만 가능 | `Log`도 되고, `console.log()`도 돼요. |
| `setTimeout(fn, ms)` | `setTimeout(fn, ms, ...args)` — 추가 인자를 넘길 수 있어요. |
| `Bridge.getScopeOf("다른 프로젝트")` | NeonJS 프로젝트는 대상이 될 수 없어요. 프로젝트끼리는 `Broadcast`로 주고받으세요. |
| 프로젝트 설정의 **JS-라이노** 카테고리 | **NeonJS** 카테고리. [언어 설정](/docs/neonjs/reference/settings)을 보세요. |

다 옮겼다면 [디버그 룸](/docs/app/guides/debug-room)에서 충분히 시험한 뒤, 기존 Rhino 프로젝트를 끄고 새 프로젝트를 켜세요.
