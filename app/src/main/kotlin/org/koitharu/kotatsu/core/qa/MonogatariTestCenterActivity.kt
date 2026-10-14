package org.koitharu.kotatsu.core.qa

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.backups.domain.ForeignBackupReviewActivity
import org.koitharu.kotatsu.discover.DiscoverActivity
import org.koitharu.kotatsu.core.media.BookLibraryActivity
import org.koitharu.kotatsu.core.media.MediaFeedActivity
import org.koitharu.kotatsu.core.media.UnifiedLibraryActivity
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.databinding.ActivityMonogatariTestCenterBinding
import org.koitharu.kotatsu.main.ui.MainActivity
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.ui.multi.SearchActivity
import org.koitharu.kotatsu.settings.SettingsActivity
import org.koitharu.kotatsu.sourcerepo.ui.FirstRunHealthChecks
import org.koitharu.kotatsu.sourcerepo.ui.SourceScanManager
import javax.inject.Inject

/**
 * A guided human test workflow + machine source diagnostics.
 * Private findings are persisted locally; sharing occurs ONLY after Copy is pressed.
 */
@AndroidEntryPoint
class MonogatariTestCenterActivity : AppCompatActivity() {

    @Inject lateinit var healthChecks: FirstRunHealthChecks
    @Inject lateinit var sourceScan: SourceScanManager

    private lateinit var ui: ActivityMonogatariTestCenterBinding
    private val prefs by lazy { getSharedPreferences(TestSpotlight.PREFS_NAME, Context.MODE_PRIVATE) }
    private var index = 0
    private var rendering = false
    private var preflight = "Not run"
    private var screenAwakeForScan = false
    private var scanScreenResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMonogatariTestCenterBinding.inflate(layoutInflater)
        setContentView(ui.root)
        MonogatariChrome.applyInsets(ui.root)
        ui.qaToolbar.setNavigationIcon(android.R.drawable.ic_media_previous)
        ui.qaToolbar.setNavigationContentDescription(android.R.string.cancel)
        ui.qaToolbar.setNavigationOnClickListener { finish() }

