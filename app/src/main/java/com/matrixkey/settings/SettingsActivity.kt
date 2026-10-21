package com.matrixkey.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

/** Settings home, Nothing-style: big dot title, a search bar, then tiles. */
class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private val containerId = 0x6d6b01
    private lateinit var titleView: TextView
    private lateinit var backButton: View
    private lateinit var search: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val (header, title) = pageHeader(this, "Settings", showBack = false) { onBackPressedDispatcher.onBackPressed() }
        titleView = title
        backButton = header.findViewWithTag("back")
        root.addView(header)
        val d = resources.displayMetrics.density
        search = searchPill(this) { openSearch() }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60 * d).toInt()).apply {
            setMargins((16 * d).toInt(), 0, (16 * d).toInt(), (10 * d).toInt())
        })
        val container = FrameLayout(this).apply { id = containerId }
        root.addView(container, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (savedInstanceState == null) {
            val start = intent.getStringExtra("xml")
            supportFragmentManager.beginTransaction()
                .replace(containerId, PrefsFragment.newInstance("prefs_main", "Settings"))
                .commitNow()
            if (start != null) openScreen(start, intent.getStringExtra("title") ?: "", intent.getStringExtra("highlight"))
        }
        supportFragmentManager.addOnBackStackChangedListener { updateHeader() }
        // Back button in the header and system back both step out one level.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
                else finish()
            }
        })
        updateHeader()
    }

    private fun updateHeader() {
        val f = supportFragmentManager.findFragmentById(containerId)
        val isHome = supportFragmentManager.backStackEntryCount == 0
        titleView.text = when (f) {
            is PrefsFragment -> (f.arguments?.getString("title") ?: "Settings").uppercase()
            is SearchFragment -> "SEARCH"
            else -> "SETTINGS"
        }
        backButton.visibility = if (isHome) View.INVISIBLE else View.VISIBLE
        search.visibility = if (isHome) View.VISIBLE else View.GONE
    }

    private fun openSearch() {
        supportFragmentManager.beginTransaction()
            .replace(containerId, SearchFragment())
            .addToBackStack("search")
            .commit()
    }

    /** Opens a settings screen, optionally scrolling to and flashing one setting. */
    fun openScreen(xml: String, title: String, highlight: String?) {
        when (xml) {
            "theme" -> { startActivity(Intent(this, ThemeActivity::class.java)); return }
            "dictionary" -> { startActivity(Intent(this, DictionaryActivity::class.java)); return }
        }
        if (supportFragmentManager.findFragmentById(containerId) is SearchFragment) supportFragmentManager.popBackStackImmediate()
        val f = PrefsFragment.newInstance(xml, title).apply { arguments?.putString("highlight", highlight) }
        if (xml == "prefs_main") {
            supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            (supportFragmentManager.findFragmentById(containerId) as? PrefsFragment)?.highlight(highlight)
            return
        }
        supportFragmentManager.beginTransaction()
            .replace(containerId, f)
            .addToBackStack(null)
            .commit()
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val args = pref.extras
        openScreen(args.getString("xml") ?: return false, args.getString("title") ?: pref.title.toString(), null)
        return true
    }

}
