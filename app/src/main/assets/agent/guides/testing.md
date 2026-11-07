---
title: Testing
summary: Testing bots in the debug room: what send_debug_message delivers, how debug messages differ from real ones, and replies that come later.
---

## The debug room

The debug room is a chat screen in the app that sends messages to one project, without a messenger. It needs the
project compiled, not turned on. Its settings (room name, app package, group chat, sender and bot names and
profiles) are shared by every project's debug room.

`send_debug_message` sends a message the same way without showing it on screen, and returns the bot's replies: text,
files sent with `MediaSharer.send` or `room.sendMedia`, errors and the logs written meanwhile. It delivers the message
to `onMessage` and to API2's message and command listeners, whichever the project receives.

- It doesn't call the legacy `response` function. Test a MessengerBot legacy project's logic by moving it into a
  function `onMessage` calls too, or ask the user to try the debug room screen, which does call `response`.
- `view_image` shows an image the bot sent.

## How debug messages differ

Check `event.room.isDebugRoom()` in a script to tell them apart.

| Value | In the debug room |
|---|---|
| `event.room.isDebugRoom()` (or `event.room.debugRoom`) | `true` |
| `event.room.name` | The debug room's room name; `"undefined"` until the user sets one |
| `event.sender.id` | `null` |
| `event.chatLogId` | `-1` |
| `event.image` | `null` |
| `event.hasMention` | `false` from the tool; in the screen, a message with `<@name>` is a mention |
| `event.packageName` | The debug room's app package, `com.kakao.talk` by default |
| `replier.reply(room, text)` | Ignores the room and answers in the debug room. |
| API2 `msg.channelId` | `null`: the debug room's ID isn't numeric. |

Sending to another room by name (`Api.replyRoom`, `bot.send`) needs a real room StarLight has received a message
from; in tests such a call usually reports failure.

## Replies that come later

By default `send_debug_message` returns once the bot has been quiet for a second after replying, or after a few
seconds without a reply.
- For a bot that answers after `await`, a timer or a network request, set `expectReplies` to the number of replies to
  wait for, or `waitSeconds` to how long to collect.
- Replies that arrive after it returns are kept: `read_debug_replies` returns them, and the next
  `send_debug_message` reports them as `earlierReplies`.
- Each reply carries the seconds it took, to check timing.
