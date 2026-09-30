/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.overlay;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import com.evilctm.api.client.LayerTargetingProcessor;
import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.processor.ProcessingPredicate;
import com.evilctm.client.processor.simple.SimpleQuadProcessor;
import com.evilctm.client.processor.simple.SpriteProvider;
import com.evilctm.client.properties.BaseCtmProperties;
import com.evilctm.client.properties.overlay.OverlayPropertiesSection;
import com.evilctm.client.util.OverlayQuads;
import com.evilctm.client.util.RenderUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * An overlay whose tile comes from a {@link SpriteProvider} (overlay_ctm, overlay_random, overlay_fixed, ...). The
 * tile is emitted as a full-face quad into the rule's layer; the quad being processed is left alone.
 */
public class SimpleOverlayQuadProcessor extends SimpleQuadProcessor implements LayerTargetingProcessor {
	protected final int tintIndex;
	@Nullable
	protected final IBlockState tintBlock;
	protected final BlockRenderLayer layer;
	/** Baked full-face quads per tile sprite, indexed by face. */
	protected final Reference2ObjectOpenHashMap<TextureAtlasSprite, BakedQuad[]> faceQuads;

	public SimpleOverlayQuadProcessor(SpriteProvider spriteProvider, ProcessingPredicate processingPredicate, TextureAtlasSprite[] sprites, int tintIndex, @Nullable IBlockState tintBlock, BlockRenderLayer layer) {
		super(spriteProvider, processingPredicate);
		this.tintIndex = tintIndex;
		this.tintBlock = tintBlock;
		this.layer = layer;
		this.faceQuads = new Reference2ObjectOpenHashMap<>(sprites.length);
		for (TextureAtlasSprite sprite : sprites) {
			if (sprite != null && !RenderUtil.isMissingSprite(sprite) && !faceQuads.containsKey(sprite)) {
				faceQuads.put(sprite, OverlayQuads.faces(sprite));
			}
		}
		faceQuads.trim();
	}

	@Override
	public BlockRenderLayer getTargetLayer() {
		return layer;
	}

	@Override
	public ProcessingResult processQuad(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, int pass, ProcessingContext context) {
		if (context.currentLayer() != layer) {
			return ProcessingResult.NEXT_PROCESSOR;
		}
		if (!processingPredicate.shouldProcessQuad(quad, sprite, level, pos, appearanceState, state, context)) {
			return ProcessingResult.NEXT_PROCESSOR;
		}
		TextureAtlasSprite newSprite = spriteProvider.getSprite(quad, sprite, level, pos, appearanceState, state, rand, context);
		if (newSprite != null && !RenderUtil.isMissingSprite(newSprite)) {
			EnumFacing face = quad.getFace();
			BakedQuad base = faceQuad(newSprite, face);
			context.emitOverlay(layer, OverlayQuads.tinted(base, tintBlock, tintIndex, level, pos));
		}
		return ProcessingResult.NEXT_PROCESSOR;
	}

	protected BakedQuad faceQuad(TextureAtlasSprite sprite, EnumFacing face) {
		BakedQuad[] quads = faceQuads.get(sprite);
		if (quads != null) {
			return quads[face.getIndex()];
		}
		return OverlayQuads.quad(face, sprite, -1, null);
	}

	public static class Factory<T extends BaseCtmProperties & OverlayPropertiesSection.Provider> extends SimpleQuadProcessor.Factory<T> {
		public Factory(SpriteProvider.Factory<? super T> spriteProviderFactory) {
			super(spriteProviderFactory);
		}

		@Override
		public QuadProcessor createProcessor(T properties, TextureAtlasSprite[] sprites) {
			OverlayPropertiesSection overlaySection = properties.getOverlayPropertiesSection();
			return new SimpleOverlayQuadProcessor(spriteProviderFactory.createSpriteProvider(sprites, properties), OverlayProcessingPredicate.fromProperties(properties), sprites, overlaySection.getTintIndex(), overlaySection.getTintBlock(), overlaySection.getLayer());
		}

		@Override
		public boolean supportsNullSprites(T properties) {
			return false;
		}
	}
}
