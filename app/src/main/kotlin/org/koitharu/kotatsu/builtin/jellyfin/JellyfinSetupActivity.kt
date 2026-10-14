package org.koitharu.kotatsu.builtin.jellyfin

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import javax.inject.Inject

/** Where the user enters their own Jellyfin server address and API key (stored on this device only). */
@AndroidEntryPoint
class JellyfinSetupActivity : AppCompatActivity() {

	@Inject
	lateinit var config: JellyfinConfig

	@Inject
	@MangaHttpClient
	lateinit var client: OkHttpClient

	private lateinit var serverInput: EditText
	private lateinit var keyInput: EditText
	private lateinit var status: TextView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setTitle(R.string.jellyfin_title)
		val pad = (16 * resources.displayMetrics.density).toInt()
		val root = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(pad, pad, pad, pad)
		}
		root.addView(TextView(this).apply { setText(R.string.jellyfin_explanation) })
		serverInput = EditText(this).apply {
			setHint(R.string.jellyfin_server_hint)
			setSingleLine(true)
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
			setText(config.server.orEmpty())
		}
		keyInput = EditText(this).apply {
			setHint(R.string.jellyfin_key_hint)
			setSingleLine(true)
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
			setText(config.apiKey.orEmpty())
		}
		root.addView(serverInput, LinearLayout.LayoutParams(-1, -2))
		root.addView(keyInput, LinearLayout.LayoutParams(-1, -2))
		status = TextView(this)
		root.addView(MaterialButton(this).apply {
			setText(R.string.jellyfin_save_test)
			setOnClickListener { saveAndTest() }
		}, LinearLayout.LayoutParams(-2, -2))
		root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
			setText(R.string.jellyfin_forget)
			setOnClickListener {
				config.clear()
				serverInput.setText("")
				keyInput.setText("")
				status.setText(R.string.jellyfin_forgotten)
			}
		}, LinearLayout.LayoutParams(-2, -2))
		root.addView(status)
		setMonogatariContent(root, MonogatariChrome.TEXT_MAX_WIDTH_DP)
	}

	private fun saveAndTest() {
		val server = JellyfinRepository.normalizeServer(serverInput.text.toString())
		val key = keyInput.text.toString().trim()
		if (server == null || key.isEmpty()) {
			status.setText(R.string.jellyfin_incomplete)
			return
		}
		config.save(server, key)
		serverInput.setText(server)
		status.setText(R.string.jellyfin_testing)
		lifecycleScope.launch {
			status.text = withContext(Dispatchers.IO) { probe(server, key) }
		}
	}

	private fun probe(server: String, key: String): String = try {
		val request = Request.Builder().url("$server/System/Info").header(JellyfinRepository.AUTH_HEADER, key).build()
		client.newCall(request).execute().use { response ->
			when {
				response.code == 401 -> getString(R.string.jellyfin_rejected)
				!response.isSuccessful -> getString(R.string.jellyfin_http_error, response.code)
				else -> {
					val name = JSONObject(response.body?.string().orEmpty().ifEmpty { "{}" }).optString("ServerName")
					getString(R.string.jellyfin_connected, name.ifBlank { server })
				}
			}
		}
	} catch (e: Exception) {
		getString(R.string.jellyfin_unreachable, e.message.orEmpty())
	}
}
