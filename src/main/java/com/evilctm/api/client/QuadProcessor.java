/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.api.client;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * One CTM rule applied to one quad.
 *
 * <p>Contract (see {@code ProcessingChain}):</p>
 * <ul>
 *     <li>To retexture: {@link ProcessingContext#replaceQuad} with the new quad and return {@link ProcessingResult#NEXT_PASS}.</li>
 *     <li>To split a quad: add the pieces to {@link ProcessingContext#getExtraQuads()} and return {@link ProcessingResult#DISCARD}.</li>
 *     <li>To overlay: {@link ProcessingContext#emitOverlay} and return {@link ProcessingResult#NEXT_PROCESSOR}.</li>
 *     <li>Never clear {@link ProcessingContext#getExtraQuads()} and never construct {@code BakedQuadRetextured}.</li>
 * </ul>
 */
public interface QuadProcessor {
	ProcessingResult processQuad(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, int pass, ProcessingContext context);

	interface ProcessingContext extends ProcessingDataProvider {
		/**
		 * Final extra quads. They are emitted after the (possibly replaced) quad being processed, or instead of it when
		 * the processor returns {@link ProcessingResult#DISCARD}, and are never processed again. Processors only add.
		 */
		List<BakedQuad> getExtraQuads();

		/** Replaces the quad being processed; later passes see the replacement. The last call before returning wins. */
		void replaceQuad(BakedQuad quad);

		/** Emits an overlay quad into {@code layer}. It is kept only when that layer is the one being built. */
		void emitOverlay(BlockRenderLayer layer, BakedQuad quad);

		/** The render layer being built, or {@code null} outside the render pipeline. */
		@Nullable
		BlockRenderLayer currentLayer();
	}

	enum ProcessingResult {
		NEXT_PROCESSOR,
		NEXT_PASS,
		STOP,
		DISCARD;
	}

	interface Factory<T extends CtmProperties> {
		QuadProcessor createProcessor(T properties, Function<ResourceLocation, TextureAtlasSprite> spriteGetter);
	}
}
