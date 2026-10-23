package org.koitharu.kotatsu.core.keys

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.addBodyText
import org.koitharu.kotatsu.core.ui.addHeaderCard
import org.koitharu.kotatsu.core.ui.addSectionCard
import org.koitharu.kotatsu.core.ui.bodyText
import org.koitharu.kotatsu.core.ui.dp
import org.koitharu.kotatsu.core.ui.pageColumn
import org.koitharu.kotatsu.core.ui.primaryButton
import org.koitharu.kotatsu.core.ui.scrollable
import org.koitharu.kotatsu.core.ui.secondaryButton
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import org.koitharu.kotatsu.core.ui.statusText
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
		val root = pageColumn()
		root.addHeaderCard(MascotForm.DEFAULT, getString(R.string.api_keys_title), getString(R.string.api_keys_explanation))
		list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		root.addView(list, LinearLayout.LayoutParams(-1, -2))
		setMonogatariContent(root.scrollable(), MonogatariChrome.TEXT_MAX_WIDTH_DP)
		render()
	}

	private fun render() {
		list.removeAllViews()
		for (service in ApiServices.all) {
			val state = if (keys.isConfigured(service.id)) getString(R.string.api_keys_saved) else getString(R.string.api_keys_not_set)
			val column = list.addSectionCard("${service.title} · $state")
			column.addBodyText(service.summary)
			column.addView(secondaryButton(getString(R.string.api_keys_signup)) {
				runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(service.signupUrl))) }
			}.apply {
				contentDescription = getString(R.string.api_keys_signup_for, service.title)
			}, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
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
					column.addView(bodyText(getString(R.string.api_keys_saved_secret, field.label, ApiServices.mask(saved))))
				}
			}
			val status = statusText()
			val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
			buttons.addView(primaryButton(getString(R.string.api_keys_save)) { save(service, inputs, status) })
			buttons.addView(secondaryButton(getString(R.string.api_keys_remove)) {
				keys.clear(service.id)
				render()
			}, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
			column.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
			column.addView(status)
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
