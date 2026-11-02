package dev.mooner.starlight.ui.agent

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import coil.load
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.model.Card
import dev.mooner.starlight.databinding.SheetAgentFilePreviewBinding
import dev.mooner.starlight.ui.common.SlideBottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the file cards do with a file: show it in the app when it can, or hand it to other apps. */
internal object FilePreview {

    enum class Kind { MARKDOWN, TEXT, IMAGE }

    /** How the app can show [card] itself, or null if only other apps can. */
    fun kindOf(card: Card): Kind? {
        val extension = card.name.substringAfterLast('.', "").lowercase()
        val mime = card.mimeType.orEmpty()
        return when {
            extension in MARKDOWN_EXTENSIONS || mime == "text/markdown" -> Kind.MARKDOWN
            mime.startsWith("image/") && mime != "image/svg+xml"      -> Kind.IMAGE
            mime.startsWith("text/") || mime in TEXT_TYPES || extension in TEXT_EXTENSIONS -> Kind.TEXT
            else                                                      -> null
        }
    }

    /**
     * Shows [file] in a sheet when the app can, otherwise offers the apps that can open it. Without [onSave], the
     * sheet has no save button; sharing still saves it elsewhere.
     */
    fun open(activity: AppCompatActivity, card: Card, file: File, onSave: (() -> Unit)? = null) {
        val kind = kindOf(card)
        if (kind == null)
            openWith(activity, card, file)
        else
            show(activity, card, file, kind, onSave)
    }

    private fun show(activity: AppCompatActivity, card: Card, file: File, kind: Kind, onSave: (() -> Unit)?) {
        val binding = SheetAgentFilePreviewBinding.inflate(LayoutInflater.from(activity))
        val dialog = SlideBottomSheetDialog(activity)
        dialog.setContentView(binding.root)
        binding.name.text = card.name
        binding.meta.text = listOfNotNull(card.project, card.path?.takeIf { it != card.name },
            Formatter.formatShortFileSize(activity, file.length())).joinToString(" · ")
        binding.icon.setImageResource(if (kind == Kind.IMAGE) R.drawable.ic_agent_image else R.drawable.ic_round_article_24)
        shrinkIcons(activity, binding.share, binding.save, binding.openWith)
        binding.close.setOnClickListener { dialog.dismiss() }
        binding.share.setOnClickListener { share(activity, card, file) }
        binding.save.isVisible = onSave != null
        binding.save.setOnClickListener { onSave?.invoke() }
        binding.openWith.setOnClickListener { openWith(activity, card, file) }

        when (kind) {
            Kind.IMAGE -> {
                binding.image.isVisible = true
                binding.image.load(file)
            }
            Kind.MARKDOWN, Kind.TEXT -> activity.lifecycleScope.launch {
                val read = runCatching { withContext(Dispatchers.IO) { readHead(file) } }
                read.onFailure { e ->
                    binding.note.isVisible = true
                    binding.note.text = activity.getString(R.string.agent_preview_failed, e.message ?: e.toString())
                }
                val (text, truncated) = read.getOrNull() ?: return@launch
                if (truncated) {
                    binding.note.isVisible = true
                    binding.note.text = activity.getString(R.string.agent_preview_truncated,
                        Formatter.formatShortFileSize(activity, MAX_PREVIEW_BYTES.toLong()))
                }
                if (kind == Kind.MARKDOWN)
                    MarkdownRenderer(activity, AgentUi.markdown(activity)).render(binding.content, text)
                else {
                    binding.code.isVisible = true
                    binding.code.text = text
                }
            }
        }

        val closer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY)
                dialog.dismiss()
        }
        activity.lifecycle.addObserver(closer)
        dialog.setOnDismissListener { activity.lifecycle.removeObserver(closer) }
        dialog.show()
    }

    /** The start of [file] as text, and whether there was more. */
    private fun readHead(file: File): Pair<String, Boolean> {
        val bytes = file.inputStream().use { input ->
            val buffer = ByteArray(MAX_PREVIEW_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0)
                    break
                total += read
            }
            buffer.copyOf(total)
        }
        val truncated = bytes.size > MAX_PREVIEW_BYTES
        val text = String(if (truncated) bytes.copyOf(MAX_PREVIEW_BYTES) else bytes, Charsets.UTF_8)
        return text to truncated
    }

    /** Lets the user pick another app to open [file] with. */
    fun openWith(context: Context, card: Card, file: File) {
        val uri = uriOf(context, file) ?: return toast(context, R.string.agent_card_no_app)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, viewType(card))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(intent, card.name))
    }

    fun share(context: Context, card: Card, file: File) {
        val uri = uriOf(context, file) ?: return toast(context, R.string.agent_card_no_app)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(card.mimeType ?: "application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, card.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(card.name, uri)
        start(context, Intent.createChooser(intent, card.name))
    }

    /** Card and sheet buttons use the 24dp icons at 16dp. */
    fun shrinkIcons(context: Context, vararg views: TextView) {
        val size = AgentUi.dp(context, 16)
        for (view in views) {
            val icon = view.compoundDrawablesRelative[0] ?: continue
            icon.setBounds(0, 0, size, size)
            view.setCompoundDrawablesRelative(icon, null, null, null)
        }
    }

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(context, R.string.agent_card_no_app)
        }
    }

    /** Null if [file] is somewhere the app's FileProvider doesn't share from. */
    private fun uriOf(context: Context, file: File): Uri? =
        runCatching { FileProvider.getUriForFile(context, FILE_AUTHORITY, file) }.getOrNull()

    private fun viewType(card: Card): String {
        val mime = card.mimeType ?: return "*/*"
        // Few phones have a viewer for script types, but most have one for plain text.
        return if (mime.startsWith("text/") || mime in TEXT_TYPES) "text/plain" else mime
    }

    private fun toast(context: Context, @StringRes text: Int) =
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    private const val FILE_AUTHORITY = "dev.mooner.starlight.provider"
    private const val MAX_PREVIEW_BYTES = 256 * 1024
    private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd")
    private val TEXT_TYPES = setOf("application/json", "application/javascript", "application/xml", "application/x-javascript")
    private val TEXT_EXTENSIONS = setOf("txt", "js", "mjs", "ts", "json", "csv", "log", "xml", "yml", "yaml", "kt", "java", "py", "html", "css", "ini", "conf")
}
