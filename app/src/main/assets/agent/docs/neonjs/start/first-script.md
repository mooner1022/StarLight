---
title: 첫 NeonJS 스크립트
description: NeonJS 프로젝트의 파일이 어떻게 실행되는지 알아보고, 명령어 몇 개를 처리하는 봇을 만들어 봐요.
order: 1
---

앱에서 프로젝트를 만드는 과정은 [첫 봇 만들기](/docs/app/start/first-bot)에 있어요. 여기서는 NeonJS로 만든 프로젝트의 코드가 **언제, 어떻게 실행되는지**에 집중할게요.

## 프로젝트 만들기

**프로젝트** 탭 → **새 프로젝트**에서 언어로 **JS(NeonJS)** 를 고르고, 이벤트는 `onMessage`를 고른 채 만들어요. 메인 파일에는 이런 틀이 들어 있어요.

```javascript title="my-bot.js"
export function onMessage(event) {
    
}
```

## 코드가 실행되는 순서

NeonJS 프로젝트의 메인 파일은 **ES 모듈**로 실행돼요.

1. **컴파일할 때** 파일의 최상위 코드가 한 번 실행돼요. `import`한 모듈도 이때 읽혀요.
2. 그 뒤 메시지가 올 때마다 StarLight가 **`export`한 함수**를 찾아 불러요.
3. 다시 컴파일하면 이전 상태는 모두 사라지고 1번부터 새로 시작해요.

```javascript title="my-bot.js"
console.log("컴파일할 때 한 번 실행돼요");

let count = 0;   // 다시 컴파일할 때까지 값이 유지돼요

export function onMessage(event) {
    count++;
    console.log(`${count}번째 메시지: ${event.message}`);
}
```

> [!IMPORTANT]
> 이벤트 함수는 반드시 `export` 해야 해요. `export`가 없으면 StarLight가 함수를 찾지 못해 아무 일도 일어나지 않아요.

모듈은 항상 엄격 모드(strict mode)로 실행돼요. 선언하지 않은 변수에 값을 넣으면 `ReferenceError`가 나니, 변수는 `let`이나 `const`로 선언하세요.

## 명령어 봇 만들기

명령어마다 처리 함수를 `Map`에 담아 두면 명령어를 늘리기 쉬워요.

```javascript title="my-bot.js"
const PREFIX = "/";
const startedAt = Date.now();

const commands = new Map([
    ["안녕", ({ sender }) => `${sender.name}님 안녕하세요!`],
    ["주사위", () => `🎲 ${Math.floor(Math.random() * 6) + 1}`],
    ["가동시간", () => `${Math.floor((Date.now() - startedAt) / 60_000)}분째 일하는 중이에요`],
    ["골라", (_, args) => args.length ? args[Math.floor(Math.random() * args.length)] : "고를 것을 알려 주세요"],
]);

export function onMessage(event) {
    if (!event.message.startsWith(PREFIX)) return;

    const [name, ...args] = event.message.slice(PREFIX.length).trim().split(/\s+/);
    const handler = commands.get(name);
    if (!handler) return;

    event.room.send(handler(event, args));
}
```

컴파일한 뒤 [디버그 룸](/docs/app/guides/debug-room)에서 `/골라 짜장 짬뽕`처럼 보내 보세요.

## 로그 보기

`console.log()`로 남긴 내용은 프로젝트 로그에 들어가요. 에디터의 서랍(☰) → **로그** 탭이나 **로그** 탭에서 볼 수 있어요. 오류는 `console.error()`로 남기면 오류 단계로 기록돼요.

```javascript
export function onMessage(event) {
    try {
        riskyWork(event);
    } catch (e) {
        console.error("처리 실패:", e.message);
    }
}
```

> [!TIP]
> 처리하지 않은 오류가 이벤트 함수 밖으로 나가면 기본 설정에서 프로젝트가 꺼져요. 실패할 수 있는 작업은 `try`/`catch`로 감싸세요.

## 다음 단계

- [모듈 나누기](/docs/neonjs/guides/modules) — 코드가 길어지면 파일을 나눠요.
- [비동기와 네트워크](/docs/neonjs/guides/async) — `fetch`로 외부 API를 불러요.
- [이벤트 함수](/docs/app/reference/events) — `onMessage` 말고 받을 수 있는 이벤트와 `event` 객체의 구조예요.
