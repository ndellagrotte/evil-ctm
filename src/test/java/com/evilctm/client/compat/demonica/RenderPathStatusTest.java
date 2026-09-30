/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.compat.demonica;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** Each render path problem has its own message and its own remedy; only FAST_RENDERER_OFF points at the option. */
class RenderPathStatusTest {
	private static Properties lang() throws Exception {
		Properties lang = new Properties();
		try (InputStream in = RenderPathStatusTest.class.getResourceAsStream("/assets/evilctm/lang/en_us.lang")) {
			lang.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		return lang;
	}

	@Test
	void everyProblemHasAMessageAndItsOwnHint() throws Exception {
		Properties lang = lang();
		for (RenderPathStatus.Problem problem : RenderPathStatus.Problem.values()) {
			if (problem == RenderPathStatus.Problem.OK) {
				continue;
			}
			assertTrue(lang.containsKey(problem.translationKey()), problem.translationKey());
			String hint = lang.getProperty(problem.hintKey());
			assertTrue(hint != null, problem.hintKey());
			assertFalse(problem.remedy().isEmpty(), problem.name());
			boolean mentionsOption = hint.contains("Fast Block Renderer") || problem.remedy().contains("Fast Block Renderer");
			assertTrue(mentionsOption == (problem == RenderPathStatus.Problem.FAST_RENDERER_OFF), problem.name());
		}
	}
}
