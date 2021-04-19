package com.mooner.starlight.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.mooner.starlight.R
import com.mooner.starlight.Utils.Companion.getLogger
import com.mooner.starlight.core.ApplicationSession.taskHandler
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_home.view.*

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
                ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        //val textView: TextView = root.findViewById(R.id.textViewLogs)
        homeViewModel.text.observe(viewLifecycleOwner, {
            //textView.text = it
        })
        root.switchAllPower.setOnCheckedChangeListener { _, isChecked ->
            root.cardViewAllPower.setCardBackgroundColor(root.context.getColor(if (isChecked) R.color.cardview_enabled else R.color.cardview_disabled))
        }

        return root
    }
}