---
title: Events
summary: How events reach scripts: event functions and API2 listeners, waiting for an answer with Chat.waitFor, scheduled tasks, the conditions for a call, workers and ordering, notification events and plugins' events.
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
| `onStartCompile()` | `starlight.project.compile` | Right before a compiled project compiles again, after API2 `startCompile` listeners. |
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

## Waiting for an answer

`Chat.waitFor` waits for the next message from the same sender in the same room (given a message) or from anyone in
a room (given a room), for a question-and-answer flow without keeping state between events:

```js
export async function onMessage(event) {
    if (event.message !== "!join") return;
    event.room.send("What's your nickname?");
    const answer = await Chat.waitFor(event, 30_000);   // null when none comes in 30 s
    event.room.send(answer ? `Welcome, ${answer.message}!` : "Time's up.");
}
```

- The answer goes to the waiting code only: the project's `onMessage`, `response` and API2 listeners don't get it.
  Other projects do.
- The message that started the wait never answers it. A second `!join` from the same person ends the first wait with
  `null`.
- Rhino has no `await`: pass a callback, `Chat.waitFor(event, 30000, function (answer) { … })`. `join()` on the
  returned future works too, but holds one of the project's workers meanwhile.
- Compiling again ends every wait without an answer.
- In the debug room, messages come from its one sender, so the next one answers. `send_debug_message` reports a reply
  the waiting code sends through the first message's room as one of that message's (`earlierReplies`).
- `Chat.findRoom(name)` and `Chat.getRooms()` give rooms to send to without an event: rooms a message came from since
  StarLight started.

## Scheduled tasks

`Schedule` runs code at times of the day, by the device's clock and time zone:

```js
Schedule.daily("09:00", () => Chat.findRoom("Family")?.send("Good morning!"));
Schedule.cron("0 */2 * * MON-FRI", (run) => Log.i("tick", `late by ${run.lateMillis} ms`));
const reminder = Schedule.once(Date.now() + 10 * 60_000, () => Log.i("reminder", "10 minutes passed"));
reminder.cancel();
```

- Set tasks up in the top-level code. Compiling again cancels the version's tasks, and StarLight compiles the projects
  that are on when it starts, so they are set up again then.
- A task runs only while the project is compiled and on: one due while it is off is skipped. A run missed while
  StarLight wasn't running is skipped too.
- While the device sleeps, Android may let a run come late, as it batches alarms; the callback's `run.lateMillis`
  says how late. Use `setTimeout` or `setInterval` for short delays.
- Sending to a room needs a room StarLight has received a message from since it started: `Chat.findRoom` gives
  `null` before that.
- A callback's error is logged, as a timer's, and doesn't unload the project.

## Workers and order

An event reaches a project only when all of these hold:
- the app's **전역 전원** (global power) is on;
- the project is compiled and on (the debug room and custom buttons call it even when it is off);
- one of its allowed-event entries allows the event, and one of its API levels has it (guide:projects#allowed-events).

Each project queues its events and runs them on its own workers: by default one at a time in NeonJS, in arrival
order, and up to three at once in Rhino, so Rhino code shared between events must allow for that. A project's
**동시 실행 수** (in its settings' **실험적 기능** category, shown while the developer setting **동시 실행 수 변경** is
on) changes how many workers it gets; the app log shows it as `concurrency N` when the project starts. NeonJS code
still runs one event at a time with more workers, since a project has one context, but events may then start out of
arrival order. A full queue (256 events)
drops the oldest. An error thrown out of an event unloads the project, so it has to be compiled again, unless its
**오류 발생시 비활성화** (disable on error) setting is off (guide:projects#errors).

Code that holds a worker longer than the project setting **느린 실행 경고 (초)** (slow run warning, 10 seconds by
default, 0 turns it off) is warned about in the project's log: what runs (the event function, `setTimeout`,
`Schedule.daily`, or NeonJS's code after an `await`) and how many events wait; again at a minute, and when it ends.
It is usually a loop that doesn't end or a call that blocks, such as `Thread.sleep`, `Utils.getWebText` or `join()`.

## Notification events

`onNotificationPosted` and the API2 notification events get every app's notifications, as Android's
`StatusBarNotification`, only while the app setting **onNotificationPosted 이벤트 사용** is on. It is off by default
and costs battery: tell the user to turn it on when a bot needs it. Chat messages don't need it; they come from the
notification rules (guide:app#notification-rules).

## Plugin events

Plugins add events with their own IDs, such as `discord.message.create`. They belong to every project whatever its
API levels, and are received like StarLight's: as the function `list_events` names, once allowed. A disabled plugin's
events don't fire; its entries stay in the project until removed.
