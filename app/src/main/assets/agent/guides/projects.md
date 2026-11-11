---
title: Projects
summary: What a project is: its files and project.json, the API levels that decide its globals and events, allowed-event entries, compiling and turning on, errors, and calling other projects.
---

A project is one bot: a folder with its info, a main script and any other files, in one language that can't change
after it is created.

## Files

GitHub builds keep projects in `StarLight/projects/<name>/` on shared storage. The Google Play build keeps the same
layout in an app-only folder (guide:play-build#data-folder).

```text
<name>/
├─ project.json     the project's info (below)
├─ <name>.js        the main script, unless renamed
├─ config.json      the project's settings, its language settings included
├─ logs.json        its recent logs
├─ data/            a usual place for what the script saves
└─ debugroom/       the debug room's chat history
```

Tools take paths relative to the project folder. `FileStream` and other file APIs resolve relative paths as their
docs say: check `describe_api` before relying on one.

## project.json

Written by StarLight; change it with the tools (`set_project_events`, `set_project_enabled`), not by editing the
file.

| Field | Meaning |
|---|---|
| `name`, `id` | The name and a UUID. |
| `mainScript` | The file compiled first. |
| `languageId` | `js_neon` (NeonJS), `JS_RHINO` (Rhino), or a plugin's language. |
| `isEnabled` | Whether the project is on; scripts read it as `info.enabled`. |
| `allowedEventIds` | The allowed-event entries (below). |
| `apiLevels` | The chosen API levels; missing in projects from before levels, which get every API. |

## API levels

A project chooses one or more API levels. Its scripts get only those levels' globals and events; an object several
levels share is given once. Plugins' APIs and events belong to every project, whatever its levels.
`onProjectButtonClicked(buttonId)` isn't an event either: every compiled project with custom buttons gets it
(guide:events#event-functions). `LibraryManager` is a global in every project too, but only while the developer setting
**외부 dex 라이브러리 로드** is on (guide:app#settings).

| Level | Globals | Events |
|---|---|---|
| StarLight original (`starlight`) | `Log`, `Projects`, `Plugins`, `Languages`, `Platform`, `Env`, `Java`, `Notification`, `HomeWidget`, `MediaSharer`, `EventEmitter` | `onMessage`, `onMessageDeleted`, `onNotificationPosted`, `onStartCompile` |
| MessengerBot API2 (`msgbot.api2`) | `Log`, `BotManager`, `Event`, `Broadcast`, `App`, `Device`, `FileStream` | listeners added with `bot.on(Event.…)` |
| MessengerBot legacy (`msgbot.legacy`) | `Log`, `Api`, `Utils`, `DataBase`, `Bridge`, `Device`, `FileStream` | `response`, `onNotificationPosted`, `onStartCompile` |

`list_apis` with the project lists exactly what it gets, and `list_projects` shows each project's levels. New projects
start with StarLight original; NeonJS suits it and API2 best. Changing levels takes effect on the next compile.

## Allowed events

A project receives only the events its allowed-event entries allow, and only if one of its API levels has the event
(or no level does, as with plugins' events). Entries are:
- an event ID: `starlight.message.create`;
- a wildcard: `starlight.*` (every StarLight event, including ones added later), `*` (every event);
- an exclusion starting with `!`: `!starlight.message.legacy` takes that event back out of a wildcard.

`set_project_events` changes them; changes apply without compiling again. `list_events` with a project shows what it
receives.

## Compiling and turning on

- Compiling reads the main script and its imports and runs the top-level code. A project must compile before it can
  receive events or be turned on.
- Turning a project on (`set_project_enabled`) makes it answer real chats. Testing in the debug room doesn't need it.
- Projects that are on compile when the app starts; one that fails to compile then is turned off.
- Compiling again replaces the running code; NeonJS projects' timers, sockets and listeners from before go away.
  Turning a project off stops new events but not timers it set; compile again to stop those.
- A compile that fails stops the project: the version that was running is unloaded, since its timers and listeners
  were already ended for the new code. It keeps its on/off switch and runs again once a compile succeeds. Only when
  the main script can't be read does the running version stay as it is.

## Errors

- An error thrown out of an event function, a listener or a function another project called is logged as an error.
  When the project setting **오류 발생시 비활성화** (disable on error) is on, the default, it also unloads the project:
  turns it off and discards its compiled code. Nothing reaches it then, the debug room included, until it is compiled
  again (`compile_project`), and turned on again for real chats.
- A compile error names the file and line. `get_project_logs` shows the logs, stack traces included.
- An error in a timer or in a Promise nobody handles is logged and doesn't unload the project.

## Calling other projects

`Projects.ofName(name)` and `Projects.ofId(id)` return another project, `Projects.getSelf()` this one; `null` if
there is none. Call one of its functions with `callFunction(name, args, onError)`:

```js
const other = Projects.ofName("scheduler");
if (other) other.callFunction("addReminder", ["room", "text"], (e) => Log.e("scheduler", String(e)));
```

- Pass all three arguments: there is no shorter overload.
- The other project must be compiled (`other.compiled`). The call runs on that project's workers, not in the caller,
  and doesn't return the function's result.
- A project's top-level code runs before its compile finishes, so calling its own functions this way from there
  fails with `onError`: call them directly.
- The other project's function must be exported if it is a NeonJS project.
- `describe_api Project` shows what else a project has, such as `info.id` and `getDataDirectory()`.

Legacy Rhino projects also have `Bridge.getScopeOf(name)`: another compiled Rhino project's global object, whose
variables and functions read as properties (`Bridge.getScopeOf("other").count`). It throws while that project isn't
compiled, as during its first compile.
