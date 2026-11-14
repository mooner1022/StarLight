package dev.mooner.starlight.ui.agent.docs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.docs.DocsIndex
import dev.mooner.starlight.agent.docs.DocsIndex.Category
import dev.mooner.starlight.agent.docs.DtsRenderer
import dev.mooner.starlight.databinding.ActivityAgentDocsBinding
import dev.mooner.starlight.databinding.ItemAgentDocBinding
import dev.mooner.starlight.databinding.ItemAgentDocSectionBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.ui.agent.AgentUi
import dev.mooner.starlight.utils.fitPage
import dev.mooner.starlight.utils.restartIfNotReady
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The docs StarLight AI reads, for checking them in developer mode: guides, APIs, events and types, each grouped by
 * the app or plugin they come from. A tap opens one as the agent's tools return it ([AgentDocActivity]).
 */
class AgentDocsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgentDocsBinding

    private var index: DocsIndex? = null
    private var category = Category.GUIDE

    private val adapter by lazy {
        DocsAdapter(AgentUi.markdown(this)) { entry -> startActivity(AgentDocActivity.newIntent(this, entry.category, entry.key)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Brought back before the runtime started: start over from the splash, without restoring what needs it.
        if (restartIfNotReady()) {
            super.onCreate(null)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        binding = ActivityAgentDocsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitPage(binding.root)

        binding.back.setOnClickListener { finish() }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        savedInstanceState?.getString(KEY_CATEGORY)
            ?.let { name -> Category.entries.firstOrNull { it.name == name } }
            ?.let { category = it }
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CATEGORY, category.name)
    }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            // Reflection over every class scripts reach, and the plugins' packages: off the main thread.
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    AgentHub.get(this@AgentDocsActivity).docs.index().also { index ->
                        Category.entries.forEach(index::grouped)
                    }
                }
            }
            binding.progress.visibility = View.GONE
            result
                .onSuccess { index = it; showCategories(); show(category) }
                .onFailure { showMessage(getString(R.string.agent_docs_failed, it.toString())) }
        }
    }

    private fun showCategories() {
        val index = index ?: return
        binding.categories.removeAllViews()
        for (each in Category.entries) {
            val chip = TextView(this).apply {
                text = getString(R.string.agent_docs_category_count, getString(labelOf(each)), index.entries(each).size)
                typeface = ResourcesCompat.getFont(context, R.font.wantedsans_medium)
                textSize = 13f
                minHeight = AgentUi.dp(context, 34)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(AgentUi.dp(context, 14), 0, AgentUi.dp(context, 14), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = AgentUi.dp(context, 6) }
                setOnClickListener { show(each) }
                tag = each
            }
            binding.categories.addView(chip)
        }
    }

    private fun show(category: Category) {
        val index = index ?: return
        this.category = category
        for (i in 0 until binding.categories.childCount) {
            val chip = binding.categories.getChildAt(i) as TextView
            val selected = chip.tag == category
            chip.setBackgroundResource(if (selected) R.drawable.bg_agent_chip else R.drawable.bg_agent_chip_outline)
            chip.setTextColor(ContextCompat.getColor(this, if (selected) R.color.agent_accent else R.color.agent_text_muted))
        }
        val rows = index.grouped(category).flatMap { (source, entries) ->
            listOf<Row>(Row.Section(sourceLabel(this, source), entries.size)) + entries.map(Row::Doc)
        }
        adapter.submitList(rows) { binding.list.scrollToPosition(0) }
        if (rows.isEmpty())
            showMessage(getString(R.string.agent_docs_empty))
        else
            binding.message.visibility = View.GONE
    }

    private fun showMessage(text: String) {
        binding.message.text = text
        binding.message.visibility = View.VISIBLE
    }

    private sealed interface Row {
        data class Section(val name: String, val count: Int) : Row
        data class Doc(val entry: DocsIndex.Entry) : Row
    }

    private class DocsAdapter(
        /** Summaries are Markdown, as the docs have them. */
        private val markwon: Markwon,
        private val onClick: (DocsIndex.Entry) -> Unit,
    ) : ListAdapter<Row, RecyclerView.ViewHolder>(DIFF) {

        class SectionHolder(val binding: ItemAgentDocSectionBinding) : RecyclerView.ViewHolder(binding.root)
        class DocHolder(val binding: ItemAgentDocBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemViewType(position: Int): Int =
            if (getItem(position) is Row.Section) TYPE_SECTION else TYPE_DOC

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_SECTION)
                SectionHolder(ItemAgentDocSectionBinding.inflate(inflater, parent, false))
            else
                DocHolder(ItemAgentDocBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = getItem(position)) {
                is Row.Section -> (holder as SectionHolder).binding.apply {
                    name.text = row.name
                    count.text = row.count.toString()
                }
                is Row.Doc -> (holder as DocHolder).binding.apply {
                    val entry = row.entry
                    title.text = entry.title
                    detail.text = entry.detail
                    detail.visibility = if (entry.detail == entry.title) View.GONE else View.VISIBLE
                    summary.text = entry.summary?.let(markwon::toMarkdown)
                    summary.visibility = if (entry.summary == null) View.GONE else View.VISIBLE
                    noDocs.visibility = if (entry.documented) View.GONE else View.VISIBLE
                    root.setOnClickListener { onClick(entry) }
                }
            }
        }

        private companion object {
            const val TYPE_SECTION = 0
            const val TYPE_DOC = 1

            val DIFF = object : DiffUtil.ItemCallback<Row>() {
                override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean = when {
                    oldItem is Row.Section && newItem is Row.Section -> oldItem.name == newItem.name
                    oldItem is Row.Doc && newItem is Row.Doc ->
                        oldItem.entry.category == newItem.entry.category && oldItem.entry.key == newItem.entry.key
                    else -> false
                }

                override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean =
                    oldItem == newItem
            }
        }
    }

    companion object {

        private const val KEY_CATEGORY = "category"

        fun labelOf(category: Category): Int = when (category) {
            Category.GUIDE -> R.string.agent_docs_guides
            Category.API   -> R.string.agent_docs_apis
            Category.EVENT -> R.string.agent_docs_events
            Category.TYPE  -> R.string.agent_docs_types
        }

        /** `StarLight`, or a plugin's name and ID. */
        fun sourceLabel(context: Context, source: String): String {
            if (source == DtsRenderer.STARLIGHT)
                return "StarLight"
            val name = Session.pluginManager.getPluginById(source)?.info?.name ?: return source
            return context.getString(R.string.agent_docs_source_plugin, name, source)
        }
    }
}
