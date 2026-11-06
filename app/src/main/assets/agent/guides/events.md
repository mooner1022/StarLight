---
title: Events
summary: How events reach scripts: event functions and API2 listeners, the conditions for a call, workers and ordering, notification events and plugins' events.
---

`list_events` lists every event with how a script receives it, and `describe_event` shows its arguments as
declarations.

## Event functions

StarLight calls a function with the event's name, with the event's arguments:

| Function | Event ID | When |
|---|---|---|
| `onMessage(event)` | `starlight.message.create` | A chat message arrives. |
| `onMessageDeleted(event)` | `starlight.message.delete` | The latest message received in a KakaoTalk room is deleted. |
| `response(room, msg, sender, isGroupChat, replier, imageDB, packageName)` | `starlight.message.legacy` | A chat message arrives (MessengerBot legacy). |
| `onNotificationPosted(sbn)` | `starlight.notification.post` | Any app posts a notification (see below). |
| `onStartCompile()` | `starlight.project.compile` | Right before a compiled project compiles again. |
| `onProjectButtonClicked(buttonId)` | — | The user taps a custom button on the project's card. |

- In NeonJS the function must be exported; in Rhino it is a top-level function.
- A project with both StarLight original and MessengerBot legacy gets `onMessage` and then `response` for each message.

## API2 listeners

MessengerBot API2 events reach listeners the script adds, not functions:

```js
const bot = BotManager.getCurrentBot();
bot.addListener(Event.MESSAGE, (msg) => {
    if (msg.content === "/hi") msg.reply("hello");
});
bot.setCommandPrefix("/");
// args is a Java array: Array.from makes a JS array of it, for array methods. Rhino can't spread it.
bot.on(Event.COMMAND, (msg) => msg.reply(`command ${msg.command} ${Array.from(msg.args).join(" ")}`));
```

| `Event.` | Event ID |
|---|---|
| `MESSAGE` | `starlight.api2.message` |
| `COMMAND` | `starlight.api2.command`, for messages starting with the bot's command prefix |
| `NOTIFICATION_POSTED` | `starlight.api2.notification_posted` |
| `NOTIFICATION_REMOVED` | `starlight.api2.notification_removed` |
| `START_COMPILE` | `starlight.api2.start_compile` |

- Listeners added at the top level stay for the life of the compiled code; compiling again starts with none.
- `Event.TICK`, `Event.BATTERY_LEVEL_CHANGED` and `Event.Activity.*` exist, but StarLight never fires them.
- API2's objects follow MessengerBot R's documented API2; `describe_api BotManager` and `describe_event` show what
  StarLight implements and where it differs.

## Workers and order

An event reaches a project only when all of these hold:
- the app's **전역 전원** (global power) is on;
- the project is compiled and on (the debug room and custom buttons call it even when it is off);
- one of its allowed-event entries allows the event, and one of its API levels has it (guide:projects#allowed-events).

Each project queues its events and runs them in order on its own workers. NeonJS runs one at a time; Rhino runs up
to three at once by default, so Rhino code shared between events must allow for that. A full queue (256 events)
drops the oldest. An error thrown out of an event unloads the project, so it has to be compiled again, unless its
**오류 발생시 비활성화** (disable on error) setting is off (guide:projects#errors).

## Notification events

`onNotificationPosted` and the API2 notification events get every app's notifications, as Android's
`StatusBarNotification`, only while the app setting **onNotificationPosted 이벤트 사용** is on. It is off by default
and costs battery: tell the user to turn it on when a bot needs it. Chat messages don't need it; they come from the
notification rules (guide:app#notification-rules).

## Plugin events

Plugins add events with their own IDs, such as `discord.message.create`. They belong to every project whatever its
API levels, and are received like StarLight's: as the function `list_events` names, once allowed. A disabled plugin's
events don't fire; its entries stay in the project until removed.
