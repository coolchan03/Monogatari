package org.koitharu.kotatsu.reader.translate

import androidx.core.view.isVisible
import androidx.core.view.children
import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.davemorrissey.labs.subscaleview.ImageSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.util.ext.consumeAll
import org.koitharu.kotatsu.core.util.ext.end
import org.koitharu.kotatsu.core.util.ext.start
import org.koitharu.kotatsu.databinding.ActivityTranslatedPageBinding
import java.io.File
import javax.inject.Inject
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shows a translated page with a one-tap switch back to the original, and lets the user tap a text bubble to see
 * how sure the text recognition was and to type a better translation.
 */
@AndroidEntryPoint
class TranslatedPageActivity : BaseActivity<ActivityTranslatedPageBinding>(), View.OnClickListener {

	@Inject
	lateinit var translator: PageTranslator

	private lateinit var result: PageTranslator.Result
	private var meta: TranslationMeta? = null
	private var showingOriginal = false
	private var sideBySide = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityTranslatedPageBinding.inflate(layoutInflater))
		result = PageTranslator.Result(
			file = File(intent.getStringExtra(EXTRA_TRANSLATED).orEmpty()),
			originalFile = File(intent.getStringExtra(EXTRA_ORIGINAL).orEmpty()),
			metaFile = File(intent.getStringExtra(EXTRA_META).orEmpty()),
			blockCount = intent.getIntExtra(EXTRA_COUNT, 0),
			sourceLanguage = intent.getStringExtra(EXTRA_SOURCE).orEmpty(),
			targetLanguage = intent.getStringExtra(EXTRA_TARGET).orEmpty(),
		)
		showingOriginal = savedInstanceState?.getBoolean(STATE_ORIGINAL) ?: false
		sideBySide = savedInstanceState?.getBoolean(STATE_SIDE_BY_SIDE) ?: false
		viewBinding.buttonSideBySide.setOnClickListener {
			sideBySide = !sideBySide
			showImage()
		}
		meta = translator.readMeta(result)
		viewBinding.buttonBack.setOnClickListener(this)
		viewBinding.buttonToggle.setOnClickListener(this)
		viewBinding.buttonFont.setOnClickListener { chooseFont() }
		val detector = GestureDetector(
			this,
			object : GestureDetector.SimpleOnGestureListener() {
				override fun onSingleTapUp(e: MotionEvent): Boolean {
					onPageTapped(e.x, e.y)
					return false
				}
			},
		)
		viewBinding.ssiv.setOnTouchListener { _, event ->
			detector.onTouchEvent(event)
			false
		}
		showImage()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putBoolean(STATE_ORIGINAL, showingOriginal)
		outState.putBoolean(STATE_SIDE_BY_SIDE, sideBySide)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_back -> dispatchNavigateUp()
			R.id.button_toggle -> {
				showingOriginal = !showingOriginal
				showImage()
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		val baseMargin = v.resources.getDimensionPixelOffset(R.dimen.screen_padding)
		viewBinding.buttonBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			marginStart = barsInsets.start(v) + baseMargin
			topMargin = barsInsets.top + baseMargin
		}
		viewBinding.buttonToggle.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			bottomMargin = barsInsets.bottom
			marginEnd = barsInsets.end(v)
		}
		viewBinding.buttonFont.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			marginEnd = barsInsets.end(v)
		}
		return insets.consumeAll(typeMask)
	}

	private fun showImage() {
		// Side by side: translation (tappable for corrections) plus the original; columns when wide, rows when tall.
		val config = resources.configuration
		viewBinding.pages.orientation = if (config.screenWidthDp >= config.screenHeightDp) {
			android.widget.LinearLayout.HORIZONTAL
		} else {
			android.widget.LinearLayout.VERTICAL
		}
		viewBinding.pages.children.forEach { child ->
			child.updateLayoutParams<android.widget.LinearLayout.LayoutParams> {
				if (viewBinding.pages.orientation == android.widget.LinearLayout.HORIZONTAL) {
					width = 0; height = ViewGroup.LayoutParams.MATCH_PARENT
				} else {
					width = ViewGroup.LayoutParams.MATCH_PARENT; height = 0
				}
			}
		}
		viewBinding.ssivOriginal.isVisible = sideBySide
		viewBinding.buttonToggle.isVisible = !sideBySide
		viewBinding.buttonSideBySide.setText(
			if (sideBySide) R.string.translated_page_single else R.string.translated_page_side_by_side,
		)
		if (sideBySide) {
			showingOriginal = false
			viewBinding.ssivOriginal.setImage(ImageSource.file(result.originalFile))
			viewBinding.ssivOriginal.contentDescription = getString(R.string.translated_page_viewing_original)
		}
		val file = if (showingOriginal) result.originalFile else result.file
		viewBinding.ssiv.setImage(ImageSource.file(file))
		viewBinding.ssiv.contentDescription = getString(
			if (showingOriginal) R.string.translated_page_viewing_original else R.string.translated_page_description,
		)
		viewBinding.buttonToggle.setText(
			if (showingOriginal) R.string.translated_page_show_translated else R.string.translated_page_show_original,
		)
		val doubtful = meta?.blocks?.count { TranslationSidecar.isDoubtful(it) } ?: 0
		viewBinding.textHint.text = when {
			showingOriginal -> getString(R.string.translated_page_viewing_original)
			doubtful > 0 -> resources.getQuantityString(R.plurals.translated_page_doubtful, doubtful, doubtful)
			else -> getString(R.string.translated_page_hint)
		}
	}

	private fun onPageTapped(viewX: Float, viewY: Float) {
		if (showingOriginal) return
		val current = meta ?: return
		val point: PointF = viewBinding.ssiv.viewToSourceCoord(viewX, viewY) ?: return
		val index = TranslationSidecar.blockAt(current.blocks, point.x, point.y) ?: return
		showCorrectionDialog(current, index)
	}

	private fun showCorrectionDialog(current: TranslationMeta, index: Int) {
		val block = current.blocks[index]
		val input = EditText(this).apply {
			setText(block.translation)
			setSelection(text.length)
			contentDescription = getString(R.string.translated_edit_hint)
			setPadding(48, 24, 48, 24)
		}
		val percent = TranslationSidecar.confidencePercent(block)
		val message = if (percent != null) {
			getString(R.string.translated_edit_recognised, block.original, percent)
		} else {
			getString(R.string.translated_edit_recognised_unknown, block.original)
		}
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.translated_edit_title)
			.setMessage(message)
			.setView(input)
			.setPositiveButton(R.string.translated_edit_save) { _, _ ->
				val updated = TranslationSidecar.withCorrection(current, index, input.text.toString())
				if (updated !== current) applyCorrection(updated)
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun chooseFont() {
		val current = meta ?: return
		val styles = arrayOf("sans", "serif", "comic")
		val labels = arrayOf(
			getString(R.string.translated_page_font_sans),
			getString(R.string.translated_page_font_serif),
			getString(R.string.translated_page_font_comic),
		)
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.translated_page_font_title)
			.setSingleChoiceItems(labels, styles.indexOf(current.fontStyle).coerceAtLeast(0)) { dialog, selected ->
				dialog.dismiss()
				if (styles[selected] != current.fontStyle) applyCorrection(current.copy(fontStyle = styles[selected]))
			}
			.setNegativeButton(android.R.string.cancel, null).show()
	}

	private fun applyCorrection(updated: TranslationMeta) {
		lifecycleScope.launch {
			try {
				translator.rewrite(result, updated)
				meta = updated
				showImage()
			} catch (e: Exception) {
				Toast.makeText(this@TranslatedPageActivity, getString(R.string.translated_page_font_failed), Toast.LENGTH_LONG).show()
			}
		}
	}

	companion object {

		private const val STATE_SIDE_BY_SIDE = "side_by_side"

		private const val EXTRA_TRANSLATED = "translated"
		private const val EXTRA_ORIGINAL = "original"
		private const val EXTRA_META = "meta"
		private const val EXTRA_COUNT = "count"
		private const val EXTRA_SOURCE = "source"
		private const val EXTRA_TARGET = "target"
		private const val STATE_ORIGINAL = "showing_original"

		fun newIntent(context: Context, result: PageTranslator.Result): Intent =
			Intent(context, TranslatedPageActivity::class.java)
				.putExtra(EXTRA_TRANSLATED, result.file.path)
				.putExtra(EXTRA_ORIGINAL, result.originalFile.path)
				.putExtra(EXTRA_META, result.metaFile.path)
				.putExtra(EXTRA_COUNT, result.blockCount)
				.putExtra(EXTRA_SOURCE, result.sourceLanguage)
				.putExtra(EXTRA_TARGET, result.targetLanguage)
	}
}
