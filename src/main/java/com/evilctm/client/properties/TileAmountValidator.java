/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.properties;

import com.evilctm.api.client.CtmProperties;
import com.evilctm.client.EvilCtmClient;

public interface TileAmountValidator<T extends BaseCtmProperties> {
	boolean validateTileAmount(int amount, T properties);

	static <T extends BaseCtmProperties> CtmProperties.Factory<T> wrapFactory(CtmProperties.Factory<T> factory, TileAmountValidator<T> validator) {
		return (properties, resourceId, pack, packPriority, resourceManager, method) -> {
			T ctmProperties = factory.createProperties(properties, resourceId, pack, packPriority, resourceManager, method);
			if (ctmProperties == null) {
				return null;
			}
			if (validator.validateTileAmount(ctmProperties.getSpriteIds().size(), ctmProperties)) {
				return ctmProperties;
			}
			return null;
		};
	}

	class Exactly<T extends BaseCtmProperties> implements TileAmountValidator<T> {
		protected final int targetAmount;

		public Exactly(int targetAmount) {
			this.targetAmount = targetAmount;
		}

		@Override
		public boolean validateTileAmount(int amount, T properties) {
			if (amount == targetAmount) {
				return true;
			}
			EvilCtmClient.LOGGER.warn("Method '" + properties.getMethod() + "' requires exactly " + targetAmount + " tiles but " + amount + " were provided in file '" + properties.getResourceId() + "' in pack '" + properties.getPackId() + "'");
			return false;
		}
	}

	class AtLeast<T extends BaseCtmProperties> implements TileAmountValidator<T> {
		protected final int targetAmount;

		public AtLeast(int targetAmount) {
			this.targetAmount = targetAmount;
		}

		@Override
		public boolean validateTileAmount(int amount, T properties) {
			if (amount >= targetAmount) {
				return true;
			}
			EvilCtmClient.LOGGER.warn("Method '" + properties.getMethod() + "' requires at least " + targetAmount + " tiles but only " + amount + " were provided in file '" + properties.getResourceId() + "' in pack '" + properties.getPackId() + "'");
			return false;
		}
	}
}
