package dev.mooner.starlight.agent.runtime

import android.content.Context
import androidx.annotation.DrawableRes
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.provider.RequestContent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What the chat calls each tool, and its icon. */
object ToolLabels {

    private class Label(val text: Int, @DrawableRes val icon: Int)

    private val LABELS = mapOf(
        "list_projects" to Label(R.string.agent_tool_list_projects, R.drawable.ic_round_projects_24),
        "list_languages" to Label(R.string.agent_tool_list_languages, R.drawable.ic_round_code_24),
        "list_files" to Label(R.string.agent_tool_list_files, R.drawable.ic_agent_folder),
        "read_file" to Label(R.string.agent_tool_read_file, R.drawable.ic_round_article_24),
        "search_files" to Label(R.string.agent_tool_search_files, R.drawable.ic_round_search_24),
        "list_events" to Label(R.string.agent_tool_list_events, R.drawable.ic_round_notifications_24),
        "list_apis" to Label(R.string.agent_tool_list_apis, R.drawable.ic_round_code_24),
        "get_project_logs" to Label(R.string.agent_tool_project_logs, R.drawable.ic_round_article_24),
        "get_app_logs" to Label(R.string.agent_tool_app_logs, R.drawable.ic_round_article_24),
        "read_docs" to Label(R.string.agent_tool_read_docs, R.drawable.ic_round_info_24),
        "write_file" to Label(R.string.agent_tool_write_file, R.drawable.ic_round_edit_24),
        "edit_file" to Label(R.string.agent_tool_edit_file, R.drawable.ic_round_edit_24),
        "rename_file" to Label(R.string.agent_tool_rename_file, R.drawable.ic_round_edit_24),
        "delete_file" to Label(R.string.agent_tool_delete_file, R.drawable.ic_agent_delete),
        "compile_project" to Label(R.string.agent_tool_compile, R.drawable.ic_round_refresh_24),
        "set_project_events" to Label(R.string.agent_tool_set_events, R.drawable.ic_round_notifications_24),
        "create_project" to Label(R.string.agent_tool_create_project, R.drawable.ic_round_add_24),
        "set_project_enabled" to Label(R.string.agent_tool_set_enabled, R.drawable.ic_round_power_settings_new_24),
        "delete_project" to Label(R.string.agent_tool_delete_project, R.drawable.ic_agent_delete),
        "send_debug_message" to Label(R.string.agent_tool_debug_message, R.drawable.ic_round_insert_comment_24),
        "read_debug_replies" to Label(R.string.agent_tool_debug_replies, R.drawable.ic_round_insert_comment_24),
        "save_attachment" to Label(R.string.agent_tool_save_attachment, R.drawable.ic_agent_folder),
        "send_file" to Label(R.string.agent_tool_send_file, R.drawable.ic_agent_attach),
        "show_project" to Label(R.string.agent_tool_show_project, R.drawable.ic_round_projects_24),
    )

    fun label(context: Context, name: String?): String =
        LABELS[name]?.let { context.getString(it.text) } ?: name.orEmpty()

    @DrawableRes
    fun icon(name: String?): Int =
        LABELS[name]?.icon ?: R.drawable.ic_round_code_24

    /** What a call works on, from its arguments: "hello-bot · main.js", “/안녕”, or "" if nothing says. */
    fun target(arguments: String, withProject: Boolean = true): String {
        val args = RequestContent.parseArguments(arguments) ?: return ""
        fun string(name: String) = (args[name] as? JsonPrimitive)?.contentOrNull
        val project = (string("project") ?: string("name")).takeIf { withProject }
        val target = string("path") ?: string("message")?.let { "“$it”" } ?: string("query")?.let { "“$it”" }
        return listOfNotNull(project, target).joinToString(" · ")
    }
}
