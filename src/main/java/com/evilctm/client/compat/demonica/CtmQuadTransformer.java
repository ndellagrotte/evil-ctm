/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.compat.demonica;

import java.util.List;

import com.demonica.api.render.terrain.BlockQuadTransformer;
import com.demonica.api.render.terrain.BlockQuadTransformerHolder;
import com.evilctm.client.pipeline.QuadPipeline;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess;

/**
 * The Demonica S20 hook, and the only class that references {@link CeleritasBlockAccess}. Called on Celeritas chunk
 * builder threads; the pipeline never throws.
 */
public final class CtmQuadTransformer implements BlockQuadTransformer {
	public static final CtmQuadTransformer INSTANCE = new CtmQuadTransformer();

	private static boolean registered;

	private CtmQuadTransformer() {
	}

	public static synchronized void registerOnce() {
		if (!registered) {
			BlockQuadTransformerHolder.register(INSTANCE);
			registered = true;
		}
	}

	@Override
	public List<BakedQuad> transform(IBlockState state, BlockPos pos, CeleritasBlockAccess blockAccess, BlockRenderLayer layer, EnumFacing side, List<BakedQuad> quads) {
		RenderPathStatus.markInvoked();
		return QuadPipeline.transformSafely(state, pos, blockAccess, layer, side, quads);
	}
}
