# PluginCore compatibility

Plugins are built against one PluginCore and run on hosts with another. Two directions matter:

- **An older plugin on a newer host.** The newer PluginCore knows everything older plugins use, so keeping them working is
  PluginCore's job: its public API only grows within a major version (§2), and a dump of it catches changes (§3).
- **A newer plugin on an older host.** An older host can't know about API added after it, so the plugin carries what it
  needs in its manifest: `api_version`, the lowest PluginCore its code uses. The StarLight Gradle plugin (slplugin) works it
  out from the code and from the version that added each PluginCore member (§4).

## 1. Versions and the manifest

- `Info.PLUGINCORE_VERSION` is the app's `versionName`. A minor version adds API, a patch fixes, a major one may break API
  and raises `Info.MIN_PLUGIN_API_VERSION`.
- **`api_version`**: the lowest PluginCore the plugin needs. A host refuses a plugin whose `api_version` is newer than its
  PluginCore, or older than `MIN_PLUGIN_API_VERSION` (`ApiCompatibility`).
- **`target_api_version`**: the PluginCore the plugin was built against. slplugin writes it. Hosts don't read it yet: the
  manifest parser ignores unknown keys, so adding it is safe on every host. It is there for gating a behavior change that
  could break plugins written before it: apply the new behavior to plugins targeting the version that made the change, and
  keep the old one for the rest.

## 2. Changing PluginCore's API

- Don't add `abstract` members to a class or interface plugins extend or implement: an older plugin fails with
  `AbstractMethodError` when the host calls them. Add `open` members with a default.
- Don't change or remove a public signature within a major version. Add an overload instead.
- When a hook gains a new form, keep calling an override of the old one. `Language.newProjectCode` calls the
  two-argument `formatDefaultCode` when a language overrides only that one.
- Say in the KDoc which version added a member (`@since`), and how plugins see a behavior change in the release notes for
  plugin authors.

## 3. The API dump

- `PluginCore/api/PluginCore.api` is PluginCore's public API, from the Kotlin Gradle plugin's ABI validation, in the
  same format as the binary-compatibility-validator. It covers every non-test variant; Android has no main compilation.
- After a deliberate API change, run `./gradlew :PluginCore:updateKotlinAbi` and commit the dump with the change. CI runs
  `:PluginCore:checkKotlinAbi`, which fails when the code and the dump differ.
- `PluginCore/api/releases/<version>.api` is the dump as each release shipped it. **When releasing, copy
  `api/PluginCore.api` to `api/releases/<versionName>.api`.** The release workflow refuses to build without it. Never edit
  a release dump afterwards.
- The oldest release dump is the baseline: `1.0.1`, made from the `v1.0.1` tag with the same settings. Nothing is recorded
  about what came before it.

## 4. `starlight/plugincore-api.json`

`:PluginCore:generateApiSince` writes it from the dumps, and every variant packages it as a Java resource, so it is in the
`classes.jar` of the AAR plugins build against. slplugin reads it there. The format is a contract between the two
repositories: change it only with a new `format`.

```json
{
  "format": 1,
  "version": "1.1.0",
  "baseline": "1.0.1",
  "classes": {
    "dev/mooner/starlight/plugincore/RequiresPluginCore": "1.1.0"
  },
  "members": {
    "dev/mooner/starlight/plugincore/project/event/ProjectEvent#getScriptArguments()[Ldev/mooner/starlight/plugincore/language/CodeGenerator$Argument;": "1.1.0",
    "dev/mooner/starlight/plugincore/project/ProjectInfoBuilder#setAllowedEventIds(Ljava/util/Set;)V": "1.1.0"
  }
}
```

- `version`: the PluginCore this file comes with, its `versionName`.
- `baseline`: the oldest release dump. Its classes and members aren't listed.
- `classes`: by JVM internal name (`a/b/C$D`), the version that added each class newer than the baseline.
- `members`: methods as `owner#name(parameters)return` and fields as `owner#name:type`, with JVM descriptors, keyed by the
  class that declares them. A member takes the version of the first release dump that has it, or `version` when no release
  has it yet. Kotlin's `internal` members and private ones aren't in the dumps, so they aren't here either.

## 5. `@RequiresPluginCore`

`dev.mooner.starlight.plugincore.RequiresPluginCore(version)` marks plugin code that runs only on hosts with PluginCore
`version` or newer, after checking `Info.PLUGINCORE_VERSION`. slplugin leaves the API used inside it, up to `version`, out
of the `api_version` it requires; lambdas and local classes inside it count as part of it. It has binary retention, so it
stays in class files and is never loaded: plugins using it load on hosts that don't have it.

The check before such code must use API every supported host has, such as `Info.PLUGINCORE_VERSION` and `Version`. A
helper added later would itself be missing on the older hosts it is meant to detect.
