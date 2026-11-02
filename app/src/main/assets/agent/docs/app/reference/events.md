---
title: 이벤트 함수
description: 메시지 수신, 삭제, 알림, 컴파일 같은 일이 생겼을 때 StarLight가 부르는 스크립트 함수와 인자를 정리했어요.
order: 1
---

스크립트에 정해진 이름의 함수를 만들어 두면, 그 일이 생길 때 StarLight가 함수를 불러요. **NeonJS 프로젝트에서는 함수를 `export` 해야** 해요.

```javascript title="NeonJS"
export function onMessage(event) { … }
```

```javascript title="Rhino"
function onMessage(event) { … }
```

## 기본 이벤트

| 함수 | 이벤트 ID | 언제 불리나요 |
|---|---|---|
| `onMessage(event)` | `starlight.message.create` | 메시지를 받았을 때 |
| `onMessageDeleted(event)` | `starlight.message.delete` | 카카오톡에서 가장 최근 메시지가 삭제됐을 때 |
| `response(room, msg, sender, isGroupChat, replier, imageDB)` | `starlight.message.legacy` | 메시지를 받았을 때. **레거시 이벤트 사용**을 켜야 해요. |
| `onNotificationPosted(sbn)` | `starlight.notification.post` | 아무 앱이나 알림을 올렸을 때. **onNotificationPosted 이벤트 사용**을 켜야 해요. |
| `onStartCompile()` | `starlight.project.compile` | 이미 컴파일된 프로젝트를 다시 컴파일하기 직전 |
| `onProjectButtonClicked(buttonId)` | — | 프로젝트 카드의 사용자 지정 버튼을 눌렀을 때 |

이벤트는 아래 조건을 모두 만족할 때만 불려요.

- 설정의 **전역 전원**이 켜져 있어요.
- 프로젝트가 컴파일되어 있고, 켜져 있어요. (디버그 룸과 사용자 지정 버튼은 꺼져 있어도 불러요.)
- 프로젝트 설정의 **호출이 허용된 이벤트**에 그 이벤트 ID가 들어 있어요. 기본값 `starlight.*`은 위의 기본 이벤트를 모두 허용해요.

이벤트는 프로젝트마다 순서대로 대기열에 들어가 차례로 실행돼요. 대기열이 가득 차면(256개) 가장 오래된 이벤트부터 버려져요.

> [!WARNING]
> 이벤트 함수에서 처리하지 않은 오류가 나면 로그에 **실행 중 에러 발생**이 남고, 프로젝트 설정의 **오류 발생시 비활성화**(기본 켜짐)에 따라 프로젝트가 꺼져요.

## onMessage

```javascript title="NeonJS"
export function onMessage(event) {
    const { message, sender, room } = event;
    if (message === "/방정보")
        room.send(`${room.name} (${room.isGroupChat() ? "단체방" : "1:1"}) · ${sender.name}`);
}
```

### event

| 속성 | 형식 | 설명 |
|---|---|---|
| `message` | 문자열 | 메시지 내용이에요. |
| `sender` | 객체 | 보낸 사람이에요. 아래 표를 보세요. |
| `room` | 객체 | 메시지가 온 방이에요. 아래 표를 보세요. |
| `packageName` | 문자열 | 메신저 앱의 패키지 이름이에요 (`com.kakao.talk`). |
| `hasMention` | 불리언 | 나를 멘션한 메시지인지 알려 줘요. |
| `chatLogId` | 숫자 | 카카오톡 메시지 ID예요. 디버그 룸에서는 `-1`이에요. |
| `image` | `Bitmap` 또는 `null` | 알림에 붙은 이미지예요. 대부분 `null`이에요. |

### event.room

| 멤버 | 설명 |
|---|---|
| `name` | 방 이름이에요. 1:1 방은 상대 이름이에요. |
| `id` | 방을 구분하는 문자열이에요. |
| `isGroupChat()` | 단체방이면 `true`예요. |
| `isDebugRoom()` | 디버그 룸에서 온 메시지면 `true`예요. |
| `send(message)` | 이 방에 메시지를 보내요. 성공하면 `true`예요. |
| `markAsRead()` | 이 방의 메시지를 읽음으로 표시해요. |

### event.sender

| 멤버 | 설명 |
|---|---|
| `name` | 보낸 사람 이름이에요. |
| `id` | 보낸 사람 ID예요. Android 9 미만과 디버그 룸에서는 `null`이에요. |
| `profileBase64` | 프로필 이미지의 Base64 문자열이에요. |
| `profileHash` | 프로필 이미지의 해시 값(숫자)이에요. 같은 사람인지 가늠할 때 써요. |
| `profileBitmap` | 프로필 이미지(`Bitmap`)예요. |

