package com.tvtoolbox.screensaver

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
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
            val pref = findPreference<Preference>("test_fetch")
            pref?.title = getString(R.string.test_running)

            scope.launch {
                val (msg, ok) = withContext(Dispatchers.IO) {
                    try {
                        val list = ImageFetcher().fetch(url)
                        Pair(getString(R.string.test_ok, list.size), true)
                    } catch (t: Throwable) {
                        Pair(getString(R.string.test_fail, t.message ?: "?"), false)
                    }
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                pref?.title = if (ok) getString(R.string.pref_test_title) else getString(R.string.pref_test_title)
                // 无论如何都恢复标题
                pref?.title = getString(R.string.pref_test_title)
            }
        }

        private fun openDreamSettings() {
            try {
                val intent = Intent(Settings.ACTION_DREAM_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (t: Throwable) {
                // 某些 ROM 没有这个 action，退到通用 settings
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
