package org.koitharu.kotatsu.reader.translate

import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel

/** The offline translation language packs ML Kit has downloaded to this device (#27). */
internal object TranslationModels {

	private val manager get() = RemoteModelManager.getInstance()

	/** Language tags of the downloaded packs, sorted. */
	suspend fun downloaded(): List<String> =
		manager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.sorted()

	suspend fun delete(language: String) {
		manager.deleteDownloadedModel(TranslateRemoteModel.Builder(language).build()).await()
	}
}
