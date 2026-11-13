package org.koitharu.kotatsu.search.ui.multi

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.search.ui.multi.SearchPaneArrangement.LIST_ONLY
import org.koitharu.kotatsu.search.ui.multi.SearchPaneArrangement.PREVIEW_BELOW
import org.koitharu.kotatsu.search.ui.multi.SearchPaneArrangement.PREVIEW_SIDE

class SearchPaneLayoutPolicyTest {

	private fun arr(w: Int, h: Int, mode: SearchPaneMode = SearchPaneMode.AUTO, collapsed: Boolean = false) =
		SearchPaneLayoutPolicy.arrangement(w, h, mode, collapsed)

	@Test fun phonesStaySinglePane() {
		assertEquals(LIST_ONLY, arr(360, 800))
		assertEquals(LIST_ONLY, arr(800, 360))
		assertEquals(LIST_ONLY, arr(599, 900))
	}

	@Test fun tabletPortraitStacksPreviewBelow() {
		for ((w, h) in listOf(600 to 960, 800 to 1280, 840 to 1200, 1000 to 1600)) {
			assertEquals("$w x $h", PREVIEW_BELOW, arr(w, h))
		}
	}

	@Test fun tabletLandscapeUsesSidePreview() {
		for ((w, h) in listOf(960 to 600, 1280 to 800, 1600 to 1000, 1200 to 840)) {
			assertEquals("$w x $h", PREVIEW_SIDE, arr(w, h))
		}
	}

	@Test fun resizedWindowsFollowTheirOwnShapeNotTheDevice() {
		assertEquals(PREVIEW_BELOW, arr(700, 900))
		assertEquals(LIST_ONLY, arr(500, 900))
		assertEquals(PREVIEW_BELOW, arr(650, 500))
		assertEquals(LIST_ONLY, arr(650, 400))
	}

	@Test fun squareFoldablesPickByAspect() {
		assertEquals(PREVIEW_SIDE, arr(900, 880))
		assertEquals(PREVIEW_BELOW, arr(900, 901))
	}

	@Test fun listOnlyPreferenceAndCollapseAlwaysWin() {
		assertEquals(LIST_ONLY, arr(1280, 800, SearchPaneMode.LIST_ONLY))
		assertEquals(LIST_ONLY, arr(800, 1280, collapsed = true))
	}

	@Test fun listAndPreviewStillNeedRoomForTwoPanes() {
		assertEquals(LIST_ONLY, arr(400, 800, SearchPaneMode.LIST_AND_PREVIEW))
		assertEquals(PREVIEW_BELOW, arr(800, 1280, SearchPaneMode.LIST_AND_PREVIEW))
	}

	@Test fun fractionIsClampedAndNanSafe() {
		assertEquals(0.25f, SearchPaneLayoutPolicy.clampFraction(0.01f), 0f)
		assertEquals(0.75f, SearchPaneLayoutPolicy.clampFraction(0.99f), 0f)
		assertEquals(0.5f, SearchPaneLayoutPolicy.clampFraction(0.5f), 0f)
		assertEquals(SearchPaneLayoutPolicy.DEFAULT_SIDE_FRACTION, SearchPaneLayoutPolicy.clampFraction(Float.NaN), 0f)
	}

	@Test fun unknownModeNameFallsBackToAuto() {
		assertEquals(SearchPaneMode.AUTO, SearchPaneMode.fromName("nope"))
		assertEquals(SearchPaneMode.LIST_ONLY, SearchPaneMode.fromName("LIST_ONLY"))
	}
}
