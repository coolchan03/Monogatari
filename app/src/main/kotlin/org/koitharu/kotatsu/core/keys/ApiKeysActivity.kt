package org.koitharu.kotatsu.core.keys

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import javax.inject.Inject

/**
 * One place for the user's own API keys and accounts: what each service is for, a link to sign up or find the key,
 * and the fields to paste it into. Nothing is sent anywhere except to the service the key belongs to.
 */
@AndroidEntryPoint
class ApiKeysActivity : AppCompatActivity() {

	@Inject
	lateinit var keys: ApiKeys

	private lateinit var list: LinearLayout

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// Keep credentials out of screenshots and the Android recent-apps preview.
		window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
		setTitle(R.string.api_keys_title)
		val pad = (12 * resources.displayMetrics.density).toInt()
		val root = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(pad, pad, pad, pad)
		}
		root.addView(TextView(this).apply { setText(R.string.api_keys_explanation) }, LinearLayout.LayoutParams(-1, -2))
		list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
		setMonogatariContent(root, MonogatariChrome.TEXT_MAX_WIDTH_DP)
		render()
	}

	private fun render() {
		list.removeAllViews()
		val pad = (14 * resources.displayMetrics.density).toInt()
		for (service in ApiServices.all) {
			val card = MaterialCardView(this).apply {
				radius = 16 * resources.displayMetrics.density
				cardElevation = 0f
				setContentPadding(pad, pad, pad, pad)
			}
			val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
			val state = if (keys.isConfigured(service.id)) getString(R.string.api_keys_saved) else getString(R.string.api_keys_not_set)
			column.addView(TextView(this).apply {
				text = "${service.title} · $state"
				setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
			})
			column.addView(TextView(this).apply { text = service.summary })
			column.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
				text = getString(R.string.api_keys_signup)
				contentDescription = getString(R.string.api_keys_signup_for, service.title)
				setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(service.signupUrl))) } }
			})
			val inputs = LinkedHashMap<String, EditText>()
			for (field in service.fields) {
				val input = EditText(this).apply {
					hint = if (field.hint.isNotBlank()) "${field.label} (${field.hint})" else field.label
					setSingleLine(true)
					inputType = if (field.secret) {
						InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
					} else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
					// A saved secret is shown masked; typing replaces it.
					val saved = keys.get(service.id, field.key)
					if (saved != null && !field.secret) setText(saved)
				}
				inputs[field.key] = input
				column.addView(input, LinearLayout.LayoutParams(-1, -2))
				val saved = keys.get(service.id, field.key)
				if (saved != null && field.secret) {
					column.addView(TextView(this).apply { text = getString(R.string.api_keys_saved_secret, field.label, ApiServices.mask(saved)) })
				}
			}
			val status = TextView(this)
			val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
			buttons.addView(MaterialButton(this).apply {
				setText(R.string.api_keys_save)
				setOnClickListener { save(service, inputs, status) }
			})
			buttons.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
				setText(R.string.api_keys_remove)
				setOnClickListener {
					keys.clear(service.id)
					render()
				}
			})
			column.addView(buttons)
			column.addView(status)
			card.addView(column)
			list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pad })
		}
	}

	/** Leaves a saved secret untouched when its box is empty, so editing one field doesn't wipe the other. */
	private fun save(service: ApiService, inputs: Map<String, EditText>, status: TextView) {
		val typed = inputs.mapValues { (field, box) ->
			box.text.toString().ifBlank { keys.get(service.id, field).orEmpty() }
		}
		when (val check = ApiServices.validate(service, typed)) {
			is ApiServices.Check.Problem -> status.text = check.reason
			is ApiServices.Check.Ok -> {
				keys.set(service.id, check.values)
				render()
			}
		}
	}
}
