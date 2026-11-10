---
title: Rhino
summary: The legacy engine (JS_RHINO) for older MessengerBot-style scripts: how it differs from NeonJS, its Java values and its require.
---

Rhino runs older scripts written for MessengerBot and earlier StarLight versions: ES5 with part of ES6, run as a
script rather than a module. Use it only when the user asks or the project already uses it; a project's language
can't change after it is created.

## Syntax

With the default language version (ES6, in the project's Rhino settings):
- **Works:** arrow functions, `let` and `const`, template literals, destructuring, rest parameters (`...args`),
  method shorthand, `for (var x of …)` and `for (let x of …)`, `Promise`, `Map`, `Set`, `Symbol` and BigInt
  literals.
- **Doesn't parse:** `class`, `async`/`await`, spread (`f(...xs)`, `[...xs]`, `{...o}`), default parameter values,
  `?.`, `??` and `for (const x of …)`. A syntax error stops the whole script from compiling.

## Differences from NeonJS

| | Rhino | NeonJS |
|---|---|---|
| Event functions | Defined at the top level: `function onMessage(event) {}` | Exported: `export function onMessage(event) {}` |
| Other files | `require()` | `import` |
| Waiting | Blocking calls such as `Utils.getWebText()`; timers | `fetch`, `WebSocket`, Promises, `async`/`await`, timers |
| Concurrency | Up to three events run at once by default, so shared state needs care | One task at a time |
| Strictness | Sloppy mode: assigning an undeclared name creates a global | Strict mode |
| Java | `Packages.java.util.ArrayList`, `java.lang.Math`, `importClass()`, `importPackage()`, `JavaAdapter` | `Java.type()`, `Java.extend()` |
| `console` | Not there; use `Log` | Logs to the project |

- Because every top-level function is visible, a Rhino project with both the StarLight original and MessengerBot API2
  levels gets a top-level `onMessage` called as StarLight's event too, so one message can reach it twice. Name an
  API2 listener apart, as the starting code does when a new project receives both events (`onApi2Message`).
- StarLight's `Java` API in Rhino is its own object: `Java.type()` returns a `java.lang.Class`, not something to
  `new`. `describe_api Java` describes it.

## Java values

- Java strings, numbers and booleans arrive as JavaScript values, so `msg.content === "!hi"` works. A `CharSequence`
  that isn't a `String`, such as a `StringBuilder`, stays a Java object: compare `String(x)`.
- A Java `long` arrives as a number, so one beyond 2^53, such as a 64-bit room ID, loses precision. The API
  declarations show `number | bigint`, which is NeonJS's; in Rhino, pass IDs as strings where an API takes them,
  and don't compute with them. A `BigInteger` arrives as a BigInt.
- Java arrays, `List`s and `Map`s read as in NeonJS: by index, `length`, `for...of`, `map.key`. Rhino has no
  `Java.from`, and reading it throws an `InternalError` rather than giving `undefined`: `Array.from(x)` makes a JS
  array.
- Getters read as properties in Rhino too (`room.groupChat`), and a boolean `isX()` must be called with parentheses
  when written as `room.isGroupChat()`.
- Java exceptions arrive as errors named `JavaException`, with the Java exception in `e.javaException`.

## require

- `require("name")` is CommonJS: it runs `name.js` and returns its `module.exports`.
- It looks in `StarLight/modules` first when the app setting **전역 모듈 로드** (load global modules) is on, then in the project folder.
  That is the opposite of NeonJS's order.
- The project's language settings can turn modules off (**외부 모듈 로드**, load external modules) or run them sandboxed.
