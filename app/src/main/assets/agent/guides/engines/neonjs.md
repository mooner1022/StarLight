---
title: NeonJS
summary: How NeonJS (js_neon) runs scripts: ES modules and exported event functions, imports, async code, timers, fetch and WebSocket, console, Java access, and what it lacks.
---

NeonJS is StarLight's main script engine: a JavaScript engine for the JVM covering ECMAScript 2025 and some of 2026
(`using`, `Array.fromAsync`, `Temporal` with the ISO calendar only). Every file of a NeonJS project is an ES module in
strict mode. Pick it for new projects unless the user asks for Rhino.

## Modules

- Files are ES modules: `import`/`export` between them. There is no `require()`, `module.exports` or `exports`.
- Paths starting with `./` or `../` resolve from the importing file. A bare name (`"utils/format"`, `"dayjs"`) is
  looked up in the project folder first, then in `StarLight/modules` when the app setting **전역 모듈 로드** (load global modules) is on.
- At each place the name is tried as written, then with `.js`, then as `<name>/index.js`. A missing module fails
  the compile with `Cannot find module '<name>'`.
- JSON and text files import with attributes:
  ```js
  import config from "./config.json" with { type: "json" };
  import help from "./help.txt" with { type: "text" };
  ```
- `import()` loads a module on demand and returns a Promise.
- Modules are read when the project compiles: after changing one, compile the project again. Each project has its own
  module instances; projects don't share module state.
- `node_modules` isn't read. An npm package works only bundled into one ES module file (esbuild
  `--format=esm --platform=neutral`), and only if it doesn't need Node APIs.
- The project's language settings can turn imports off (**외부 모듈 로드**, load external modules) or confine them to the project
  folder and `StarLight/modules` (**샌드박스 환경에서 로드**, load in a sandbox, on by default).

## Event functions

- StarLight calls only **exported** functions: `export function onMessage(event) { ... }`. A function that isn't
  exported is never called, without any error.
- Event functions may be `async`. StarLight doesn't wait for the Promise, so catch errors inside.
- Top-level `await` works; the compile waits for the module's top-level code to finish.
- API2 events don't call functions: scripts add listeners with `bot.on(...)`, see guide:events#api2-listeners.

## Async code

- A NeonJS project runs one task at a time on its own event loop: an event function, a timer callback, a fetch
  continuation. While one runs, every other message waits.
- StarLight doesn't limit how long a task runs. A busy loop stops the project until it is compiled again or its scope
  is discarded. Wait with `await` or timers, never in a loop, and split heavy work with `setTimeout`.
- An uncaught error in an event-loop task is logged as `Uncaught exception: …`, a rejected Promise nobody handles as
  `Unhandled promise rejection: …`, both with a JS stack trace.

## Timers

- `setTimeout(fn, ms, ...args)`, `setInterval(fn, ms, ...args)`, `clearTimeout(id)`, `clearInterval(id)`, run as the
  project's tasks.
- `setInterval` measures each interval from the end of the previous callback.
- Recompiling the project or discarding its scope cancels its timers. Turning the project off does not.
- A callback's error is logged as `Exception on timer <id>`; an interval keeps running.
- `toast(message, duration?)` shows a short message on screen (`duration` 0 short, 1 long, the default).

## fetch and WebSocket

- `fetch`, `Headers`, `Request`, `Response`, `FormData`, `WebSocket`, `URL`, `URLSearchParams`, `AbortController`,
  `AbortSignal.timeout(ms)` work as on the web.
- They reach any internet host over `https:` or `http:`, and `127.0.0.1` on the device. Private network addresses
  (`192.168.x.x`, `10.x.x.x`) are refused; use Java's HTTP classes for those.
- Redirects are followed up to 20 times; there are no cookies and no cache. Up to 16 requests run at once per
  project, and a response is at most 32 MiB. Connecting times out after 10 seconds, and waiting for data after 30.
- Up to 8 WebSockets can be open at once per project.
- A failed connection rejects with `TypeError: fetch failed`, the reason in `cause`.
- A WebSocket stays open until `close()` or until the project is compiled again.
- Prefer these to the legacy `Utils.getWebText()`, which blocks the project while it waits.

## Console

- `console.log/info/dir/table/group/count/timeEnd` log at info level, `console.debug` at debug, `console.warn` at
  warning, `console.error/trace` and failed `console.assert` at error. Format specifiers (`%s`, `%d`, `%o`) work.
- Output goes to the project's log, which `get_project_logs` and `send_debug_message` show, except the debug level:
  `console.debug` and `Log.d` go only to the app's log, so use `console.log` for anything you need to read back.
- The project's log keeps about its latest 50 entries.
- The `Log` API logs too, with a tag: `describe_api Log`.

## Java access

- `Java.type("java.util.ArrayList")` returns a class: construct it with `new`, call its static members, test with
  `instanceof`. Nested classes: `Java.type("android.os.Build").VERSION` or `Java.type("android.os.Build$VERSION")`.
  Array types: `Java.type("int[]")`.
- Getters read as properties: `getName()` as `obj.name`, a boolean `isEnabled()` as `obj.enabled`. Written as
  `obj.isEnabled` without parentheses it is the method itself, which is always truthy. `describe_api` shows members the
  way scripts see them.
- Values crossing over: numbers and strings become JS primitives; a `long` beyond 2^53 and any `BigInteger` become a
  BigInt (compare 64-bit IDs as strings: `String(id)`); `null` stays `null`; other objects, including a
  `CharSequence` that isn't a `String`, stay Java objects (`String(x)` for text).
- Java arrays and `List`s are array-like: index, `length`, `for...of`, spread. They aren't JS arrays: `Array.from(x)`
  makes one, as in Rhino (`Java.from(x)` works too). `Java.to([1, 2], "long[]")` makes a Java array. A `Map`'s entries read as properties.
- JS functions pass where Java wants a single-method interface (`Runnable`, a callback) or a Kotlin function type.
  `Java.extend(Base, { ... })` subclasses or implements; `Java.super(obj)` calls the parent's methods.
- A Java exception is caught as an error named `HostError` whose `message` is the Java message.
- Pick an overload explicitly by signature: `sb["append(java.lang.String)"]("x")`.
- Scripts run on the project's worker thread. UI calls must go to the main thread (`Handler(Looper.getMainLooper())`);
  `toast()` does that itself.
- The Google Play build blocks some Java APIs: guide:play-build#java-access-limits.

## What NeonJS lacks

- `Intl`: `toLocaleString()` ignores locales. Format numbers and dates yourself or with Java's `NumberFormat` and
  `DateTimeFormatter`.
- Node.js: no `require`, `process`, `Buffer` or `node:` modules. Bytes are `Uint8Array`.
- DOM: no `window` or `document`.
- Rhino's Java syntax: no `Packages`, `java.lang...`, `importClass()`, `importPackage()` or `JavaAdapter`.
- `print()`: use `console.log()`.
