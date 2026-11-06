---
title: 스크립트 API
description: 모든 프로젝트에서 쓸 수 있는 StarLight 기본 전역 API(Api, 파일 보내기 MediaSharer·room.sendMedia, Log, DataBase, FileStream, Device 등)를 정리했어요.
order: 2
---

StarLight는 프로젝트를 컴파일할 때 아래 전역 API를 스크립트에 넣어 줘요. NeonJS와 Rhino 프로젝트 모두에서 같은 이름으로 쓸 수 있어요. 켜져 있는 플러그인이 등록한 API도 함께 들어와요.

`Api`, `Utils`, `FileStream`, `DataBase`, `Device`, `Bridge`는 메신저봇과 호환되는 API이고, `BotManager`, `Broadcast`, `App`은 메신저봇 R의 API2와 비슷한 API예요.

> [!TIP]
> 에디터의 자동 완성에도 이 API들이 나와요. `Api.`까지 치면 쓸 수 있는 메서드 목록을 볼 수 있어요.

## 답장과 프로젝트 제어: Api

| 메서드 | 설명 |
|---|---|
| `Api.replyRoom(room, message, hideToast?)` | 방 이름으로 메시지를 보내요. 앱이 시작된 뒤 메시지를 받은 적이 있는 방이어야 해요. |
| `Api.replyToID(roomId, message, hideToast?)` | 방 ID(`event.room.id`)로 메시지를 보내요. |
| `Api.canReply(room)` | 그 방에 보낼 수 있는지 알려 줘요. |
| `Api.markAsRead(room?)` | 방의 메시지를 읽음으로 표시해요. |
| `Api.markAsReadOnID(roomId)` | 방 ID로 읽음 표시해요. |
| `Api.getScriptNames()` | 모든 프로젝트 이름 목록이에요. |
| `Api.reload(name?)`, `Api.compile(name?)` | 프로젝트를 다시 컴파일해요. 이름을 빼면 **모든** 프로젝트를 다시 컴파일해요. |
| `Api.on(name?)`, `Api.off(name?)`, `Api.isOn(name)` | 프로젝트를 켜고 끄고, 켜져 있는지 알려 줘요. |
| `Api.isCompiled(name)`, `Api.unload(name)` | 컴파일 여부를 알려 주거나, 컴파일된 상태를 버려요. |
| `Api.showToast(text, length)` | 짧은 안내 메시지를 띄워요. |
| `Api.makeNoti(title, content, id?)` | 기기 알림을 띄워요. |
| `Api.UIThread(fn, callback?)` | `fn`을 메인 스레드에서 실행해요. `callback(error, result)`로 결과를 받아요. |
| `Api.getContext()` | 앱의 Android `Context`예요. |

## 파일 보내기: MediaSharer, room.sendMedia

이미지·동영상·파일을 채팅방에 보내요. 카카오톡 방에는 카카오톡의 공유 화면을 열어 보내고, **디버그룸에서는 채팅에 그 파일이 바로 보여요.** 그래서 파일을 보내는 봇도 디버그룸에서 시험할 수 있어요.

| API | 설명 |
|---|---|
| `MediaSharer.send(room, files, options?)` | `room`은 `msg`, `msg.room`, 카카오톡 채널 ID(문자열 또는 BigInt), 메시지를 받은 적 있는 방 이름이에요. 상대 경로는 **프로젝트 폴더** 기준이에요. |
| `msg.room.sendMedia(files, options?)` | 그 방에 보내요. 어느 프로젝트가 불렀는지 모르므로 **상대 경로는 받지 않아요.** 절대 경로·URL 등을 쓰거나 `MediaSharer.send`를 쓰세요. |
| `MediaSharer.leave(to?, delay?)` | 보낸 뒤 카카오톡에서 나가요. `"home"`(기본), `"starlight"`, 패키지 이름. 채팅방이 열려 있는 동안에는 카카오톡이 알림을 보내지 않아요. |
| `MediaSharer.getRooms()`, `MediaSharer.findRoom(name)` | 메시지를 받은 카카오톡 방 목록과 이름으로 찾은 방이에요(`id`, `name`, `isGroupChat`, `lastSeen`, `userId`). |
| `MediaSharer.clearCache(target?)` | 캐시를 비워요. URL이나 캐시 키를 주면 그것만 지워요. |
| `MediaSharer.hasOverlayPermission()` | '다른 앱 위에 표시' 권한이 있는지 알려 줘요. 없으면 StarLight가 화면에 떠 있을 때만 보낼 수 있어요. |

