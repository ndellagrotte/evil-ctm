/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties;

import java.io.StringReader;
import java.util.Properties;

import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.TestPack;
import net.minecraft.util.ResourceLocation;

/** Builds and initializes {@link BaseCtmProperties} from text for the conformance tests. */
final class PropsTestSupport {
	private PropsTestSupport() {
	}

	static BaseCtmProperties parse(String path, int packPriority, String text) {
		McBootstrap.ensure();
		Properties properties = new Properties();
		try {
			properties.load(new StringReader(text));
		} catch (java.io.IOException e) {
			throw new IllegalArgumentException(e);
		}
		BaseCtmProperties props = new BaseCtmProperties(properties, new ResourceLocation("minecraft", path), new TestPack("p" + packPriority), packPriority, null, "random");
		props.init();
		return props;
	}

	static BaseCtmProperties parse(String path, String text) {
		return parse(path, 0, text);
	}

	static boolean valid(BaseCtmProperties props) {
		return props.isValid();
	}
}
