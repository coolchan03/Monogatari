package org.koitharu.kotatsu.search.ui.multi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.core.media.MediaType

class SearchPreviewActionsTest {

	@Test fun mangaGetsReadDownloadAndShare() {
		val actions = SearchPreviewActions.forTitle(MediaType.MANGA, hasAlternatives = false, hasWebUrl = true)
		assertTrue(PreviewAction.DOWNLOAD in actions)
		assertTrue(PreviewAction.BROWSER in actions)
		assertFalse(PreviewAction.CHOOSE_SOURCE in actions)
		assertEquals(PreviewAction.PRIMARY, actions.first())
	}

	@Test fun streamedMediaNeverOffersChapterDownload() {
		for (media in listOf(MediaType.VIDEO, MediaType.AUDIO)) {
			assertFalse(PreviewAction.DOWNLOAD in SearchPreviewActions.forTitle(media, false, true))
		}
	}

	@Test fun sourceChooserOnlyWithRealAlternatives() {
		assertTrue(PreviewAction.CHOOSE_SOURCE in SearchPreviewActions.forTitle(MediaType.MANGA, true, true))
		assertFalse(PreviewAction.CHOOSE_SOURCE in SearchPreviewActions.forTitle(MediaType.MANGA, false, true))
	}

	@Test fun browserHiddenWithoutUrl() {
		assertFalse(PreviewAction.BROWSER in SearchPreviewActions.forTitle(MediaType.MANGA, false, false))
	}

	@Test fun primaryActionMatchesMediaAndProgress() {
		assertEquals(PrimaryActionKind.READ, SearchPreviewActions.primaryKind(MediaType.MANGA, false))
		assertEquals(PrimaryActionKind.CONTINUE, SearchPreviewActions.primaryKind(MediaType.BOOK, true))
		assertEquals(PrimaryActionKind.WATCH, SearchPreviewActions.primaryKind(MediaType.VIDEO, false))
		assertEquals(PrimaryActionKind.CONTINUE_LISTENING, SearchPreviewActions.primaryKind(MediaType.AUDIO, true))
	}

	@Test fun factsDropBlankAndDuplicatePartsWithoutFiller() {
		assertEquals("Ongoing · English · ★ 4.2",
			SearchPreviewActions.joinFacts(listOf("Ongoing", null, " ", "English", "ongoing", "★ 4.2")))
		assertEquals("", SearchPreviewActions.joinFacts(listOf(null, "")))
	}
}
