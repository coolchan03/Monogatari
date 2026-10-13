package org.koitharu.kotatsu.backups.domain

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import java.io.File
import java.io.IOException

/** Read-only review; foreign backups are NEVER silently merged or restored. */
class ForeignBackupReviewActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var chooseFile: MaterialButton

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) inspectSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.foreign_backup_review_title)
        val pad = (16f * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        chooseFile = MaterialButton(this).apply {
            text = getString(R.string.foreign_backup_choose)
            setOnClickListener { picker.launch(arrayOf("*/*")) }
        }
        status = TextView(this).apply {
            text = getString(R.string.foreign_backup_read_only)
            textSize = 15f
            setTextIsSelectable(true)
        }
        root.addView(chooseFile)
        root.addView(ScrollView(this).apply { addView(status) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty()

    private fun inspectSelected(uri: Uri) {
        chooseFile.isEnabled = false
        status.text = getString(R.string.foreign_backup_reviewing)
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching {
                    val staged = File.createTempFile("foreign-preview-", ".tmp", cacheDir)
                    try {
                        contentResolver.openInputStream(uri)?.use {
                            BackupArchiveInspector.copyBounded(it, staged)
                        } ?: throw IOException("Selected backup is unreadable")
                        ForeignBackupReview.inspect(staged, displayName(uri))
                    } finally {
                        staged.delete()
                    }
                }
            }
            chooseFile.isEnabled = true
            status.text = report.fold(
                onSuccess = { value ->
                    buildString {
                        appendLine(getString(R.string.foreign_backup_format, value.format.name))
                        value.counts.forEach { (name, count) ->
                            appendLine("${name}: ${count}")
                        }
                        appendLine()
                        appendLine(value.details)
                        appendLine()
                        append(getString(R.string.foreign_backup_read_only))
                    }
                },
                onFailure = { error ->
                    getString(R.string.foreign_backup_failed, error.message.orEmpty())
                },
            )
        }
    }
}
