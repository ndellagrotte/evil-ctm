/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor.simple;

import com.evilctm.client.processor.ConnectionPredicate;
import com.evilctm.impl.client.ProcessingContextImpl;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Shared helpers for the sprite provider conformance tests. */
final class SpriteProviderTestSupport {
	static final IBlockState STONE = Blocks.STONE.getDefaultState();

	private SpriteProviderTestSupport() {
	}

	/** Connects to any neighbour whose block is {@code block}. */
	static ConnectionPredicate connectTo(Block block) {
		return (level, pos, appearanceState, state, otherPos, otherAppearanceState, otherState, face, quadSprite) -> otherState.getBlock() == block;
	}

	static TextureAtlasSprite[] sprites(String prefix, int count) {
		TextureAtlasSprite[] sprites = new TextureAtlasSprite[count];
		for (int i = 0; i < count; i++) {
			sprites[i] = TestSprites.create("test:" + prefix + "_" + i);
		}
		return sprites;
	}

	static BakedQuad quad(EnumFacing face) {
		return TestQuads.fullFace(face, TestSprites.create("test:base_" + face), -1);
	}

	static int indexOf(TextureAtlasSprite[] sprites, TextureAtlasSprite sprite) {
		for (int i = 0; i < sprites.length; i++) {
			if (sprites[i] == sprite) {
				return i;
			}
		}
		return -1;
	}

	static TextureAtlasSprite run(SpriteProvider provider, BakedQuad quad, com.evilctm.testutil.FakeBlockAccess access, BlockPos pos) {
		ProcessingContextImpl context = new ProcessingContextImpl();
		try {
			return provider.getSprite(quad, quad.getSprite(), access, pos, STONE, STONE, 0L, context);
		} finally {
			context.end();
		}
	}
}
