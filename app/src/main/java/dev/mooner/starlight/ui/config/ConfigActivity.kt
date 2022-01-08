package dev.mooner.starlight.ui.config

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import dev.mooner.starlight.databinding.ActivityConfigBinding
import dev.mooner.starlight.utils.EXTRA_SUBTITLE
import dev.mooner.starlight.utils.EXTRA_TITLE
import dev.mooner.starlight.utils.initAdapter
import dev.mooner.starlight.utils.onDestroyed

class ConfigActivity : AppCompatActivity() {

    lateinit var binding: ActivityConfigBinding

    var recyclerAdapter: ParentAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra(EXTRA_TITLE)
        val subTitle = intent.getStringExtra(EXTRA_SUBTITLE)

        binding.title.text = title
        binding.subTitle.text = subTitle
        binding.scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val alpha = if (scrollY in 0..200) {
                1f - (scrollY / 200.0f)
            } else {
                0f
            }
            binding.imageViewLogo.alpha = alpha
            binding.title.alpha = alpha
            binding.subTitle.alpha = alpha
        }

        binding.leave.setOnClickListener { finish() }

        initAdapter()
        val mLayoutManager = LinearLayoutManager(applicationContext)
        binding.recyclerView.apply {
            layoutManager = mLayoutManager
            adapter = recyclerAdapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
        recyclerAdapter?.destroy()
        recyclerAdapter = null
    }
}