package com.tvtoolbox.screensaver

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置宿主 Activity。承载 SettingsFragment。
 * 手机和 TV 都用同一个 fragment，只是宿主样式不同。
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.toolbar_settings))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.settings_title)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            when (preference.key) {
                "test_fetch" -> doTest()
                "open_dream_settings" -> openDreamSettings()
            }
            return super.onPreferenceTreeClick(preference)
        }

        private fun doTest() {
            val url = Prefs.imageUrl(requireContext())
            if (url.isBlank()) {
                Toast.makeText(requireContext(), R.string.no_url, Toast.LENGTH_SHORT).show()
                return
            }
            val pref = findPreference<Preference>("test_fetch") ?: return
            val originalTitle = pref.title
            pref.title = getString(R.string.test_running)

            scope.launch {
                val msg = withContext(Dispatchers.IO) {
                    try {
                        val list = ImageFetcher().fetch(url)
                        getString(R.string.test_ok, list.size)
                    } catch (t: Throwable) {
                        getString(R.string.test_fail, t.message ?: "?")
                    }
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                pref.title = originalTitle
            }
        }

        private fun openDreamSettings() {
            try {
                startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (t: Throwable) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Throwable) {
                    Toast.makeText(requireContext(), "无法打开系统设置", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            scope.cancel()
        }
    }
}
