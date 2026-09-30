/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.resource;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Replaces Forge's fancy missing model with the plain one. The fancy variant renders a label through the font renderer
 * on its first {@code getQuads}, which needs a GL context and so fails on Celeritas worker threads (and in model
 * probes). No model is wrapped.
 */
public class ModelEvents {
	/** Binary name of Forge's {@code FancyMissingModel.BakedModel} (package-private class). */
	private static final String FANCY_MISSING_MODEL_CLASS = "net.minecraftforge.client.model.FancyMissingModel$BakedModel";

	@SubscribeEvent
	public void onModelBake(ModelBakeEvent event) {
		IRegistry<ModelResourceLocation, IBakedModel> registry = event.getModelRegistry();
		IBakedModel missingModel = event.getModelManager().getMissingModel();
		for (ModelResourceLocation location : registry.getKeys()) {
			IBakedModel model = registry.getObject(location);
			if (model != null && FANCY_MISSING_MODEL_CLASS.equals(model.getClass().getName())) {
				registry.putObject(location, missingModel);
			}
		}
	}
}
