# Agent docs

Specification for the documentation StarLight AI reads about the script APIs, events and engines.

- Status: agreed design, 2026-11-06. Implementation follows this document; changes to it are made here first.
- Decisions:
  - Prose comes from KDoc, extracted at build time by KSP.
  - Docs are written in English.
  - The copy of the site's human docs leaves the APK.

## 1. Goals

- The agent knows exactly what a given project's scripts can use: objects, members, events and their arguments. This is filtered by the project's API levels, its language and the plugins that are loaded.
- Facts can't drift from the code. Structure is read from what is registered at run time, and prose sits next to the declaration it describes.
- Plugins document their APIs and events the same way the app does, with no extra setup beyond the StarLight Gradle plugin (slplugin) and KSP.
- Output is compact: signatures in TypeScript declaration form, with one-line summaries. Long explanations live in guides that the agent opens only when it needs them.

Non-goals:
- Human documentation. The site (starlight-landing) keeps its own docs, written for people.
- Translations.
- A Rhino-specific API view. Differences from NeonJS are described in a guide.

## 2. Overview

Three sources feed one catalog, and the agent reads that catalog through tools.

| Source | Gives | Where |
|---|---|---|
| Run-time reflection | What exists for a project, and how a script reaches it: global objects, members as NeonJS exposes them, events and their argument types | `Session.apiManager`, `ApiLevelManager`, `ProjectEventManager`, the classes they name |
| KDoc, extracted by KSP | Summaries, details, parameter names, nullability, generic types, optional parameters, `@since`, related guides | `starlight/agent/<module>.api.json` in each APK |
| Guides | Concepts that belong to no single declaration: engine rules, the project model, event delivery, testing, Play limits, where things are in the app | Markdown under `assets/agent/guides/` |

```
registered APIs, levels, events ──reflection──┐
<module>.api.json (KSP, from KDoc) ───────────┼──► Catalog (app) ──► renderer (.d.ts) ──► agent tools
assets/agent/guides/**.md ────────────────────┘
```

## 3. Writing docs

### 3.1 What must be documented

The **script surface** is every class a script can reach:
- the instance class of each registered `Api`, both `OBJECT` (a per-project instance as a global) and `CLASS` (the class itself: constructors and static members);
- the types of the arguments a script receives from each registered `ProjectEvent` (§3.3);
- recursively, the classes that the members of those classes take and return.

The closure stops at **documented packages**: the package prefixes each module extracts (`starlight.docs.packages`, §4). A type outside them is shown by its fully qualified name, and its members aren't followed. This applies to the JDK, Android and Kotlin classes, and to any other class.

Phase 1 documents these packages:
- **App:** `dev.mooner.starlight.api`, `dev.mooner.starlight.media` and `dev.mooner.starlight.listener`.
- **PluginCore:**
  - `dev.mooner.starlight.plugincore.chat` (`Message`, `ChatRoom`, `ChatSender`, `DeletedMessage`, `MediaFile`, …);
  - `dev.mooner.starlight.plugincore.logger`, because the `Log` API is its `ProjectLogger`;
  - `dev.mooner.starlight.plugincore.library`, for the `LibraryManager` API that PluginCore registers while a developer setting is on;
  - single classes, which the option also takes: `Project`, `ProjectInfo`, `StarlightPlugin`, `PluginInfo` and `Language`, which `Projects`, `Plugins` and `Languages` return.

Of `Project`, `StarlightPlugin` and `Language`, whose public surface is mostly the plugin API, only what scripts use is described (`info`, `compiled`, `callFunction`, `id`, `name`, …); the rest is `@suppress`ed. Their packages stay outside. `describe_api` still shows a type outside the docs, such as a plugin's, from reflection without descriptions (§7); for Java's, Kotlin's and Android's classes it says to use their own references.

Every public member of a class inside the closure needs KDoc, unless it is marked `@suppress`. The coverage test (§11) checks exactly this set.

### 3.2 KDoc rules

