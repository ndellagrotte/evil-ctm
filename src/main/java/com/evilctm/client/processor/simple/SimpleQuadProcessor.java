/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import javax.annotation.Nullable;

import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.processor.AbstractQuadProcessorFactory;
import com.evilctm.client.processor.BaseProcessingPredicate;
import com.evilctm.client.processor.ProcessingPredicate;
import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.properties.BaseCtmProperties;
import com.evilctm.client.util.QuadUtil;
import com.evilctm.client.util.RenderUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class SimpleQuadProcessor implements QuadProcessor {
	protected SpriteProvider spriteProvider;
	protected ProcessingPredicate processingPredicate;

	public SimpleQuadProcessor(SpriteProvider spriteProvider, ProcessingPredicate processingPredicate) {
		this.spriteProvider = spriteProvider;
		this.processingPredicate = processingPredicate;
	}

	@Override
	public ProcessingResult processQuad(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, int pass, ProcessingContext context) {
		if (!processingPredicate.shouldProcessQuad(quad, sprite, level, pos, appearanceState, state, context)) {
			return ProcessingResult.NEXT_PROCESSOR;
		}
		TextureAtlasSprite newSprite = spriteProvider.getSprite(quad, sprite, level, pos, appearanceState, state, rand, context);
		return process(quad, sprite, newSprite, context);
	}

	public static ProcessingResult process(BakedQuad quad, TextureAtlasSprite oldSprite, @Nullable TextureAtlasSprite newSprite, ProcessingContext context) {
		if (newSprite == null) {
			return ProcessingResult.STOP;
		}
		if (RenderUtil.isMissingSprite(newSprite)) {
			EvilCtmClient.LOGGER.debug("Skipping CTM replacement for '{}' because replacement sprite '{}' is missing", oldSprite.getIconName(), newSprite.getIconName());
			return ProcessingResult.NEXT_PROCESSOR;
		}
		context.replaceQuad(QuadUtil.retexture(quad, newSprite));
		return ProcessingResult.NEXT_PASS;
	}

	public static class Factory<T extends BaseCtmProperties> extends AbstractQuadProcessorFactory<T> {
		protected SpriteProvider.Factory<? super T> spriteProviderFactory;

		public Factory(SpriteProvider.Factory<? super T> spriteProviderFactory) {
			this.spriteProviderFactory = spriteProviderFactory;
		}

		@Override
		public QuadProcessor createProcessor(T properties, TextureAtlasSprite[] sprites) {
			return new SimpleQuadProcessor(spriteProviderFactory.createSpriteProvider(sprites, properties), BaseProcessingPredicate.fromProperties(properties));
		}

		@Override
		public int getSpriteAmount(T properties) {
			return spriteProviderFactory.getSpriteAmount(properties);
		}
	}
}
