/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.loader;

import com.evilctm.api.client.CachingPredicates;
import com.evilctm.api.client.CtmLoader;
import com.evilctm.api.client.CtmLoaderRegistry;
import com.evilctm.api.client.CtmProperties;
import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.processor.BaseCachingPredicates;
import com.evilctm.client.processor.TopQuadProcessor;
import com.evilctm.client.processor.simple.CtmSpriteProvider;
import com.evilctm.client.processor.simple.FixedSpriteProvider;
import com.evilctm.client.processor.simple.HorizontalSpriteProvider;
import com.evilctm.client.processor.simple.RandomSpriteProvider;
import com.evilctm.client.processor.simple.RepeatSpriteProvider;
import com.evilctm.client.processor.simple.SimpleQuadProcessor;
import com.evilctm.client.processor.simple.VerticalSpriteProvider;
import com.evilctm.client.properties.BaseCtmProperties;
import com.evilctm.client.properties.ConnectingCtmProperties;
import com.evilctm.client.properties.OrientedConnectingCtmProperties;
import com.evilctm.client.properties.RandomCtmProperties;
import com.evilctm.client.properties.RepeatCtmProperties;
import com.evilctm.client.properties.TileAmountValidator;

/** Registers the OptiFine {@code method=} loaders. */
public final class CtmLoaders {
	private static boolean registered;

	private CtmLoaders() {
	}

	/** Idempotent. */
	public static synchronized void registerAll() {
		if (registered) {
			return;
		}
		registered = true;
		CtmLoaderRegistry registry = CtmLoaderRegistry.get();

		CtmLoader<OrientedConnectingCtmProperties> ctmLoader = createLoader(
				TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(OrientedConnectingCtmProperties::new), new TileAmountValidator.AtLeast<>(47)),
				new SimpleQuadProcessor.Factory<>(new CtmSpriteProvider.Factory()), true);
		registry.registerLoader("ctm", ctmLoader);
		registry.registerLoader("glass", ctmLoader);

		CtmLoader<OrientedConnectingCtmProperties> horizontalLoader = createLoader(
				TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(OrientedConnectingCtmProperties::new), new TileAmountValidator.Exactly<>(4)),
				new SimpleQuadProcessor.Factory<>(new HorizontalSpriteProvider.Factory()), true);
		registry.registerLoader("horizontal", horizontalLoader);
		registry.registerLoader("bookshelf", horizontalLoader);

		registry.registerLoader("vertical", createLoader(
				TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(OrientedConnectingCtmProperties::new), new TileAmountValidator.Exactly<>(4)),
				new SimpleQuadProcessor.Factory<>(new VerticalSpriteProvider.Factory()), true));

		registry.registerLoader("top", createLoader(
				TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(ConnectingCtmProperties::new), new TileAmountValidator.Exactly<>(1)),
				new TopQuadProcessor.Factory(), true));

		registry.registerLoader("random", createLoader(
				BaseCtmProperties.wrapFactory(RandomCtmProperties::new),
				new SimpleQuadProcessor.Factory<>(new RandomSpriteProvider.Factory()), true));

		registry.registerLoader("repeat", createLoader(
				TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(RepeatCtmProperties::new), new RepeatCtmProperties.Validator<>()),
				new SimpleQuadProcessor.Factory<>(new RepeatSpriteProvider.Factory()), true));

		// CleanContinuity used a bare constructor here, so init() never ran and every fixed rule matched everything.
		registry.registerLoader("fixed", createLoader(
				TileAmountValidator.wrapFactory(BaseCtmProperties.wrapFactory(BaseCtmProperties::new), new TileAmountValidator.Exactly<>(1)),
				new SimpleQuadProcessor.Factory<>(new FixedSpriteProvider.Factory()), true));

		CompactLoaders.register(registry);
		HorizontalVerticalLoaders.register(registry);
		OverlayLoaders.register(registry);
	}

	public static <T extends BaseCtmProperties> CtmLoader<T> createLoader(CtmProperties.Factory<T> propertiesFactory, QuadProcessor.Factory<T> processorFactory, boolean isValidForMultipass) {
		CachingPredicates.Factory<T> predicatesFactory = new BaseCachingPredicates.Factory<>(isValidForMultipass);
		return new CtmLoader<>() {
			@Override
			public CtmProperties.Factory<T> getPropertiesFactory() {
				return propertiesFactory;
			}

			@Override
			public QuadProcessor.Factory<T> getProcessorFactory() {
				return processorFactory;
			}

			@Override
			public CachingPredicates.Factory<T> getPredicatesFactory() {
				return predicatesFactory;
			}
		};
	}
}