`files`는 경로(`/sdcard/...`, `file://...`), `http(s)` URL, data URL, Base64 문자열(`base64:...`), Java `byte[]`, NeonJS의 `Uint8Array`·`ArrayBuffer`, `Bitmap`(예: `msg.image`) 중 하나이거나 이들의 배열이에요. 형식은 내용으로 판별해요.

`options`는 `{ fileName, timeout, cache, headers, leave }`예요. 모르는 키는 오타로 보고 거부해요.

보내기는 **바로 반환**하고 결과는 나중에 알려 줘요. NeonJS에서는 Promise라 `await`할 수 있고, Rhino에서는 `CompletableFuture`라 `.join()`으로 기다려요. 실패해도 예외 없이 `ok: false`로 끝나요.

| 결과 | 설명 |
|---|---|
| `ok` | 방이 파일을 받았는지 |
| `sent`, `failed` | 보낸 파일 수, 준비하지 못해 뺀 파일 수 |
| `error` | 하나도 못 보냈을 때 그 이유 |
| `errors` | 뺀 파일마다 `파일: 이유` |
| `room`, `via` | 방 이름과 ID, 다른 경로로 보냈을 때 그 방법(`"user 95"`, `"chooser"`) |

```javascript title="NeonJS"
export async function onMessage(msg) {
    if (msg.message === "!고양이") {
        const r = await MediaSharer.send(msg, "https://example.com/cat.jpg", { leave: "home" });
        if (!r.ok)
            msg.room.send("전송 실패: " + r.error);
    }
}
```

> [!TIP]
> `send_debug_message`의 답장에는 봇이 보낸 파일이 `files`(이름, 형식, 크기)로 들어와요. 이미지에는 `image` ID가 붙어서, `view_image`에 넘기면 그 이미지를 직접 볼 수 있어요.

전송 설정(다운로드 제한 시간, 최대 파일 크기, 전송 후 동작, 캐시)은 앱의 **설정 > 미디어 전송**에 있어요.

## 로그: Log

프로젝트 로그에 기록을 남겨요. 로그는 로그 탭과 에디터 서랍의 로그 탭에서 볼 수 있어요.

| 메서드 | 단계 |
|---|---|
| `Log.v()`, `Log.verbose()` | 상세 |
| `Log.d()`, `Log.debug()` | 디버그 |
| `Log.i()`, `Log.info()` | 정보 |
| `Log.w()`, `Log.warn()` | 경고 |
| `Log.e()`, `Log.error()` | 오류 |
| `Log.wtf()` | 심각 |
| `Log.clear()` | 이 프로젝트의 로그를 지워요. |

모든 메서드는 `Log.i(message)` 또는 `Log.i(tag, message)`로 불러요. NeonJS 프로젝트에서는 `console.log()` 등도 같은 로그에 남아요.

## 데이터 저장

### DataBase

프로젝트 폴더의 `data/` 안에 텍스트 파일로 저장해요. 파일 이름만 넘기면 돼요.

| 메서드 | 설명 |
|---|---|
| `DataBase.setDataBase(file, data)` | 파일에 내용을 써요. 없으면 만들어요. |
| `DataBase.getDataBase(file)` | 파일 내용을 읽어요. **파일이 없으면 오류가 나요.** |
| `DataBase.appendDataBase(file, data)` | 파일 끝에 덧붙여요. 파일이 없으면 오류가 나요. |
| `DataBase.removeDataBase(file)` | 파일을 지워요. |

