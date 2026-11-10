package dev.mooner.starlight.ui.agent.docs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.docs.DocsIndex
import dev.mooner.starlight.agent.docs.DocsIndex.Category
import dev.mooner.starlight.agent.docs.GuideLibrary
import dev.mooner.starlight.databinding.ActivityAgentDocBinding
import dev.mooner.starlight.ui.agent.AgentUi
import dev.mooner.starlight.ui.agent.MarkdownRenderer
import dev.mooner.starlight.utils.fitPage
import io.noties.markwon.LinkResolver
import io.noties.markwon.LinkResolverDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One of StarLight AI's docs, exactly as its tools return it: a guide as Markdown, an API, event or type as the
 * declarations `describe_api` and `describe_event` give. `guide:` references open the guide here.
 */
class AgentDocActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgentDocBinding

    private lateinit var category: Category
    private lateinit var key: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgentDocBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitPage(binding.root)

        category = intent.getStringExtra(EXTRA_CATEGORY)
            ?.let { name -> Category.entries.firstOrNull { it.name == name } }
            ?: Category.GUIDE
        key = intent.getStringExtra(EXTRA_KEY).orEmpty()

        binding.back.setOnClickListener { finish() }
        binding.title.text = key
        binding.subtitle.text = getString(AgentDocsActivity.labelOf(category))
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val (entry, content) = withContext(Dispatchers.Default) {
                runCatching {
                    val index = AgentHub.get(this@AgentDocActivity).docs.index()
                    index.entry(category, key) to index.content(category, key)
                }
                    .getOrElse { null to DocsIndex.Content(getString(R.string.agent_docs_failed, it.toString()), isMarkdown = false) }
            }
            binding.progress.visibility = View.GONE
            if (entry != null) {
                binding.title.text = entry.title
                binding.subtitle.text = listOf(
                    getString(AgentDocsActivity.labelOf(category)),
                    AgentDocsActivity.sourceLabel(this@AgentDocActivity, entry.source),
                    // A guide's section, when a reference opened one.
                    (if (category == Category.GUIDE) key.removePrefix(GuideLibrary.PREFIX) else entry.detail).takeIf { it != entry.title },
                ).filterNotNull().joinToString(" · ")
            }
            show(content)
        }
    }

    private fun show(content: DocsIndex.Content) {
        if (content.isMarkdown) {
            val markwon = AgentUi.markdown(this, LinkResolver { view, link ->
                if (link.startsWith(GuideLibrary.PREFIX)) openGuide(link) else LinkResolverDef().resolve(view, link)
            })
            MarkdownRenderer(this, markwon).render(binding.markdown, content.text)
        } else {
            binding.code.text = withGuideLinks(content.text.trimEnd())
            binding.code.movementMethod = LinkMovementMethod.getInstance()
            binding.code.visibility = View.VISIBLE
        }
        binding.copy.isEnabled = true
        binding.copy.setOnClickListener {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("StarLight AI", content.text))
            Toast.makeText(this, R.string.agent_copied, Toast.LENGTH_SHORT).show()
        }
    }

    /** [text] with each `guide:` reference, such as the `// Related:` lines', opening its guide. */
    private fun withGuideLinks(text: String): CharSequence {
        val spannable = SpannableString(text)
        for (match in GUIDE_REF.findAll(text)) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = openGuide(match.value)
            }, match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun openGuide(ref: String) =
        startActivity(newIntent(this, Category.GUIDE, ref))

    companion object {

        private const val EXTRA_CATEGORY = "category"
        private const val EXTRA_KEY = "key"

        /** `guide:<id>[#anchor]` or `guide:<module>:<id>[#anchor]`, without trailing punctuation. */
        private val GUIDE_REF = Regex("""${GuideLibrary.PREFIX}[A-Za-z0-9._/#:-]*[A-Za-z0-9_/-]""")

        fun newIntent(context: Context, category: Category, key: String): Intent =
            Intent(context, AgentDocActivity::class.java)
                .putExtra(EXTRA_CATEGORY, category.name)
                .putExtra(EXTRA_KEY, key)
    }
}
