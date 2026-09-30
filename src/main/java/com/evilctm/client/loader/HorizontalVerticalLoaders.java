/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.loader;

import com.evilctm.api.client.CtmLoader;
import com.evilctm.api.client.CtmLoaderRegistry;
import com.evilctm.client.processor.simple.HorizontalVerticalSpriteProvider;
import com.evilctm.client.processor.simple.SimpleQuadProcessor;
import com.evilctm.client.processor.simple.VerticalHorizontalSpriteProvider;
import com.evilctm.client.properties.BaseCtmProperties;
import com.evilctm.client.properties.OrientedConnectingCtmProperties;
import com.evilctm.client.properties.TileAmountValidator;

/** {@code horizontal+vertical}, {@code vertical+horizontal} and their {@code h+v} / {@code v+h} aliases. */
public final class HorizontalVerticalLoaders {
	private HorizontalVerticalLoaders() {
	}

	public static void register(CtmLoaderRegistry registry) {
		CtmLoader<OrientedConnectingCtmProperties> horizontalVertical = CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(OrientedConnectingCtmProperties::new), new TileAmountValidator.Exactly<>(7),
				new SimpleQuadProcessor.Factory<>(new HorizontalVerticalSpriteProvider.Factory()), true);
		registry.registerLoader("horizontal+vertical", horizontalVertical);
		registry.registerLoader("h+v", horizontalVertical);

		CtmLoader<OrientedConnectingCtmProperties> verticalHorizontal = CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(OrientedConnectingCtmProperties::new), new TileAmountValidator.Exactly<>(7),
				new SimpleQuadProcessor.Factory<>(new VerticalHorizontalSpriteProvider.Factory()), true);
		registry.registerLoader("vertical+horizontal", verticalHorizontal);
		registry.registerLoader("v+h", verticalHorizontal);
	}
}
