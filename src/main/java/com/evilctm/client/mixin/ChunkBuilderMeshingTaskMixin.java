/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.mixin;

import com.evilctm.client.layer.LayerRouter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;

/**
 * Adds extra render layers (overlays, CTM-mod layers, emissive) at Celeritas' per-block {@code canRenderInLayer}
 * call, so S20 sees the block in those layers. Not a render hook: it only widens the layer set of the fast-path mesher.
 * Wrapping the call site (not {@code Block.canRenderInLayer}) covers blocks that override that method, knows the
 * position (Demonica sends some positions to vanilla's renderer) and leaves every other caller untouched.
 * <p>The priority is below Demonica's (1100), so Demonica's wrappers of the same call (shader pack layer, hidden
 * blocks) are applied later, sit outside this one and keep the final say.</p>
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false, priority = 900)
public abstract class ChunkBuilderMeshingTaskMixin {
	@Unique
	private static final String EXECUTE = "execute(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;"
			+ "Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;";

	@WrapOperation(method = EXECUTE, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/Block;canRenderInLayer(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockRenderLayer;)Z"))
	private boolean evilctm$allowExtraLayer(Block block, IBlockState state, BlockRenderLayer layer, Operation<Boolean> original,
											@Local BlockPos.MutableBlockPos pos) {
		if (original.call(block, state, layer)) {
			return true;
		}
		return LayerRouter.extraPossible && LayerRouter.allowExtraLayer(state, layer, pos);
	}
}
