/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.loader;

import com.evilctm.api.client.CtmLoaderRegistry;
import com.evilctm.client.processor.CompactCtmQuadProcessor;
import com.evilctm.client.properties.BaseCtmProperties;
import com.evilctm.client.properties.CompactConnectingCtmProperties;
import com.evilctm.client.properties.TileAmountValidator;

/** {@code method=ctm_compact}. */
public final class CompactLoaders {
	private CompactLoaders() {
	}

	public static void register(CtmLoaderRegistry registry) {
		registry.registerLoader("ctm_compact", CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(CompactConnectingCtmProperties::new), new TileAmountValidator.AtLeast<>(5),
				new CompactCtmQuadProcessor.Factory(), false));
	}
}
