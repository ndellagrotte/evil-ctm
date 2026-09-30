/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.mixin;

import com.evilctm.client.layer.LayerRouter;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds extra render layers (overlays, CTM-mod layers, emissive) for blocks Celeritas meshes on the fast path, so S20
 * sees the block in those layers. Not a render hook: it only widens the layer set.
 */
@Mixin(Block.class)
public abstract class BlockRenderLayerMixin {
	@Inject(method = "canRenderInLayer", at = @At("RETURN"), cancellable = true, remap = false)
	private void evilctm$allowExtraLayer(IBlockState state, BlockRenderLayer layer, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && LayerRouter.extraPossible && LayerRouter.allowExtraLayer(state, layer)) {
			cir.setReturnValue(true);
		}
	}
}