```kotlin
/**
 * The bots of this device's projects; a global in API2 projects.
 *
 * Details, edge cases and short examples go here, as Markdown.
 *
 * @see guide:events#api2-listeners
 */
class BotManager(private val project: Project) {

    /**
     * The rooms bots can send to, from the messages received so far.
     *
     * @param packageName The messenger to list rooms of. Not used yet; every messenger's rooms are returned.
     * @return The rooms, in no particular order.
     * @since 1.0.1
     */
    @JvmOverloads
    fun getRooms(packageName: String? = null): Array<Api2Room> = …
}
```

- **Summary:** the first sentence. It is the one line shown in lists, so it says what the declaration is or does. Write it for someone writing a script, not for someone maintaining the class.
- **Details:** the paragraphs after the summary are shown only in full descriptions. Use them for behavior, edge cases, threading and errors. Fenced code blocks are allowed.
- **Tags:**
  - `@param` documents each parameter.
  - `@return` describes the result when it isn't obvious.
  - `@throws` names the errors a script may see.
  - `@since` gives the StarLight version that added it.
  - `@suppress` hides a public member from scripts' docs.
  - `@see guide:<id>[#anchor]` links a guide (§3.4).
  - `@listeners <event ID prefix>` marks a function that takes an event name, then a listener (API2's `bot.on`). The renderer declares it once per listener event whose ID starts with the prefix, pairing the event's `ProjectEvent.listenerName` with its listener's arguments: `on(eventName: "message", listener: (msg: Api2Message) => void)`, before the general declaration.
- **Properties:** document the Kotlin property. The renderer shows it as a property when NeonJS does (§6.2).
- **Style:** English; plain facts; no UI tutorials. Say what happens on the unusual paths, such as `null`, missing rooms, the debug room and Play builds.

### 3.3 Events

A `ProjectEvent` subclass is documented on the class:

```kotlin
/**
 * A chat message arrived, in a room of an app the notification rules cover.
 *
 * Called once per message, on the project's workers. The debug room calls it too, see guide:testing.
 *
 * @param event The message: its text, sender and room, and the room to reply to.
 */
class ProjectOnMessageEvent : ProjectEvent() { … }
```

- The summary says when the event happens.
- There is one `@param` for each argument the script receives, if it receives any, using the argument's name.
- `description` and `usage` stay run-time properties, as they are UI text shown in the app. The catalog shows `usage` next to the KDoc.

**What the script receives.** `argTypes` are the arguments StarLight fires an event with. They aren't always what a script gets: an API2 event is fired with a `Message`, but `dispatch` hands its listeners an `Api2Message`. Another example: `notification_posted` listeners get `(sbn, sessionManager)`, where `sessionManager` is always `null`.

So `ProjectEvent` gains a property, added in PluginCore 1.1.0:

```kotlin
/** The arguments a script's function or listener receives; the ones the event is fired with, unless dispatch changes them. */
open val scriptArguments: Array<CodeGenerator.Argument<*>>
    get() = argTypes
```

Events whose `dispatch` changes the arguments override it. The API2 events and anything that wraps arguments do. The catalog, the coverage test and the `@param` rule use `scriptArguments`.

### 3.4 Guides

- **Location:** `app/src/main/assets/agent/guides/<id>.md`. In a plugin, `src/main/assets/agent/guides/<id>.md`.
- **ID:** the path under `guides/` without `.md`, such as `engines/neonjs`.
- **Front matter:**
  ```markdown
  ---
  title: NeonJS
  summary: How NeonJS runs scripts: ES modules and exports, async, fetch and WebSocket, console, Java access.
  ---
  ```
  `summary` is the one line shown in the guide index and in "Related" lists.
- **Anchors:** each heading's slug: lowercased, spaces as `-`, characters other than letters, digits and `-` removed. `## Java access` becomes `#java-access`.
- **References:**
  - From KDoc: `@see guide:<id>[#anchor]`.
  - Between guides: Markdown links to `guide:<id>[#anchor]`.
  - A guide of another module takes the module's ID in front: `guide:starlight:engines/neonjs`, or `guide:discord:setup` from the app. Without one, the ID is resolved in the module that holds the reference.
- **Size:** one topic per guide, ideally under 4,000 words. Split by topic rather than writing one long page.

## 4. Build-time extraction (KSP)

- **Module:** `:docs-processor`, a JVM module with a KSP2 `SymbolProcessorProvider`, in this repository.
- **Applied to:** `:app` and `:PluginCore`, in every variant.
- **Options:**
  - `starlight.docs.module`: the module ID, `starlight` for `:app` and `plugincore` for `:PluginCore`; a plugin's ID in plugins.
  - `starlight.docs.packages`: comma-separated package prefixes to extract, with their subpackages, or class names, with their nested classes. Leaving it out means every package.
- **For each public class, interface and object** in those packages, it emits:
  - its JVM name and KDoc;
  - each public constructor, function and property, keyed by JVM name and JVM descriptor (`Resolver.mapToJvmSignature`). Property accessors are keyed as their getter and setter methods, so `@get:JvmName` is respected.
  - Kotlin types with nullability and type arguments, parameter names, and which parameters have defaults;
  - the parsed KDoc: summary, details, `@param`, `@return`, `@throws`, `@since`, `@suppress` and the guide references from `@see`.
- **Details the processor handles:**
  - A type alias is written as the type it stands for: a script gets that type, and the alias may not be public (`typealias EventCallback = (Array<out Any>) -> Unit`).
  - A `vararg` parameter is keyed with its array descriptor. KSP's signature gives the element type's.
  - A `suspend` function is keyed as the JVM has it, with the `Continuation` parameter and `Object` result that KSP's signature leaves out.
  - A primary constructor without KDoc of its own takes the class's; the renderer then repeats only its `@param`s. To hide a constructor scripts shouldn't call, make it non-public (`Notification`'s is private, with an internal factory): KSP doesn't read a KDoc written before the `constructor` keyword.
  - KSP gives the doc text without its `*` margins, so a line may start with `**bold**`. Margins are stripped only when every line has one, and only the indentation all lines share is removed, so code blocks keep theirs.
  - The output is declared as depending on every processed source (aggregating). Otherwise an incremental build that recompiles one file writes a file with only that file's classes.
  - KSP2 analyses in the Gradle daemon. With several KSP tasks running at once (three variants in one build), one of them can run without the Android classes, so their types come out unresolved and the keys stop matching. The root build runs KSP tasks one at a time through a shared build service, and the processor fails the build when its output has an unresolved type, rather than shipping docs that silently don't join.
- **Output:** a Java resource `starlight/agent/<module>.api.json`, packaged at the APK root. It isn't put under `META-INF/`, which plugin builds exclude. Phase 0 confirmed that the release APK and AAB keep the file and that the app's class loader finds it.
- **Format:** carries `"format": 1`. A reader skips files with a format it doesn't know.

```json
{
  "format": 1,
  "module": "starlight",
  "packages": ["dev.mooner.starlight.api", "dev.mooner.starlight.media", "dev.mooner.starlight.listener"],
  "classes": {
    "dev.mooner.starlight.api.api2.BotManagerApi$BotManager": {
      "kind": "class",
      "doc": { "summary": "…", "details": "…", "since": null, "see": ["guide:events#api2-listeners"] },
      "members": {
        "getRooms(Ljava/lang/String;)[Ldev/mooner/starlight/api/api2/Api2Room;": {
          "kind": "function",
          "params": [{ "name": "packageName", "type": "kotlin.String?", "optional": true }],
          "returns": "kotlin.Array<dev.mooner.starlight.api.api2.Api2Room>",
          "doc": { "summary": "…", "params": { "packageName": "…" }, "returns": "…" }
        }
      }
    }
  }
}
```

## 5. Run-time catalog

The catalog lives in the app (`dev.mooner.starlight.agent.docs`). It is built lazily, and rebuilt when APIs, events or plugins change.

1. **Inputs:**
   - registered `Api`s with their API levels. The app's own APIs and events are listed in `CoreApis`, which `ApplicationSession` registers from and the tests read;
   - registered `ProjectEvent`s with their IDs, API levels and `scriptArguments`;
   - the doc files `starlight/agent/starlight.api.json` and `starlight/agent/plugincore.api.json` from the app's class loader (plugins' from their packages, §9);
   - the guides.
2. **Script view:** each class is read with Java reflection the way NeonJS exposes it (§6.2), so the catalog lists what a script can actually call.
3. **Join:** each member is matched to its doc entry by class, JVM name and descriptor.
   - KSP records one descriptor per Kotlin declaration, the full one. Reflection also sees the shorter overloads `@JvmOverloads` generates.
   - A reflected method whose parameters are a prefix of a documented declaration's, with the same name and return type, belongs to that declaration.
   - A parameter is rendered optional (`?`) only when such a shorter overload exists. A Kotlin default without one is still required, because NeonJS can't call it with fewer arguments.
   - Where no entry matches (an undocumented plugin, or a member added without KDoc), the member keeps its reflected shape. Parameter names and nullability come from Kotlin's metadata through `kotlin-reflect`; Java reflection has names only when compiled with `-java-parameters` (`arg0` otherwise), and no nullability. An overload `@JvmOverloads` made takes the names of the longer one it shortens.
   - A member marked `@suppress` reaches no further types, so a description's "Without docs" list names only what it shows.
   - A companion's `@JvmStatic` member is documented on the companion (`…$Companion`) but reflected on the class, so the join looks in both.
   - From `java.lang.Enum`, only `name()` and `ordinal()` are shown, with docs of their own; the rest of `Enum` is hidden.
4. **For a project:**
   - only APIs in its API levels, or in no level (plugins'), are listed;
   - globals its language provides itself (`Language.nativeGlobals`) are left out;
   - events are marked as received or not, by its allowed events.
5. **Closure:** the types reachable from the project's APIs and events, as in §3.1.

## 6. Rendering

### 6.1 Format

Declarations in TypeScript syntax, with the summary as a `/** */` comment, and details only in full descriptions:

```ts
/** The bots of this device's projects; a global in API2 projects. */
declare const BotManager: {
  /** The bot of the project running this script. */
  getCurrentBot(): Bot;
  /** The rooms bots can send to, from the messages received so far. */
  getRooms(packageName?: string | null): JavaList<Api2Room>;
};

interface Bot {
  /** Sends `msg` to `room`, a room name, ID or Api2Room; whether the room was found. */
  send(room: string | bigint | Api2Room, msg: string, packageName?: string | null): boolean;
}

// Related: guide:events#api2-listeners — How API2 listeners receive events and on which threads.
```

- `OBJECT` APIs render as `declare const <name>: { … }`. `CLASS` APIs render as `declare class <name> { constructor(…); static … }`.
- Other documented classes render as `interface <SimpleName>`. Nested classes take their simple name.
- Members follow §6.2.
- A full description adds details, `@param`/`@return`/`@throws` text and `@since` as JSDoc tags. It ends with "Related" lines for the referenced guides, each with its summary.

### 6.2 Members as NeonJS exposes them

These rules mirror `HostClassInfo` in NeonJS:
- **Names:** a member's JS name is its `@HostName` if it has one, otherwise its JVM name. The rules below apply to JS names.
- **Methods:** public methods, static and instance.
- **Properties:**
  - A method named `getX` with no parameters and a non-void result becomes property `x`. So does `isX` with no parameters returning `boolean`.
  - A one-parameter `setX(…)` makes the property writable. A `setX` with no getter stays a method (`Bot.setCommandPrefix`): NeonJS also takes `x = …`, but shown as a property it would read as one that can be read back.
  - No property is made when its name is already a method or a field.
  - Properties are shown as properties only; the getter methods aren't listed again.
- **Getters declared as functions:** `fun getCurrentBot()` (not a Kotlin property) is shown as the method its author
  wrote, as MessengerBot's docs call it. NeonJS reads it as property `currentBot` too, but the docs show one form.
- **Fluent setters:** a `setX(…)` that returns a value (a builder's) stays a method; no property is made of it.
- **Collections:** a class that is a Java `Map` or `List` is declared as extending `JavaMap`/`JavaList`, without
  listing the collection's own members.
- **Kotlin booleans:**
  - A Kotlin `val isFoo` compiles to `isFoo()`, so scripts see it as `foo`.
  - With `@get:JvmName("getIsFoo")`, as `Api2Message` does, they see `isFoo`.
  - The renderer shows whichever one scripts see.
- **Fields:** public fields, as properties. A `lateinit` property's backing field is public to Java; one that isn't meant for scripts is `@field:JvmSynthetic`, which hides it.
- **Inherited members:** a documented class shows what it declares, and what it inherits from documented classes, not what an outside supertype brings (`CoroutineScope.coroutineContext` on `Project`). `java.lang.Enum`'s `name()` and `ordinal()` stay (§5).
- **Left out:**
  - `Object`'s methods;
  - Kotlin-generated members (`componentN`, `copy`, `*$default`, `access$*`);
  - members marked `@suppress`.
- **Overloads:**
  - A chain made by `@JvmOverloads` (or matching the KDoc's optional parameters) becomes one signature with `?` parameters.
  - Other overloads are listed separately.
  - NeonJS calls only an overload whose parameter count matches, so a parameter is optional only when such an overload exists.

### 6.3 Types

| JVM/Kotlin | Rendered |
|---|---|
| `String`, `Char` | `string` |
| `Int`, `Short`, `Byte`, `Float`, `Double` and their boxes | `number` |
| `Long` and its box | `number \| bigint` (a BigInt beyond 2^53, so 64-bit IDs keep their value). This is NeonJS's; Rhino gives a number that loses precision beyond 2^53, which the Rhino guide says. |
| `Boolean` | `boolean` |
| `BigInteger` | `bigint` |
| `Unit`, `void` | `void` |
| `Any`, `Object` | `any` |
| `Array<T>`, `List<T>` (and subtypes) | `JavaList<T>`. Both engines read them by index and with `for...of`, and `Array.from(x)` copies them; `Java.from` exists only in NeonJS. |
| `Map<K, V>` | `JavaMap<V>` |
| `Set<T>` | `JavaSet<T>`: `for...of`, `size()` and `contains(x)`, no index |
| an array or `List` parameter | `T[] \| JavaList<T>`: both engines convert a JS array for it (not for a `Set`) |
| `CompletionStage<T>`, `CompletableFuture<T>` | `Promise<T>` |
| Kotlin function types and type aliases of them, Java interfaces with one abstract method (`Callable`, `Runnable`) | `(p1: A) => R`. NeonJS calls a JS function for any interface's methods. KSP gives no parameter names, so they are numbered. |
| `Nothing` (only ever `null`, such as API2's `sessionManager`) | `null` |
| a nullable type | `T \| null` |
| a documented class | its simple name |
| any other class, including a `CharSequence` that isn't a `String` | its fully qualified name, such as `android.graphics.Bitmap` |

NeonJS doesn't turn Java arrays, lists and maps into JS values (`HostObjects`), so they get their own types. Every description that uses them begins with:

```ts
/** A Java array or List: elements by index, `length` and `for...of`. It isn't a JS array: `Array.from(x)` makes one, for array methods. */
interface JavaList<T> { readonly length: number; readonly [index: number]: T; [Symbol.iterator](): Iterator<T> }
/** A Java Map: its entries are properties, read as `map[key]` or `map.key`. */
interface JavaMap<V> { readonly [key: string]: V }
```

This table lives in one class, and it is the only place to change.

## 7. Agent tools

| Tool | Input | Output |
|---|---|---|
| `list_apis` | `project` (optional) | One line per API: name, kind (`object`/`class`), API levels, source (`starlight` or a plugin ID), summary. With a project, only what it gets. |
| `describe_api` | `name`: an API, or a type from the closure | Full description of it, plus the declarations of the types first reached from it. Further types are listed by name, to describe separately. |
| `list_events` | `project` (optional) | One line per event: ID, `usage`, API level, source, summary. With a project, whether it receives each one. |
| `describe_event` | `id` | Full description: how a script receives it, the declarations of the argument types and of the types they reach first, related guides. An event received through a listener (API2's `bot.on`) is declared as the listener's type, `type MessageListener = (msg: Api2Message) => void`, not as a function StarLight calls. |
| `read_guide` | `id` (optional), may end in `#anchor` | Without an ID, the index of guides (ID and summary). With one, the guide or its section. |

- With a project, `list_events` marks each event `[receives]`, `[doesn't receive]`, or `[not in its API levels]` for one that is allowed but owned by levels the project didn't choose, so it never arrives. `set_project_events` reports those separately too.
- The answers are built by `DocsAnswers`, apart from looking up the project, so tests check them without the app.
- `describe_api` takes any type an API or event leads to: a documented one; one without docs (another module's, such as `Project`, or a plugin's), shown from reflection without descriptions; or Java's, Kotlin's and Android's, for which it points to their own references. A description lists which types it declared in short, which it only names, and which have no docs.
- `list_events` ends with a note on `onProjectButtonClicked(buttonId)`, which StarLight calls by name but isn't an event.
- **Removed:** `read_docs`, and the current `list_apis` output built from `Api.objects`.
- **Kept for now:** `Api.objects` itself, for the editor's autocomplete (§10, phase 3).
- **System prompt:**
  - API facts are removed: the event object's members, MediaSharer and module syntax.
  - It points to the tools.
  - It names the guides that apply everywhere: `engines/neonjs`, `projects` and `events`.
- **Developer mode:** "StarLight AI 문서" lists the guides, APIs, events and types, each grouped by the app or plugin they come from, with the types the agent sees without docs marked. It shows each one as the tools return it, built by `DocsIndex`, and `guide:` references in it open the guide.

## 8. Initial guides

| ID | Covers |
|---|---|
| `engines/neonjs` | ES modules, exported event functions, imports and JSON imports, async/await and the event loop, timers, `fetch`/`WebSocket`, `console`, `Java.type` and host objects, what is missing (Node, DOM, `Intl`) |
| `engines/rhino` | The legacy engine: syntax limits, `require`, Java packages, Java strings in comparisons, how it differs from NeonJS |
| `projects` | Files and folders, `project.json`, API levels, allowed-event entries (IDs, wildcards, `!` exclusions), compile and enable, errors turning a project off |
| `events` | How events reach scripts: functions versus API2 listeners, workers and ordering, notification events and their app setting, plugin events |
| `testing` | The debug room: how its messages differ from real ones, `send_debug_message`, replies that come later |
| `play-build` | What the Google Play build lacks: plugins, self-updates, the shared folder, and the Java access `ScriptHostPolicy` blocks |
| `app` | Where things are in the app (tabs, settings, permissions, notification rules), for answering how-to questions |

## 9. Plugins

- **Source:** the plugin that registered an API or event is found from its class's loader. Each plugin has its own
  `PluginHostClassLoader`, so PluginCore needs no change for this, and the catalog already does it in phase 1.
- **Plugin package:**
  - `starlight/agent/<pluginId>.api.json` at the APK root, from KSP;
  - `assets/agent/guides/**.md`.
  - Both are read from the package file of each loaded plugin (`InstalledPlugin.file`), as `assets/starlight.json` is, by `PluginDocs`. They are read again when the loaded plugins or their files change.
- **Packages:** slplugin leaves `starlight.docs.packages` out, so a plugin's file lists every public class of the plugin module, with `"packages": []`. For a file that names no packages, the catalog treats the classes it lists as documented and follows them in a closure.
- **Precedence:** the app's doc files and guides come first. A plugin's file can't replace the docs of a class the app's files have, and a plugin's guides never replace the app's.
- **Guide IDs** are namespaced by the plugin ID (`discord:setup`). `read_guide` lists the app's guides, then the plugins'.
- **References:** in a plugin's KDoc and guides, a `guide:` reference without a module is the plugin's own guide (`guide:setup` in plugin `discord` is `guide:discord:setup`). `guide:starlight:<id>` is the app's. They are rewritten with the module when the package is read, also in the text, so the agent sees references it can open.
- **slplugin** (0.5.0 and later):
  - bundles a copy of `:docs-processor`, so there's no repository to resolve it from. The copy is built into its own jar inside slplugin's, because Gradle instruments the plugin's own jar, and is added to the plugin's `ksp` configuration with `kotlinx-serialization-json`. This module is the original: change it here first, then copy it to slplugin (`plugin/src/docsProcessor`, and its tests to `plugin/src/test`, where they use JUnit 5's imports).
  - runs it when the plugin's build applies KSP, with `starlight.docs.module` set to the plugin ID: from `starlight { manifest { } }`, or from the hand-written `src/main/assets/starlight.json`. KSP isn't applied by slplugin, because each KSP release supports a range of AGP versions (2.3.13 refuses AGP before 8.12), while slplugin supports AGP 8.0 and later. So a plugin adds one line to its `plugins { }` block.
  - runs KSP tasks one at a time, as the app's root build does (§4);
  - compiles with parameter names: `-parameters` for Java, `-java-parameters` for Kotlin;
  - checks the package: the doc file is the one StarLight reads (`<pluginId>.api.json`, format 1, its module is the plugin ID), and each guide has front matter with a `title` and a `summary`. It reports how many classes and guides the package documents.
- **Older plugins:** without docs they still appear with their reflected structure.
- **Coverage:** plugins aren't held to it. A description lists the plugin's types that have no docs (`// Without docs:`).

## 10. Phases

| Phase | Content | Ships in |
|---|---|---|
| 0 | KSP spike: KSP2 with Kotlin 2.4.20 and AGP 8.13 on `:app` and `:PluginCore`; both flavors, configuration cache and the release workflow build. If KSP can't be used, prose falls back to run-time annotations, and only §3 and §4 change. | — |
| 1 | `:docs-processor`, catalog, renderer, tools, guides, KDoc for the app's 22 APIs, their types and its 10 events, `ProjectEvent.scriptArguments`, `CoreApis`, tests, system prompt, removal of `assets/agent/docs`, `DocsLibrary` and `read_docs` | StarLight 1.1.0 and PluginCore 1.1.0 |
| 2 | Reading plugin docs and guides (StarLight 1.1.0), slplugin release (0.5.0); then docs for the Discord, webserver, MCP and ktor plugins, a page for plugin authors on the site | PluginCore 1.1.0 and later |
| 3 | The editor's autocomplete from the catalog; exporting the API reference for the site | later |

## 11. Verification

- **Coverage test:** every core API and its closure, and every core event, has a summary. Every public member has one or is `@suppress`ed. Every one of an event's `scriptArguments` has `@param`.
- **References test:** every `guide:` reference resolves to a guide and an existing anchor.
- **Setting names:** every bold name with Korean in it, in the guides and KDoc, appears in the app's sources, as the app shows settings.
- **Against NeonJS (`CatalogNeonTest`):** for every class in the closure, NeonJS's own `HostClassInfo` has each method shown, with every arity its optional parameters claim, each property's getter, the setter of each writable one, and each constructor.
- **Engine behavior (`EngineValuesTest`):** what the docs say about Java values and syntax, evaluated in both engines: `JavaList`/`JavaMap` access, `Array.from`, getters, `long` (BigInt in NeonJS, a lossy number in Rhino), map members against entries, Java exceptions, the syntax Rhino parses, and what NeonJS lacks.
- **Tool answers (`DocsAnswersTest`):** each tool's output, lookups and errors, and no answer over 24,000 characters.
- **Script-view tests:**
  - fixture classes for getters, `is`-getters, `@get:JvmName`, setters, fields, `@JvmOverloads` chains, other overloads, static members and `@HostName`;
  - checked against NeonJS by evaluating the same names in a NeonJS context, in a JVM unit test with `neonjs-core`, or in an instrumented test if that needs Android.
- **Rendering tests:** assertions on the declarations that matter (`BotManager`, `MediaSharer`, the message events, API2 listeners, classes, maps, function types), not golden files, so that docs can be reworded without updating tests. Every rendering is written to `build/agent-docs-preview/` for review, and the coverage test writes what is undocumented to `build/agent-docs-undocumented.txt`.
- **On a device:**
  - the catalog loads in the release build, with plugins' APIs attributed to them;
  - an agent session that writes and tests an API2 bot, using only the new tools.

## 12. Removed

- **From the app:**
  - `app/src/main/assets/agent/docs/` (the copy of the site's docs);
  - `DocsLibrary`;
  - the `read_docs` tool, its label and the system prompt's references to it.
- **On the site:** starlight-landing's docs are no longer a copy of anything in the app, so they no longer need syncing.