```javascript
const raw = (() => {
    try { return DataBase.getDataBase("count.txt"); } catch { return "0"; }
})();
DataBase.setDataBase("count.txt", String(Number(raw) + 1));
```

### FileStream

기기의 아무 경로에나 읽고 써요. 경로는 **절대 경로**로 적어요(예: `/sdcard/StarLight/shared/memo.txt`).

| 메서드 | 설명 |
|---|---|
| `FileStream.read(path)` | 파일 내용을 읽어요. 없거나 실패하면 `null`이에요. |
| `FileStream.write(path, data)` | 파일에 써요. 상위 폴더가 없으면 만들어요. |
| `FileStream.append(path, data)` | 파일 끝에 덧붙여요. 파일이 없으면 오류가 나요. |
| `FileStream.remove(path)` | 파일을 지워요. 성공하면 `true`예요. |

### Env

프로젝트 폴더의 `.env` 파일에 적은 값을 읽어요. API 키처럼 코드에 직접 적고 싶지 않은 값을 두기에 좋아요.

```text title=".env"
# 주석
API_KEY=abcd1234
USER_NAME=별빛
GREETING="안녕하세요, ${USER_NAME}"
```

```javascript
const key = Env.get("API_KEY");
```

- 한 줄에 `이름=값`으로 적어요. 이름은 영문자, 숫자, `_`만 쓸 수 있어요.
- `"큰따옴표"` 값과 따옴표 없는 값에서는 `${다른_이름}`으로 **앞에서 정의한** 값을 끼워 넣을 수 있어요. `'작은따옴표'` 값은 적은 그대로예요.
- 형식이 틀리면 로그에 **환경 변수 파일(…) 로드 실패**가 남고 `Env`는 비어 있어요.
- 모든 값은 문자열이에요. `.env`를 고친 뒤에는 다시 컴파일해야 반영돼요.

## 기기와 앱

### Device

| 메서드 | 설명 |
|---|---|
| `Device.getBatteryLevel()` | 배터리 잔량(%)이에요. |
| `Device.isCharging()`, `Device.getPlugType()` | 충전 중인지, 충전 방식(`"acc"`, `"usb"`, `"wireless"`)을 알려 줘요. |
| `Device.getAndroidVersionCode()`, `Device.getAndroidVersionName()` | Android 버전이에요. |
| `Device.getPhoneBrand()`, `Device.getPhoneModel()` | 제조사와 모델명이에요. |
| `Device.isScreenOn()`, `Device.isPowerSaveMode()` | 화면이 켜져 있는지, 절전 모드인지 알려 줘요. |
| `Device.getFreeMemory()`, `Device.getFreeStorageSpace(path)` | 남은 메모리와 저장 공간이에요. |
| `Device.acquireWakeLock(flags, tag?, timeout?)`, `Device.releaseWakeLock()` | 기기가 잠들지 않게 붙잡거나 놓아요. |

### Utils