        index = (savedInstanceState?.getInt("step")
            ?: prefs.getInt("step", 0)).coerceIn(MonogatariTestPlan.cases.indices)
        ui.qaPrevious.setOnClickListener { selectStep(index - 1) }
        ui.qaNext.setOnClickListener {
            if (index == MonogatariTestPlan.cases.lastIndex) copyReport()
            else selectStep(index + 1)
        }
        ui.qaOpenFeature.setOnClickListener { openFeature() }
        ui.qaVerdicts.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!rendering && isChecked) {
                val result = when (checkedId) {
                    R.id.qa_pass -> CheckResult.PASS
                    R.id.qa_fail -> CheckResult.FAIL
                    else -> CheckResult.UNTESTED
                }
                prefs.edit().putString("result_${currentCase().id}", result.name).apply()
                updateProgress()
            }
        }
        ui.qaFeelings.setOnCheckedStateChangeListener { _, ids ->
            if (!rendering) {
                val feeling = when (ids.firstOrNull()) {
                    R.id.qa_feel_love -> ExperienceRating.LOVE
                    R.id.qa_feel_good -> ExperienceRating.GOOD
                    R.id.qa_feel_mixed -> ExperienceRating.MIXED
                    R.id.qa_feel_needs_work -> ExperienceRating.NEEDS_WORK
                    R.id.qa_feel_not_for_me -> ExperienceRating.NOT_FOR_ME
                    else -> ExperienceRating.UNRATED
                }
                prefs.edit().putString("feeling_${currentCase().id}", feeling.name).apply()
                updateProgress()
            }
        }
        ui.qaNotes.doAfterTextChanged { editable ->
            if (!rendering) {
                prefs.edit().putString("note_${currentCase().id}", editable?.toString().orEmpty()).apply()
            }
        }
        ui.qaRerunQuick.setOnClickListener { runPreflight() }
        ui.qaRunSources.setOnClickListener { confirmFullSourceScan() }
        ui.qaStopSources.setOnClickListener { sourceScan.stop() }
        ui.qaCopyReport.setOnClickListener { copyReport() }
        ui.qaCopyFullSources.setOnClickListener {
            if (!sourceScan.hasSavedResults(true)) {
                toast("No full source diagnostic results saved yet")
            } else {
                copy("Monogatari full source diagnostics", sourceScan.report(allSources = true))
            }
        }

        selectStep(index)
        runPreflight()
        lifecycleScope.launch {
            sourceScan.status.collect { status -> renderScanStatus(status) }
        }
    }

    override fun onResume() {
        super.onResume()
        scanScreenResumed = true
        updateScanScreenAwake(sourceScan.status.value.running)
        // A verdict may have been given from the spotlight card on another screen.
        selectStep(index)
    }

    override fun onPause() {
        scanScreenResumed = false
        updateScanScreenAwake(false)
        super.onPause()
    }

    private fun updateScanScreenAwake(running: Boolean) {
        val shouldStayAwake = running && scanScreenResumed
        if (shouldStayAwake == screenAwakeForScan) return
        screenAwakeForScan = shouldStayAwake
        if (shouldStayAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("step", index)
        super.onSaveInstanceState(outState)
    }

    private fun currentCase() = MonogatariTestPlan.cases[index]

    private fun finding(test: TestCase): TestFinding = TestFinding(
        runCatching {
            CheckResult.valueOf(prefs.getString("result_${test.id}", CheckResult.UNTESTED.name)!!)
        }.getOrDefault(CheckResult.UNTESTED),
        prefs.getString("note_${test.id}", "").orEmpty(),
        runCatching {
            ExperienceRating.valueOf(prefs.getString("feeling_${test.id}", ExperienceRating.UNRATED.name)!!)
        }.getOrDefault(ExperienceRating.UNRATED),
    )

    private fun selectStep(next: Int) {
        index = next.coerceIn(MonogatariTestPlan.cases.indices)
        prefs.edit().putInt("step", index).apply()
        val test = currentCase()
        val saved = finding(test)
        rendering = true
        ui.qaStepTitle.text = "${index + 1}. ${test.title}"
        ui.qaStepInstructions.text = test.instructions
        ui.qaNotes.setText(saved.notes)
        ui.qaFeelings.check(when (saved.feeling) {
            ExperienceRating.LOVE -> R.id.qa_feel_love
            ExperienceRating.GOOD -> R.id.qa_feel_good
            ExperienceRating.MIXED -> R.id.qa_feel_mixed
            ExperienceRating.NEEDS_WORK -> R.id.qa_feel_needs_work
            ExperienceRating.NOT_FOR_ME -> R.id.qa_feel_not_for_me
            ExperienceRating.UNRATED -> { ui.qaFeelings.clearCheck(); View.NO_ID }
        })
        ui.qaVerdicts.check(
            when (saved.result) {
                CheckResult.PASS -> R.id.qa_pass
                CheckResult.FAIL -> R.id.qa_fail
                CheckResult.UNTESTED -> R.id.qa_untested
            },
        )
        ui.qaOpenFeature.text = if (test.destination == TestDestination.NONE) {
            getString(R.string.monogatari_qa_rerun_quick)
        } else {
            getString(R.string.monogatari_qa_open_feature)
        }
        ui.qaPrevious.isEnabled = index > 0
        ui.qaNext.isEnabled = true
        ui.qaNext.text = if (index == MonogatariTestPlan.cases.lastIndex) "Finish & copy" else getString(R.string.monogatari_qa_next)
        rendering = false
        updateProgress()
    }

    private fun updateProgress() {
        val cases = MonogatariTestPlan.cases
        val completed = cases.count { finding(it).result != CheckResult.UNTESTED }
        val problem = cases.count { finding(it).result == CheckResult.FAIL }
        ui.qaProgress.max = cases.size
        ui.qaProgress.progress = completed
        ui.qaProgressText.text = "Feature ${index + 1} of ${cases.size}"
        ui.qaCompletionText.text = "$completed reviewed · $problem need fixing · ${cases.size - completed} not tested"
    }

    private fun runPreflight() {
        ui.qaQuickStatus.text = getString(R.string.monogatari_qa_preflight_loading)
        lifecycleScope.launch {
            preflight = try {
                withContext(Dispatchers.IO) { healthChecks.run().summary() }
            } catch (e: Exception) {
                "Quick device checks could not finish: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(150)}"
            }
            ui.qaQuickStatus.text = preflight
        }
    }

    private fun renderScanStatus(status: SourceScanManager.Status) {
        updateScanScreenAwake(status.running)
        val count = if (status.total > 0) "${status.done} of ${status.total}" else "${status.done} checked"
        val saved = sourceScan.hasSavedResults(true)
        ui.qaScanStatus.text = when {
            status.running -> "Running ${status.mode} scan: $count. ${status.message}"
            saved -> "Saved full source results. ${sourceScan.report(true).lineSequence().take(2).joinToString(" · ")}"
            status.message.isNotBlank() -> status.message
            else -> "No full source sweep yet. This is optional and can take several hours."
        }
        ui.qaScanProgress.isIndeterminate = status.running && status.total == 0
        if (status.total > 0) {
            ui.qaScanProgress.progress = (100L * status.done / status.total).toInt().coerceIn(0, 100)
        } else if (!status.running) {
            ui.qaScanProgress.progress = 0
        }
        ui.qaRunSources.isEnabled = !status.running
        ui.qaStopSources.isEnabled = status.running
    }

    private fun confirmFullSourceScan() {
        val hasSaved = sourceScan.hasSavedResults(true)
        MaterialAlertDialogBuilder(this)
            .setTitle(if (hasSaved) "Resume source diagnostics?" else "Test every website?")
            .setMessage(
                "This may contact more than 1,500 source entries, take hours, use substantial data, " +
                "and trigger site rate limits. It runs while this app process is alive; a killed app " +
                "must be reopened to resume from the saved checkpoint. Video playback is NOT verified.",
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(if (hasSaved) "Start over" else "Source settings") { _, _ ->
                if (hasSaved) sourceScan.start(allSources = true, restart = true)
                else openFeature(TestDestination.SOURCES)
            }
            .setPositiveButton(if (hasSaved) "Resume saved" else "Begin scan") { _, _ ->
                sourceScan.start(allSources = true, restart = false)
            }
            .show()
    }

    private fun openFeature() {
        val test = currentCase()
        if (test.destination != TestDestination.NONE) {
            TestSpotlight.arm(this, test)
        }
        openFeature(test.destination)
    }

    private fun openFeature(destination: TestDestination) {
        val intent = when (destination) {
            TestDestination.NONE -> { runPreflight(); return }
            TestDestination.HOME -> Intent(this, MainActivity::class.java)
            TestDestination.BOOKS -> Intent(this, BookLibraryActivity::class.java)
            TestDestination.UNIFIED_LIBRARY -> Intent(this, UnifiedLibraryActivity::class.java)
            TestDestination.NEWS -> Intent(this, NewsFeedActivity::class.java)
            TestDestination.MEDIA_FEEDS -> Intent(this, MediaFeedActivity::class.java)
            TestDestination.SOURCES -> Intent(this, SettingsActivity::class.java)
                .setAction("org.koitharu.kotatsu.action.MONOGATARI_SOURCE_TESTS")
            TestDestination.SETTINGS -> Intent(this, SettingsActivity::class.java)
            TestDestination.LIBRARY -> Intent(this, MainActivity::class.java)
            TestDestination.DISCOVER -> Intent(this, DiscoverActivity::class.java)
            TestDestination.IMPORT -> Intent(this, ForeignBackupReviewActivity::class.java)
            TestDestination.JELLYFIN_SETUP -> Intent(this, org.koitharu.kotatsu.builtin.jellyfin.JellyfinSetupActivity::class.java)
            TestDestination.DOWNLOADS -> Intent(this, SettingsActivity::class.java)
                .setAction(AppRouter.ACTION_MANAGE_DOWNLOADS)
            TestDestination.MANGA_SEARCH, TestDestination.BOOK_SEARCH, TestDestination.ANIME_SEARCH, TestDestination.AUDIO_SEARCH ->
                Intent(this, SearchActivity::class.java)
                    .putExtra(AppRouter.KEY_QUERY, when (destination) {
                        TestDestination.MANGA_SEARCH -> "Naruto"
                        TestDestination.BOOK_SEARCH -> "Pride and Prejudice"
                        TestDestination.AUDIO_SEARCH -> "The Count of Monte Cristo"
                        else -> "BL drama"
                    })
                    .putExtra(AppRouter.KEY_KIND, SearchKind.SIMPLE)
                    .putExtra(SearchActivity.EXTRA_MEDIA_TYPE, when (destination) {
                        TestDestination.MANGA_SEARCH -> MediaType.MANGA.name
                        TestDestination.BOOK_SEARCH -> MediaType.BOOK.name
                        TestDestination.AUDIO_SEARCH -> MediaType.AUDIO.name
                        else -> MediaType.VIDEO.name
                    })
        }
        startActivity(intent)
    }

    private fun copyReport() {
        val answers = MonogatariTestPlan.cases.associate { it.id to finding(it) }
        val sourceSummary = if (sourceScan.hasSavedResults(true)) {
            val lines = sourceScan.report(true).lineSequence().toList()
            val overview = lines.take(4)
            val failures = lines.asSequence().filter { it.startsWith("FAIL |") }.take(35).toList()
            val partial = lines.asSequence().filter { it.startsWith("PARTIAL |") }.take(10).toList()
            (overview + "Sample failures:" + failures + "Sample partial results:" + partial +
                "Full source log is available with the separate Copy full source log button.").joinToString("\n")
        } else {
            "Full source scan not run yet; diagnostics are not evidence of playback success."
        }
        val report = MonogatariTestPlan.report(
            version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            preflight = preflight,
            scanSummary = sourceSummary,
            findings = answers,
        )
        copy("Monogatari testing report", report)
        MaterialAlertDialogBuilder(this)
            .setTitle("Report copied")
            .setMessage("Paste the report into your chat with ChatGPT or Claude. Your test answers and a sample of any source failures are included. Nothing was uploaded automatically.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun copy(title: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(title, value))
        toast("Copied to clipboard")
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
