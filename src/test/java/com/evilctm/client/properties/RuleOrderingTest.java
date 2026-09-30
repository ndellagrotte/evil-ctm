/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The loader sorts in reverse order, so "greater" means applied first. */
class RuleOrderingTest {
	private static BaseCtmProperties rule(String name, int pack, String extra) {
		return PropsTestSupport.parse("optifine/ctm/" + name + ".properties", pack, "tiles=0\n" + extra);
	}

	private static void assertFirst(BaseCtmProperties winner, BaseCtmProperties loser) {
		assertTrue(winner.compareTo(loser) > 0);
		assertTrue(loser.compareTo(winner) < 0);
	}

	@Test
	void tileRuleBeatsBlockRule() {
		assertFirst(rule("a", 0, "matchTiles=stone\n"), rule("b", 0, "matchBlocks=stone\n"));
	}

	@Test
	void topPackBeatsHeavierBottomPack() {
		assertFirst(rule("a", 1, "matchBlocks=stone\n"), rule("a", 0, "matchBlocks=stone\nweight=100\n"));
	}

	@Test
	void weightBreaksTiesWithinPack() {
		assertFirst(rule("a", 0, "matchBlocks=stone\nweight=5\n"), rule("a", 0, "matchBlocks=stone\n"));
	}

	@Test
	void resourceIdBreaksRemainingTies() {
		assertFirst(rule("a", 0, "matchBlocks=stone\n"), rule("b", 0, "matchBlocks=stone\n"));
	}

	@Test
	void builtinTileRuleLosesToUserBlockRule() {
		BaseCtmProperties builtin = rule("a", 0, "matchTiles=stone\n");
		builtin.setBuiltin(true);
		assertFirst(rule("b", 0, "matchBlocks=stone\n"), builtin);
	}

	@Test
	void prioritizeOverrideIsKept() {
		assertFirst(rule("b", 0, "matchBlocks=stone\nprioritize=true\n"), rule("a", 0, "matchTiles=stone\nprioritize=false\n"));
	}

	@Test
	void reverseSortOrder() {
		List<BaseCtmProperties> list = new ArrayList<>(List.of(rule("b", 0, "matchBlocks=stone\n"), rule("a", 0, "matchBlocks=stone\n"), rule("c", 1, "matchBlocks=stone\n")));
		list.sort(Collections.reverseOrder());
		assertTrue(list.get(0).getResourceId().getPath().contains("/c."));
		assertTrue(list.get(1).getResourceId().getPath().contains("/a."));
	}
}