| 메서드 | 설명 |
|---|---|
| `Utils.getWebText(url)` | 웹 페이지의 HTML을 문자열로 가져와요. |
| `Utils.parse(url)` | 웹 페이지를 [jsoup](https://jsoup.org/) `Document`로 가져와요. `select()`로 원하는 요소를 골라요. |
| `Utils.randomAlphanumeric(length)` | 영문자와 숫자로 된 임의 문자열이에요. |
| `Utils.lw` | 카카오톡의 "전체 보기"를 만드는 보이지 않는 문자열이에요. `"요약" + Utils.lw + "\n긴 내용"`처럼 써요. |

```javascript
const doc = Utils.parse("https://example.com");
const title = doc.select("h1").first().text();
```

> [!NOTE]
> `Utils.getWebText()`와 `Utils.parse()`는 응답이 올 때까지 기다려요. NeonJS 프로젝트에서는 [`fetch`](/docs/neonjs/guides/async#fetch)를 쓰면 기다리는 동안 다른 이벤트를 처리할 수 있어요.

### App, Platform

| 메서드 | 설명 |
|---|---|
| `App.getContext()` | 앱의 Android `Context`예요. |
| `App.runOnUiThread(fn, callback?)` | `fn`을 메인 스레드에서 실행해요. |
| `Platform.getName()`, `Platform.getVersion()` | `"StarLight"`와 앱 버전이에요. |
| `Platform.getUptime()` | 앱이 켜진 뒤 지난 시간(밀리초)이에요. |

## 알림과 위젯

### Notification

기기에 알림을 띄우고, 나중에 내용을 바꾸거나 지워요.

```javascript
const noti = Notification.create(1)
    .setTitle("봇 알림")
    .setText("새 메시지 10개")
    .onClick("확인", (id) => Log.i("눌림: " + id))
    .build();

noti.update("봇 알림", "새 메시지 11개");
noti.delete();
```

`setTitle()`과 `setText()`는 꼭 불러야 해요. `lights(argb, onMs, offMs)`, `onDismiss(fn)`도 쓸 수 있어요.

### HomeWidget

홈의 **스크립트 값** 위젯에 값을 보여 줘요. [홈과 위젯](/docs/app/guides/home#스크립트-값-위젯)을 보세요.

## 프로젝트 사이

| API | 설명 |
|---|---|
| `Broadcast.send(name, value)` | 같은 이름을 구독한 모든 프로젝트의 콜백에 값을 보내요. |
| `Broadcast.register(name, fn)`, `Broadcast.unregister(name, fn)` | 이름을 구독하거나 그만둬요. 다시 컴파일하면 구독은 자동으로 풀려요. |
| `Projects.getSelf()`, `Projects.ofName(name)` | 프로젝트 객체를 가져와요. `info`, `directory`, `isCompiled`, `compile()`, `setEnabled()` 등이 있어요. |
| `Plugins.getAll()`, `Plugins.ofId(id)` | 설치된 플러그인 정보예요. |
| `Bridge.getScopeOf(name)` | 다른 **Rhino** 프로젝트의 전역 객체를 가져와요. |

```javascript title="알림 프로젝트"
Broadcast.register("alert", (text) => Api.replyRoom("공지방", text));
```

```javascript title="다른 프로젝트"
Broadcast.send("alert", "점검이 끝났어요");
```

## 이벤트 방식: BotManager (메신저봇 API2)

메신저봇 R의 API2와 같은 방식이에요. 프로젝트의 [API 방식](/docs/app/guides/projects#api-방식)에 **메신저봇 API2**가 있어야 쓸 수 있고, NeonJS를 권장해요. 동작은 [kbotdocs의 API2 문서](https://kbotdocs.dev/reference/api2)를 따라요.

```javascript
const bot = BotManager.getCurrentBot();

function onMessage(msg) {
    if (msg.content === "/핑") msg.reply("퐁");
}
bot.addListener(Event.MESSAGE, onMessage);

function onCommand(msg) {
    // "@더하기 4 7" → msg.command === "더하기", msg.args → ["4", "7"]
    if (msg.command === "더하기") msg.reply(Number(msg.args[0]) + Number(msg.args[1]));
}
bot.setCommandPrefix("@");
bot.addListener(Event.COMMAND, onCommand);
```

| `msg` 멤버 | 설명 |
|---|---|
| `content`, `room`, `packageName` | 메시지 내용, 방 이름, 메신저 패키지 이름 |
| `channelId`, `logId` | 방 ID와 메시지 ID. BigInt예요. 숫자 ID가 없는 방(디버그 룸)의 `channelId`는 `null`이에요. |
| `author.name`, `author.hash`, `author.avatar` | 보낸 사람 이름, 방 안에서의 고유 ID(`author.userHash`도 같아요), 프로필 사진 |
| `image` | 사진 메시지면 `getBase64()`, `getBitmap()`이 있는 객체, 아니면 `null` |
| `isGroupChat`, `isDebugRoom`, `isMention` | 단체방, 디버그 룸, 멘션 여부. 괄호 없이 읽는 값이에요. |
| `isMultiChat` | 듀얼 메신저 여부. 아직 구분하지 않아 늘 `false`예요. |
| `reply(message)`, `markAsRead()` | 답장, 읽음 표시. 성공하면 `true`예요. |
| `command`, `args` | `command` 이벤트에서만: 접두사 뒤 첫 단어와 그 뒤 단어들의 배열 |

| 이벤트 (`Event.…`) | 리스너 인자 | 언제 |
|---|---|---|
| `MESSAGE` (`"message"`) | `msg` | 메시지를 받았을 때 |
| `COMMAND` (`"command"`) | `msg` | `bot.setCommandPrefix()`로 정한 접두사로 시작하는 메시지를 받았을 때 |
| `NOTIFICATION_POSTED` | `sbn, sessionManager` | 아무 앱이나 알림을 올렸을 때. **onNotificationPosted 이벤트 사용**을 켜야 해요. `sessionManager`는 늘 `null`이에요. |
| `NOTIFICATION_REMOVED` | `sbn, rankingMap, reason` | 알림이 지워졌을 때. 같은 설정을 켜야 해요. |
| `START_COMPILE` | 없음 | 다시 컴파일하기 직전 |

- `Event.Activity.*`, `Event.TICK`, `Event.BATTERY_LEVEL_CHANGED` 상수도 있지만 StarLight에서는 불리지 않아요. 스크립트 전용 화면이 없기 때문이에요.
- `bot`: `on`/`addListener`, `prependListener`, `off`/`removeListener`, `removeAllListeners`, `listeners`, `getListenersMap`, `send(방, 메시지)`(방은 이름, ID, `getRooms()`의 방), `canReply`, `markAsRead`, `setCommandPrefix`, `getName`, `getRootPath`, `getPower`/`setPower`, `compile`, `unload`
- `BotManager`: `getCurrentBot`, `getBot(이름)`(컴파일되지 않았으면 `null`), `getBotList`, `getBotNames`, `getBotCount`, `getRooms`, `getPower`/`setPower`, `compile`, `compileAll`, `prepare`, `prepareAll`, `isCompiled`, `unload`
- `BotManager.getRooms()`는 지금까지 메시지를 받은 방의 목록이에요. 각 방에 `name`, `channelId`, `isGroupChat`, `send(message)`, `markAsRead()`가 있어요.
- 메신저 패키지를 고르는 `packageName` 인자는 받기만 하고 아직 쓰지 않아요.

> [!NOTE]
> 리스너는 다른 이벤트와 같은 규칙을 따라요. 프로젝트 스위치가 꺼져 있거나 허용 이벤트(`starlight.api2.*`)에 없으면 불리지 않고, 리스너에서 오류가 나면 **오류 발생시 비활성화** 설정에 따라 프로젝트가 꺼져요. `START_COMPILE` 리스너의 오류는 로그에만 남아요. 디버그 룸은 스위치가 꺼져 있어도 불러요. 리스너는 프로젝트를 다시 컴파일하거나 스코프를 폐기하면 사라져요.

## 언어마다 다른 전역

| | NeonJS | Rhino |
|---|---|---|
| 콘솔 | `console.log()` 등 | 없음 — `Log`를 써요 |
| 토스트 | `toast(message, duration?)` | `toast(message, duration?)` |
| 타이머 | `setTimeout(fn, ms, ...args)` 등 | `setTimeout(fn, ms)` 등 (추가 인자 없음) |
| HTTP | `fetch`, `WebSocket` | `Utils.getWebText()`, Java API |
| Java 클래스 | `Java.type("…")` → 클래스 객체 | `Packages.…`, `importClass()` (`Java.type()`은 `java.lang.Class`를 돌려줘요) |
| 다른 파일 | `import` | `require()` |

> [!NOTE]
> Google Play 판에서는 리플렉션, 클래스 로딩, 프로세스 실행 같은 일부 Java API를 쓸 수 없어요. [Google Play 판의 제한](/docs/neonjs/guides/java#google-play-판의-제한)을 보세요.
