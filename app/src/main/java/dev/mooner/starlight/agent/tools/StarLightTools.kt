package dev.mooner.starlight.agent.tools

import dev.mooner.starlight.agent.model.Card
import dev.mooner.starlight.agent.model.CardKind
import dev.mooner.starlight.logging.LogCollector
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.event.getInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * The agent's tools for StarLight projects. They follow the built-in tools of the StarLight MCP server plugin
 * (github.com/mooner1022/starlight-mcp), so that the same instructions work in both, and run on the app's own
 * project manager instead of a plugin's.
 */
class StarLightTools(private val docs: DocsLibrary) {

    val all: List<AgentTool> by lazy { (readTools + handOffTools + writeTools).sortedBy(AgentTool::name) }

    fun find(name: String): AgentTool? =
        all.firstOrNull { it.name == name }

    private val readTools: List<AgentTool> get() = listOf(
        tool(
            "list_projects",
            "Lists the StarLight projects (chatbot scripts) on this device with their language, main script and state.",
            ToolRisk.READ,
            describe = { "List projects" },
        ) { _ ->
            val projects = Session.projectManager.getProjects().sortedBy { it.info.name.lowercase() }
            val json = buildJsonArray {
                for (project in projects)
                    addJsonObject {
                        put("name", project.info.name)
                        put("id", project.info.id.toString())
                        put("language", project.info.languageId)
                        put("mainScript", project.info.mainScript)
                        put("enabled", project.info.isEnabled)
                        put("compiled", project.isCompiled)
                    }
            }
            ToolOutput(json.toString(), summary = "${projects.size} projects")
        },
        tool(
            "list_languages",
            "Lists the script languages installed in StarLight, with the id create_project takes.",
            ToolRisk.READ,
            describe = { "List languages" },
        ) { _ ->
            val json = buildJsonArray {
                for (language in Session.languageManager.getLanguages())
                    addJsonObject {
                        put("id", language.id)
                        put("name", language.name)
                        put("fileExtension", language.fileExtension)
                    }
            }
            ToolOutput(json.toString())
        },
        tool(
            "list_files",
            "Lists the files of a project, recursively. Paths are relative to the project directory.",
            ToolRisk.READ,
            params = {
                project()
                string("path", "Directory to list, relative to the project directory. Defaults to the project directory.", optional = true)
            },
            describe = { "List files of ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val files = ProjectFiles(project.directory)
            val (entries, truncated) = files.list(args.stringArg("path") ?: ".", MAX_LISTED_FILES)
            val text = entries.joinToString("\n") { file ->
                if (file.isDirectory) "${files.relative(file)}/" else "${files.relative(file)} (${file.length()} bytes)"
            }.ifEmpty { "(empty)" }
            ToolOutput(if (truncated) "$text\n... (more than $MAX_LISTED_FILES files)" else text)
        },
        tool(
            "read_file",
            "Reads a text file of a project, with line numbers. Without a path, reads the main script. " +
                "The result ends with the file's hash, which write_file and edit_file can check.",
            ToolRisk.READ,
            params = {
                project()
                string("path", "File path relative to the project directory. Defaults to the main script.", optional = true)
                integer("offset", "First line to read, from 1 (default 1).", optional = true)
                integer("limit", "Number of lines to read (default all, at most $MAX_READ_LINES).", optional = true)
            },
            describe = { "Read ${it.stringArg("path") ?: "the main script"} of ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val path = args.stringArg("path") ?: project.info.mainScript
            val (file, text) = ProjectFiles(project.directory).read(path)
            val lines = text.lines()
            val offset = (args.intArg("offset") ?: 1).coerceAtLeast(1)
            val limit = (args.intArg("limit") ?: MAX_READ_LINES).coerceIn(1, MAX_READ_LINES)
            val shown = lines.drop(offset - 1).take(limit)
            val numbered = shown.mapIndexed { i, line -> "${(offset + i).toString().padStart(5)}  $line" }.joinToString("\n")
            val more = if (offset - 1 + shown.size < lines.size) "\n... (${lines.size} lines in all; read on with offset)" else ""
            ToolOutput(
                "$numbered$more\n\n[hash ${ProjectFiles.sha256(file.readBytes())}]",
                summary = "$path · ${lines.size} lines",
            )
        },
        tool(
            "search_files",
            "Searches the text files of a project and returns matching lines as path:line: text.",
            ToolRisk.READ,
            params = {
                project()
                string("query", "Text to find, or a regular expression if regex is true.")
                boolean("regex", "Whether query is a regular expression (default false).", optional = true)
                boolean("ignoreCase", "Whether to ignore case (default true).", optional = true)
            },
            describe = { "Search ${it.stringArg("project")} for \"${it.stringArg("query")}\"" },
        ) { args ->
            val hits = ProjectFiles(findProject(args).directory).search(
                query = args.requireString("query"),
                isRegex = args.booleanArg("regex") ?: false,
                ignoreCase = args.booleanArg("ignoreCase") ?: true,
                limit = MAX_SEARCH_HITS,
            )
            ToolOutput(hits.joinToString("\n").ifEmpty { "(no matches)" }, summary = "${hits.size} matches")
        },
        tool(
            "list_events",
            "Lists the events projects can receive, with the function a script defines for each (such as onMessage) and " +
                "its arguments. With a project, also tells which events it receives.",
            ToolRisk.READ,
            params = { string("project", "Name (or id) of a project, to see which events it receives.", optional = true) },
            describe = { "List events" },
        ) { args ->
            val allowed = args.stringArg("project")?.let { allowedEvents(findProject(args)) }
            val json = buildJsonArray {
                for (eventClass in ProjectEventManager.find(ALL_EVENTS)) {
                    val id = ProjectEventManager.validateAndGetEventID(eventClass) ?: continue
                    val event = eventClass.getInstance()
                    addJsonObject {
                        put("id", id)
                        put("name", event.name)
                        put("function", event.functionName)
                        putJsonArray("args") { event.argTypes.forEach { add("${it.name}: ${it.type.java.simpleName}") } }
                        allowed?.let { put("allowed", id in it) }
                    }
                }
            }
            ToolOutput(json.toString())
        },
        tool(
            "list_apis",
            "Lists the global APIs StarLight gives scripts (from the app and its plugins), with their functions and values.",
            ToolRisk.READ,
            describe = { "List script APIs" },
        ) { _ ->
            val json = buildJsonArray {
                for (api in Session.apiManager.getApis())
                    addJsonObject {
                        put("name", api.name)
                        putJsonArray("functions") {
                            api.objects.filterIsInstance<ApiObject.Function>().forEach { function ->
                                add("${function.name}(${function.args.joinToString { it.simpleName }}): ${function.returns.simpleName}")
                            }
                        }
                        putJsonArray("values") {
                            api.objects.filterIsInstance<ApiObject.Value>().forEach { add("${it.name}: ${it.returns.simpleName}") }
                        }
                    }
            }
            ToolOutput(json.toString())
        },
        tool(
            "get_project_logs",
            "Returns the latest logs of a project: compile results, errors and what the script logged.",
            ToolRisk.READ,
            params = {
                project()
                integer("limit", "Number of latest entries (default $DEFAULT_LOG_LIMIT).", optional = true)
            },
            describe = { "Read logs of ${it.stringArg("project")}" },
        ) { args ->
            val logger = findProject(args).logger
            val logs = synchronized(logger.logs) { logger.logs.toList() }
            ToolOutput(formatLogs(logs, args.intArg("limit") ?: DEFAULT_LOG_LIMIT))
        },
        tool(
            "get_app_logs",
            "Returns the latest logs of StarLight itself and its plugins.",
            ToolRisk.READ,
            params = { integer("limit", "Number of latest entries (default $DEFAULT_LOG_LIMIT).", optional = true) },
            describe = { "Read app logs" },
        ) { args ->
            ToolOutput(formatLogs(LogCollector.logs, args.intArg("limit") ?: DEFAULT_LOG_LIMIT))
        },
        tool(
            "read_docs",
            "Reads StarLight's documentation (in Korean): events and their objects, the script APIs, NeonJS modules, " +
                "fetch, async code and Java access. Without a path, lists the pages. Check it before relying on an API " +
                "you are unsure of.",
            ToolRisk.READ,
            params = { string("path", "Page path from the list, such as app/reference/events.", optional = true) },
            describe = { "Read the docs" },
        ) { args ->
            val path = args.stringArg("path")
            if (path.isNullOrBlank())
                ToolOutput(docs.pages.joinToString("\n") { "${it.path}: ${it.title} - ${it.description}" }, summary = "Docs index")
            else
                ToolOutput(docs.page(path), summary = path)
        },
    )

    /** Tools that put something in front of the user rather than change a project. */
    private val handOffTools: List<AgentTool> get() = listOf(
        tool(
            "send_file",
            "Gives the user a file as a card they can open, share or save to their device: either a file of a " +
                "project (project and path), or a new file you write for them (name and content), such as an " +
                "export, a report or code they asked to download. Prefer this to pasting long content into a reply.",
            ToolRisk.READ,
            params = {
                string("project", "Name (or id) of the project the file is in.", optional = true)
                string("path", "Path of the project file, relative to the project directory.", optional = true)
                string("name", "File name for a new file, such as report.csv.", optional = true)
                string("content", "Text content of the new file.", optional = true)
                string("caption", "One line telling the user what the file is.", optional = true)
            },
            describe = { "Send ${it.stringArg("path") ?: it.stringArg("name")} to the user" },
        ) { args ->
            val caption = args.stringArg("caption")
            val content = args.stringArg("content")
            val card = if (content != null) {
                val name = args.stringArg("name")?.trim()?.takeIf { FILE_NAME_REGEX.matches(it) && it != "." && it != ".." }
                    ?: throw ToolException("A new file needs a plain file name, such as report.csv")
                // Each card keeps its own copy, so sending a file again with the same name doesn't change an earlier one.
                val file = uniqueFile(outbox(), name)
                file.writeText(content)
                Card(
                    kind = CardKind.FILE,
                    name = name, path = file.name, size = file.length(), mimeType = mimeTypeOf(name), caption = caption,
                )
            } else {
                val project = findProject(args)
                val files = ProjectFiles(project.directory)
                val file = files.resolve(args.requireString("path"))
                if (!file.isFile)
                    throw ToolException("No such file: ${args.stringArg("path")}")
                Card(
                    kind = CardKind.FILE,
                    name = file.name, project = project.info.name, path = files.relative(file),
                    size = file.length(), mimeType = mimeTypeOf(file.name), caption = caption,
                )
            }
            ToolOutput("The user now sees ${card.name} as a file card.", summary = card.name, card = card)
        },
        tool(
            "show_project",
            "Shows the user a card for a project with buttons that open its editor, debug room and settings. Use it " +
                "when you finish creating or changing a project, so the user can go straight to it.",
            ToolRisk.READ,
            params = {
                project()
                string("caption", "One line telling the user what to look at.", optional = true)
            },
            describe = { "Show ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val card = Card(
                kind = CardKind.PROJECT,
                name = project.info.name, project = project.info.name, caption = args.stringArg("caption"),
            )
            ToolOutput("The user now sees a card for ${project.info.name}.", summary = project.info.name, card = card)
        },
    )

    private val writeTools: List<AgentTool> get() = listOf(
        tool(
            "write_file",
            "Creates or overwrites a text file of a project with the whole content. Prefer edit_file for small changes. " +
                "Compile the project afterwards to apply script changes.",
            ToolRisk.WRITE,
            params = {
                project()
                string("path", "File path relative to the project directory.")
                string("content", "The whole new content of the file.")
                expectedHash()
            },
            describe = { "Write ${it.stringArg("path")} in ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val path = args.requireString("path")
            val content = args.requireString("content")
            val file = ProjectFiles(project.directory).write(path, content, args.stringArg("expectedHash"))
            ToolOutput("Wrote ${content.lines().size} lines to $path [hash ${ProjectFiles.sha256(file.readBytes())}]", summary = path)
        },
        tool(
            "edit_file",
            "Replaces a piece of text in a project file. The text to replace must occur exactly once; include enough " +
                "surrounding lines to make it unique. Cheaper than rewriting the whole file.",
            ToolRisk.WRITE,
            params = {
                project()
                string("path", "File path relative to the project directory.")
                string("old", "The exact text to replace.")
                string("new", "The text to put in its place.")
                expectedHash()
            },
            describe = { "Edit ${it.stringArg("path")} in ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val path = args.requireString("path")
            val file = ProjectFiles(project.directory).edit(path, args.requireString("old"), args.requireString("new"), args.stringArg("expectedHash"))
            ToolOutput("Edited $path [hash ${ProjectFiles.sha256(file.readBytes())}]", summary = path)
        },
        tool(
            "rename_file",
            "Renames or moves a file within a project. The main script and project.json can't be renamed this way.",
            ToolRisk.WRITE,
            params = {
                project()
                string("from", "Current path, relative to the project directory.")
                string("to", "New path, relative to the project directory.")
            },
            describe = { "Rename ${it.stringArg("from")} to ${it.stringArg("to")} in ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val files = ProjectFiles(project.directory)
            val from = args.requireString("from")
            val to = args.requireString("to")
            val source = files.resolve(from)
            val target = files.resolve(to)
            if (!source.isFile)
                throw ToolException("No such file: $from")
            if (isProjectFile(project, files.relative(source)))
                throw ToolException("$from belongs to the project itself; renaming it would break the project")
            if (target.exists())
                throw ToolException("$to already exists")
            target.parentFile?.mkdirs()
            if (!source.renameTo(target))
                throw ToolException("Could not rename $from to $to")
            ToolOutput("Renamed $from to $to", summary = "$from → $to")
        },
        tool(
            "delete_file",
            "Deletes a file of a project. Directories and the main script are not deleted.",
            ToolRisk.DESTRUCTIVE,
            params = {
                project()
                string("path", "File path relative to the project directory.")
            },
            describe = { "Delete ${it.stringArg("path")} from ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val files = ProjectFiles(project.directory)
            val path = args.requireString("path")
            val file = files.resolve(path)
            if (!file.isFile)
                throw ToolException("No such file: $path")
            if (isProjectFile(project, files.relative(file)))
                throw ToolException("$path belongs to the project itself and can't be deleted")
            if (!file.delete())
                throw ToolException("Could not delete $path")
            ToolOutput("Deleted $path", summary = path)
        },
        tool(
            "compile_project",
            "Compiles a project, which applies changes to its scripts and runs their top-level code, and returns the " +
                "error with its location if it fails, plus the logs written meanwhile.",
            ToolRisk.WRITE,
            params = { project() },
            describe = { "Compile ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            compile(project)
        },
        tool(
            "set_project_events",
            "Chooses which events a project receives. A project gets chat messages only if it receives " +
                "$MESSAGE_EVENT; see list_events for the ids.",
            ToolRisk.WRITE,
            params = {
                project()
                stringArray("allow", "Event ids to start receiving.", optional = true)
                stringArray("disallow", "Event ids to stop receiving.", optional = true)
            },
            describe = { "Change the events of ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val allow = args.stringSet("allow")
            val disallow = args.stringSet("disallow")
            val known = ProjectEventManager.find(ALL_EVENTS).mapNotNullTo(hashSetOf()) { ProjectEventManager.validateAndGetEventID(it) }
            val unknown = (allow + disallow) - known
            if (unknown.isNotEmpty())
                throw ToolException("Unknown events: ${unknown.joinToString()}. See list_events.")
            // Allowed ids may be wildcards such as starlight.*; they are stored expanded so that one can be removed.
            val events = allowedEvents(project) + allow - disallow
            Session.projectManager.updateProjectInfo(project.info.name, true) {
                allowedEventIds.clear()
                allowedEventIds.addAll(events)
            }
            ToolOutput("Receives: ${allowedEvents(project).sorted().joinToString().ifEmpty { "nothing" }}", summary = project.info.name)
        },
        tool(
            "create_project",
            "Creates a project with the language's starting code for the chosen events. It starts turned off and not " +
                "compiled; write its script, compile it, test it, then turn it on if the user wants it running.",
            ToolRisk.WRITE,
            params = {
                string("name", "Project name: letters, digits, Hangul, '-' and '_'.")
                string("language", "Language id from list_languages; js_neon (NeonJS) unless the user asks otherwise.")
                stringArray("events", "Event ids it receives (default: $MESSAGE_EVENT).", optional = true)
            },
            describe = { "Create the project ${it.stringArg("name")}" },
        ) { args ->
            val name = args.requireString("name").trim()
            if (!PROJECT_NAME_REGEX.matches(name))
                throw ToolException("Invalid project name '$name': use letters, digits, Hangul, '-' and '_'")
            val manager = Session.projectManager
            if (manager.getProjectByName(name, true) != null)
                throw ToolException("A project named '$name' already exists")
            val languageId = args.requireString("language")
            val language = Session.languageManager.getLanguage(languageId, false)
                ?: throw ToolException("No language '$languageId'. See list_languages.")
            val events = args.stringSet("events").ifEmpty { setOf(MESSAGE_EVENT) }.associateWith { id ->
                (ProjectEventManager.findFirst(id) ?: throw ToolException("No event '$id'. See list_events.")).getInstance()
            }
            manager.newProject(events = events) {
                this.name = name
                mainScript = "$name.${language.fileExtension}"
                this.languageId = language.id
            }
            val project = manager.getProjectByName(name) ?: throw ToolException("Project $name was not created")
            ToolOutput("Created $name with main script ${project.info.mainScript}", summary = name)
        },
        tool(
            "set_project_enabled",
            "Turns a project on or off. Only a project that is on answers real chats; the debug room " +
                "(send_debug_message) works while it is off.",
            ToolRisk.DESTRUCTIVE,
            params = {
                project()
                boolean("enabled", "true to turn the project on, false to turn it off.")
            },
            describe = { "Turn ${it.stringArg("project")} ${if (it.booleanArg("enabled") == true) "on" else "off"}" },
        ) { args ->
            val project = findProject(args)
            val enabled = args.booleanArg("enabled") ?: throw ToolException("Missing argument: enabled (a boolean)")
            if (!project.setEnabled(enabled)) {
                if (!project.isCompiled && !enabled)
                    return@tool ToolOutput("${project.info.name} is off", summary = project.info.name)
                throw ToolException(
                    if (!project.isCompiled) "${project.info.name} isn't compiled: compile it before turning it on"
                    else "StarLight didn't turn ${project.info.name} ${if (enabled) "on" else "off"}"
                )
            }
            ToolOutput("${project.info.name} is now ${if (project.info.isEnabled) "on" else "off"}", summary = project.info.name)
        },
        tool(
            "delete_project",
            "Deletes a project and all of its files. This can't be undone; do it only when the user asked for it.",
            ToolRisk.DESTRUCTIVE,
            params = { project() },
            describe = { "Delete the project ${it.stringArg("project")} and its files" },
        ) { args ->
            val project = findProject(args)
            Session.projectManager.removeProject(project, removeFiles = true)
            ToolOutput("Deleted ${project.info.name}", summary = project.info.name)
        },
        tool(
            "send_debug_message",
            "Sends a chat message to a project from its debug room, as if a user sent it, and returns what the bot " +
                "replied (with how many seconds each reply took), any error, and the logs written meanwhile. The " +
                "project must be compiled and receive $MESSAGE_EVENT; it may be off. How long it waits: by default " +
                "until the bot has been quiet for a second after replying, or ${DebugMessenger.DEFAULT_WAIT_SECONDS} seconds without " +
                "a reply. For a bot that answers later or in several messages (timers, network requests, await), set " +
                "expectReplies to return as soon as that many replies came, or waitSeconds to collect for that long. " +
                "Replies that come after it returns are kept for read_debug_replies; ones left from earlier messages " +
                "come back here as earlierReplies.",
            ToolRisk.WRITE,
            params = {
                project()
                string("message", "The message text.")
                string("sender", "Name of the sender (default: the debug room's).", optional = true)
                string("room", "Name of the chat room (default: the debug room's).", optional = true)
                boolean("isGroupChat", "Whether the room is a group chat.", optional = true)
                waitParams()
            },
            describe = { "Send \"${it.stringArg("message")}\" to ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val startMillis = System.currentTimeMillis()
            val wait = waitOf(args)
            val sent = DebugMessenger.send(project, DebugMessenger.Options(
                text = args.requireString("message"),
                sender = args.stringArg("sender"),
                room = args.stringArg("room"),
                isGroupChat = args.booleanArg("isGroupChat"),
            ), wait)
            val logs = synchronized(project.logger.logs) { project.logger.logs.filter { it.millis >= startMillis } }
            debugReplies(sent.events, wait, logs, earlier = sent.earlier, withTrigger = false)
        },
        tool(
            "read_debug_replies",
            "Returns what a project's bot sent to the debug room since you last looked, such as replies that came " +
                "after send_debug_message returned (timers, network requests, await), each with the message it " +
                "answered and how many seconds after it, plus the logs written since. Waits like send_debug_message: " +
                "by default until the bot has been quiet for a second after replying, or ${DebugMessenger.DEFAULT_WAIT_SECONDS} " +
                "seconds without a reply; expectReplies and waitSeconds work the same way, and waitSeconds 0 returns " +
                "what is there at once.",
            ToolRisk.READ,
            params = {
                project()
                waitParams()
            },
            describe = { "Check the debug room replies of ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val wait = waitOf(args)
            val (events, since) = DebugMessenger.read(project, wait)
            val logs = synchronized(project.logger.logs) { project.logger.logs.filter { it.millis >= since } }
            debugReplies(events, wait, logs, earlier = emptyList(), withTrigger = true)
        },
        tool(
            "save_attachment",
            "Copies a file the user attached to this conversation into a project, such as an image or a data file.",
            ToolRisk.WRITE,
            params = {
                project()
                string("attachment", "The attachment id, from the attachment tag in the user's message.")
                string("path", "Where to save it, relative to the project directory.")
            },
            describe = { "Save the attachment to ${it.stringArg("path")} in ${it.stringArg("project")}" },
        ) { args ->
            val project = findProject(args)
            val source = attachmentFile(args.requireString("attachment"))
                ?: throw ToolException("No attachment ${args.stringArg("attachment")} in this conversation")
            val path = args.requireString("path")
            val target = ProjectFiles(project.directory).resolve(path)
            if (target.isDirectory)
                throw ToolException("Is a directory: $path")
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            ToolOutput("Saved ${target.length()} bytes to $path", summary = path)
        },
    )

    private suspend fun compile(project: Project): ToolOutput = withContext(Dispatchers.IO) {
        val startMillis = System.currentTimeMillis()
        val error = try {
            project.compile(true)
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            e
        }
        val logs = synchronized(project.logger.logs) { project.logger.logs.filter { it.millis >= startMillis } }
        val logText = if (logs.isEmpty()) "" else "\n\nLogs:\n" + formatLogs(logs, logs.size)
        if (error == null)
            return@withContext ToolOutput("Compiled ${project.info.name}$logText", summary = project.info.name)
        val location = ScriptErrors.locate(error)?.let { at ->
            val source = at.source?.let { sourcePath(project, it) } ?: project.info.mainScript
            " (at $source:${at.line}${at.column?.let { ":$it" }.orEmpty()})"
        }.orEmpty()
        val message = ScriptErrors.message(error)
        ToolOutput("Failed to compile ${project.info.name}: $message$location$logText", isError = true, summary = message.take(120))
    }

    /**
     * Whether [relative] is project.json or the main script, which hold the project together. Compared without case,
     * as shared storage doesn't tell `Main.js` from `main.js`.
     */
    private fun isProjectFile(project: Project, relative: String): Boolean =
        relative.equals(project.info.mainScript.removePrefix("./"), ignoreCase = true) ||
            relative.equals(PROJECT_INFO_FILE, ignoreCase = true)

    private fun sourcePath(project: Project, source: String): String {
        val file = File(source)
        if (!file.isAbsolute)
            return source.removePrefix("./")
        return runCatching { ProjectFiles(project.directory).relative(file.canonicalFile) }.getOrDefault(source)
    }

    private fun findProject(args: JsonObject): Project {
        val key = args.requireString("project")
        val manager = Session.projectManager
        val projects = manager.getProjects()
        return projects.firstOrNull { it.info.name == key }
            ?: projects.firstOrNull { it.info.id.toString() == key }
            ?: projects.firstOrNull { it.info.name.equals(key, ignoreCase = true) }
            ?: throw ToolException("No project named '$key'. Use list_projects to see the projects.")
    }

    private fun allowedEvents(project: Project): Set<String> =
        ProjectEventManager.filterAllowedEvents(project.info.allowedEventIds)

    private fun ParamsBuilder.waitParams() {
        integer("waitSeconds", "Collect replies for this many seconds, at most ${DebugMessenger.MAX_WAIT_SECONDS}. With " +
            "expectReplies, the longest to wait instead (default ${DebugMessenger.EXPECT_WAIT_SECONDS}).", optional = true)
        integer("expectReplies", "Return as soon as the bot has sent this many replies.", optional = true)
    }

    private fun waitOf(args: JsonObject): DebugMessenger.Wait =
        DebugMessenger.Wait.of(args.intArg("waitSeconds"), args.intArg("expectReplies"))

    /** The result of a look at the debug room: replies, the first error, a note when something is missing, and logs. */
    private fun debugReplies(
        events: List<DebugMessenger.Event>,
        wait: DebugMessenger.Wait,
        logs: List<LogData>,
        earlier: List<DebugMessenger.Event>,
        withTrigger: Boolean,
    ): ToolOutput {
        val replies = events.filter { it.text != null }
        val error = events.firstNotNullOfOrNull { it.error }
        fun JsonArrayBuilder.reply(event: DebugMessenger.Event, trigger: Boolean) = addJsonObject {
            if (trigger)
                put("for", event.trigger)
            put("text", event.text)
            put("seconds", (event.seconds * 10).roundToLong() / 10.0)
        }
        val seconds = wait.maxMillis / 1000
        val json = buildJsonObject {
            putJsonArray("replies") { replies.forEach { reply(it, withTrigger) } }
            error?.let { put("error", ScriptErrors.message(it)) }
            val expected = wait.expect
            when {
                error != null -> Unit
                replies.isEmpty() ->
                    put("note", "No reply within ${seconds}s. A reply that comes later is kept: read_debug_replies gets it.")
                expected != null && replies.size < expected ->
                    put("note", "Got ${replies.size} of the $expected replies expected within ${seconds}s. Later ones " +
                        "are kept: read_debug_replies gets them.")
            }
            if (earlier.any { it.text != null })
                putJsonArray("earlierReplies") { earlier.filter { it.text != null }.forEach { reply(it, trigger = true) } }
            if (logs.isNotEmpty())
                put("logs", formatLogs(logs, logs.size))
        }
        return ToolOutput(
            json.toString(),
            isError = error != null,
            // The first reply, and how many more came.
            summary = replies.firstOrNull()?.text?.take(80)?.let { if (replies.size > 1) "$it (+${replies.size - 1})" else it }
                ?: error?.let(ScriptErrors::message) ?: "No reply",
        )
    }

    private fun formatLogs(logs: List<LogData>, limit: Int): String =
        logs.takeLast(limit.coerceIn(1, MAX_LOG_LIMIT))
            .joinToString("\n") { "${TIME_FORMAT.get()!!.format(Date(it.millis))} ${it.type} [${it.tag.orEmpty()}] ${it.message}" }
            .ifEmpty { "(no logs)" }

    private fun tool(
        name: String,
        description: String,
        risk: ToolRisk,
        params: ParamsBuilder.() -> Unit = {},
        describe: (JsonObject) -> String,
        run: suspend ToolContext.(JsonObject) -> ToolOutput,
    ): AgentTool =
        AgentTool(name, description, risk, ParamsBuilder().apply(params).build(), describe) { args ->
            // File work blocks, and compiling waits for the script engine.
            withContext(Dispatchers.IO) { run(args) }
        }

    private fun ParamsBuilder.expectedHash() =
        string("expectedHash", "Hash from reading the file; if the file changed since, nothing is written. An empty " +
            "string writes only if the file doesn't exist.", optional = true)

    private companion object {
        const val ALL_EVENTS = "*"
        const val MESSAGE_EVENT = "starlight.message.create"
        const val PROJECT_INFO_FILE = "project.json"
        const val MAX_LISTED_FILES = 500
        const val MAX_READ_LINES = 2000
        const val MAX_SEARCH_HITS = 200
        const val DEFAULT_LOG_LIMIT = 50
        const val MAX_LOG_LIMIT = 300

        val FILE_NAME_REGEX = Regex("^[^/\\\\:*?\"<>|]{1,100}$")

        /** [name] in [dir], or "name (2).ext" and so on when that is taken. */
        fun uniqueFile(dir: File, name: String): File {
            val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
            val base = name.substring(0, dot)
            val extension = name.substring(dot)
            val numbered = generateSequence(2) { it + 1 }.map { n -> File(dir, "$base ($n)$extension") }
            return (sequenceOf(File(dir, name)) + numbered).first { !it.exists() }
        }

        fun mimeTypeOf(name: String): String =
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: when (name.substringAfterLast('.', "").lowercase()) {
                    "js", "mjs", "ts", "kt", "py", "md", "log", "csv" -> "text/plain"
                    "json" -> "application/json"
                    else -> "application/octet-stream"
                }

        // The rule of StarLight's new project dialog.
        val PROJECT_NAME_REGEX = Regex("^[-_\\dA-Za-zㄱ-ㅎㅏ-ㅣ가-힣]+$")

        val TIME_FORMAT: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial { SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT) }
    }
}
