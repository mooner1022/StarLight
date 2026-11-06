---
title: Google Play build
summary: What the Google Play build of StarLight lacks next to the GitHub build: plugins, self-updates, the shared StarLight folder, and the Java APIs scripts can't reach.
---

StarLight comes as two builds of the same app (one per device): the **Google Play build** and the **GitHub build**
(an APK from GitHub or the download page). Scripts and projects are the same in both, apart from what this guide lists.

## What it lacks

| | Google Play build | GitHub build |
|---|---|---|
| Plugins and the plugin store | Not available: no plugin APIs, events or languages | Available |
| Updates | From Google Play | In the app (**정보 → 업데이트 확인**) |
| Data | An app-only folder, deleted with the app | `StarLight` on shared storage |
| Required permissions | Notification access, display over other apps | The same, plus all-files access |
| External libraries (`LibraryManager`) | Not available | From `StarLight/libs`, with a developer setting |

When a user asks for something only plugins provide (a Discord bot, a web server), say it needs the GitHub build.

## Data folder

The Google Play build keeps the same layout as `StarLight/` in
`/storage/emulated/0/Android/data/dev.mooner.starlight/files`. File managers may not show it, and uninstalling the
app deletes it. To back up or move projects, export them as zip files from the project's info screen.

Tools take paths relative to the project, and so do `DataBase` (in the project's `data` folder) and `MediaSharer`.
`FileStream` takes paths as given, so in this build only paths inside the folder above work.

## Java access limits

Scripts in the Google Play build, NeonJS and Rhino alike, can't reach Java APIs that load or run code from outside the
app. Using one throws an error naming the denied class or member.

- **Packages:** `dalvik.system`, `java.lang.reflect`, `java.lang.invoke`, `kotlin.reflect`, `kotlin.jvm.internal`,
  `sun`, `com.sun`, `jdk.internal`, `com.android.dx`, `libcore`, and the engines' own packages
  (`org.mozilla.javascript`, `dev.mooner.neonjs`).
- **Classes:** `ClassLoader`, `Runtime`, `Process`, `ProcessBuilder`, `ProcessHandle`, `SecurityManager`,
  `android.system.Os`, `PackageInstaller`.
- **Members:**
  - `Class`'s reflection and loading: `forName`, `getClassLoader`, `newInstance`, `getMethod(s)`,
    `getDeclaredMethod(s)`, `getField(s)`, `getDeclaredField(s)`, `getConstructor(s)` and the like. Its other members,
    such as `getName()`, still work.
  - `System.load`, `System.loadLibrary`.
  - `WebView.addJavascriptInterface`.

Every other Java and Android API works as in the GitHub build. Code that needs a denied API has to be written without
it, or the user needs the GitHub build.
