/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import com.evilctm.client.layer.LayerRouter;
import com.evilctm.client.model.ReloadEpoch;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

/**
 * Registered after the model manager's listener, so it runs once the new models are visible through
 * {@code getModelForState}; bumps {@link ReloadEpoch} so model-derived caches recompute.
 */
public class ModelReloadListener implements IResourceManagerReloadListener {
	@Override
	public void onResourceManagerReload(IResourceManager resourceManager) {
		ReloadEpoch.bump();
		LayerRouter.refreshActive();
	}
}
