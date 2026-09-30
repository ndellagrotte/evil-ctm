/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The screen must fit 854x480 at GUI scale 2 (240 px high) without the status text covering a toggle. */
class EvilCtmConfigScreenLayoutTest {
	private static final int HEIGHT = 240;
	private static final int TOGGLES = 6;

	private static void assertFits(EvilCtmConfigScreen.Layout layout, int statusLines) {
		assertTrue(layout.top() >= 42 + statusLines * 10, "status text overlaps the first toggle");
		assertTrue(layout.doneY() + 20 <= HEIGHT - 4, "Done is off screen: " + layout);
		assertTrue(layout.pitch() >= 21, "buttons overlap: " + layout);
	}

	@Test
	void okStatusFitsInOneColumn() {
		EvilCtmConfigScreen.Layout layout = EvilCtmConfigScreen.layout(HEIGHT, 1, TOGGLES);
		assertFits(layout, 1);
		assertFalse(layout.twoColumns());
	}

	@Test
	void longWarningFallsBackToTwoColumns() {
		EvilCtmConfigScreen.Layout layout = EvilCtmConfigScreen.layout(HEIGHT, 5, TOGGLES);
		assertFits(layout, 5);
		assertTrue(layout.twoColumns());
	}
}
