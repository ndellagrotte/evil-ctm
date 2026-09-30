/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.loader;

import com.evilctm.api.client.CtmLoader;
import com.evilctm.api.client.CtmLoaderRegistry;
import com.evilctm.client.processor.overlay.SimpleOverlayQuadProcessor;
import com.evilctm.client.processor.overlay.StandardOverlayQuadProcessor;
import com.evilctm.client.processor.simple.CtmSpriteProvider;
import com.evilctm.client.processor.simple.FixedSpriteProvider;
import com.evilctm.client.processor.simple.HorizontalSpriteProvider;
import com.evilctm.client.processor.simple.HorizontalVerticalSpriteProvider;
import com.evilctm.client.processor.simple.RandomSpriteProvider;
import com.evilctm.client.processor.simple.RepeatSpriteProvider;
import com.evilctm.client.processor.simple.VerticalHorizontalSpriteProvider;
import com.evilctm.client.processor.simple.VerticalSpriteProvider;
import com.evilctm.client.properties.BaseCtmProperties;
import com.evilctm.client.properties.RepeatCtmProperties;
import com.evilctm.client.properties.TileAmountValidator;
import com.evilctm.client.properties.overlay.BaseOverlayCtmProperties;
import com.evilctm.client.properties.overlay.OrientedConnectingOverlayCtmProperties;
import com.evilctm.client.properties.overlay.RandomOverlayCtmProperties;
import com.evilctm.client.properties.overlay.RepeatOverlayCtmProperties;
import com.evilctm.client.properties.overlay.StandardOverlayCtmProperties;

/** The {@code overlay*} methods. Overlays emit into their own layer and never replace the quad they sit on. */
public final class OverlayLoaders {
	private OverlayLoaders() {
	}

	public static void register(CtmLoaderRegistry registry) {
		StandardOverlayQuadProcessor.init();

		registry.registerLoader("overlay", CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(StandardOverlayCtmProperties::new), new TileAmountValidator.AtLeast<>(17),
				new StandardOverlayQuadProcessor.Factory(), true));

		registry.registerLoader("overlay_ctm", CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(OrientedConnectingOverlayCtmProperties::new), new TileAmountValidator.AtLeast<>(47),
				new SimpleOverlayQuadProcessor.Factory<>(new CtmSpriteProvider.Factory()), true));

		registry.registerLoader("overlay_random", CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(RandomOverlayCtmProperties::new),
				new SimpleOverlayQuadProcessor.Factory<>(new RandomSpriteProvider.Factory()), true));

		registry.registerLoader("overlay_repeat", CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(RepeatOverlayCtmProperties::new), new RepeatCtmProperties.Validator<>(),
				new SimpleOverlayQuadProcessor.Factory<>(new RepeatSpriteProvider.Factory()), true));

		registry.registerLoader("overlay_fixed", CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(BaseOverlayCtmProperties::new), new TileAmountValidator.Exactly<>(1),
				new SimpleOverlayQuadProcessor.Factory<>(new FixedSpriteProvider.Factory()), true));

		registry.registerLoader("overlay_horizontal", CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(OrientedConnectingOverlayCtmProperties::new), new TileAmountValidator.Exactly<>(4),
				new SimpleOverlayQuadProcessor.Factory<>(new HorizontalSpriteProvider.Factory()), true));

		registry.registerLoader("overlay_vertical", CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(OrientedConnectingOverlayCtmProperties::new), new TileAmountValidator.Exactly<>(4),
				new SimpleOverlayQuadProcessor.Factory<>(new VerticalSpriteProvider.Factory()), true));

		CtmLoader<OrientedConnectingOverlayCtmProperties> horizontalVertical = CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(OrientedConnectingOverlayCtmProperties::new), new TileAmountValidator.Exactly<>(7),
				new SimpleOverlayQuadProcessor.Factory<>(new HorizontalVerticalSpriteProvider.Factory()), true);
		registry.registerLoader("overlay_horizontal+vertical", horizontalVertical);
		registry.registerLoader("overlay_h+v", horizontalVertical);

		CtmLoader<OrientedConnectingOverlayCtmProperties> verticalHorizontal = CtmLoaders.createLoader(
				BaseCtmProperties.wrapFactory(OrientedConnectingOverlayCtmProperties::new), new TileAmountValidator.Exactly<>(7),
				new SimpleOverlayQuadProcessor.Factory<>(new VerticalHorizontalSpriteProvider.Factory()), true);
		registry.registerLoader("overlay_vertical+horizontal", verticalHorizontal);
		registry.registerLoader("overlay_v+h", verticalHorizontal);
	}
}