> [!IMPORTANT]
> `isGroupChat`과 `isDebugRoom`은 **괄호를 붙여 불러야** 해요. `room.isGroupChat`처럼 괄호 없이 쓰면 함수 자체가 돌아와서 조건문에서 항상 참이 돼요. NeonJS에서는 `room.groupChat`, `room.debugRoom`으로 읽어도 돼요.

> [!NOTE]
> NeonJS에서 `chatLogId`처럼 2⁵³보다 큰 숫자는 BigInt로 들어와요. 비교할 때는 `String(event.chatLogId)`처럼 문자열로 바꾸세요. [Java와 Android API 쓰기](/docs/neonjs/guides/java#값이-바뀌는-규칙)를 보세요.

## onMessageDeleted

카카오톡에서 **가장 최근에 받은 메시지**가 삭제되면 불려요.

| `event` 속성 | 설명 |
|---|---|
| `message` | 삭제된 메시지 내용이에요. |
| `sender` | 보낸 사람 이름(문자열)이에요. |
| `room` | 방이에요. 알 수 없으면 `null`이에요. |
| `packageName`, `chatLogId` | `onMessage`와 같아요. |

## response (레거시)

메신저봇, 채자봇 같은 다른 앱의 봇 코드와 호환되는 함수예요. **설정 → 알림, 이벤트**에서 **레거시 이벤트 사용**을 켜야 불려요. 켜 두면 메시지마다 `onMessage` 다음에 `response`도 불려요.

```javascript title="Rhino"
function response(room, msg, sender, isGroupChat, replier, imageDB) {
    if (msg === "/안녕")
        replier.reply(sender + "님 안녕하세요!");
}
```

| 인자 | 설명 |
|---|---|
| `room` | 방 이름이에요. |
| `msg` | 메시지 내용이에요. |
| `sender` | 보낸 사람 이름이에요. |
| `isGroupChat` | 단체방이면 `true`예요. |
| `replier` | 답장 객체예요. 아래 표를 보세요. |
| `imageDB` | 프로필 이미지 객체예요. `getProfileBase64()`, `getProfileHash()`, `getProfileBitmap()`이 있어요. |

일곱 번째 인자 `packageName`은 넘어오지 않아요(`undefined`). 패키지 이름이 필요하면 `onMessage`를 쓰세요.

| `replier` 메서드 | 설명 |
|---|---|
| `reply(message)` | 메시지가 온 방에 답장해요. |
| `reply(room, message, hideToast?)` | 방 이름으로 그 방에 보내요. 앱이 시작된 뒤 메시지를 받은 적이 있는 방이어야 해요. 실패 안내를 숨기려면 `hideToast`를 `true`로 넘겨요. |

읽음 처리는 `replier`에 없으니 `Api.markAsRead()`를 쓰세요.

## onNotificationPosted

**설정 → 알림, 이벤트**에서 **onNotificationPosted 이벤트 사용**을 켜면, StarLight를 뺀 **모든 앱의 알림**이 이 함수로 들어와요. 인자 `sbn`은 Android의 [`StatusBarNotification`](https://developer.android.com/reference/android/service/notification/StatusBarNotification)이에요.

```javascript title="NeonJS"
export function onNotificationPosted(sbn) {
    const extras = sbn.getNotification().extras;
    console.log(sbn.getPackageName(), String(extras.getCharSequence("android.title")));
}
```

알림이 많은 기기에서는 부하가 커질 수 있어요. 필요할 때만 켜세요.

## onStartCompile

이미 컴파일된 프로젝트를 다시 컴파일할 때, 이전 상태를 정리하기 **직전에** 불려요. 처음 컴파일할 때는 불리지 않아요. 열어 둔 연결을 닫거나 값을 저장하는 데 쓰고, 오래 걸리는 작업은 하지 마세요.

## onProjectButtonClicked

프로젝트 설정의 **실험적 기능 → 사용자 지정 버튼**에 버튼을 추가하면 프로젝트 카드에 버튼이 생겨요. 버튼을 누르면 버튼에 정한 ID가 `buttonId`로 넘어와요. 프로젝트가 컴파일되어 있어야 하고, 꺼져 있어도 불려요.

```javascript title="NeonJS"
export function onProjectButtonClicked(buttonId) {
    if (buttonId === "reset") {
        DataBase.setDataBase("count.txt", "0");
        toast("초기화했어요");
    }
}
```

## 플러그인 이벤트

플러그인이 더한 이벤트도 같은 방식으로 받아요. 프로젝트 설정의 **호출이 허용된 이벤트**에 플러그인 이벤트 ID(예: `webserver.*`)를 더하고, 플러그인이 정한 이름의 함수를 만들어요. 함수 이름과 인자는 각 플러그인의 문서를 보세요.
