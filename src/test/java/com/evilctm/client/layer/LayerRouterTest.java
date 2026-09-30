/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.evilctm.api.client.LayerTargetingProcessor;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestProcessors;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LayerRouterTest {
	private FakeGate gate;
	private TextureAtlasSprite overlaySource;

	/** Mimics a block patched by BlockRenderLayerMixin. */
	static class MixedInBlock extends Block {
		MixedInBlock() {
			super(Material.ROCK);
		}

		@Override
		public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
			return super.canRenderInLayer(state, layer) || LayerRouter.extraPossible && LayerRouter.allowExtraLayer(state, layer);
		}
	}

	static class SingleQuadModel implements IBakedModel {
		private final List<BakedQuad> quads;

		SingleQuadModel(BakedQuad quad) {
			this.quads = List.of(quad);
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
			return side == null ? quads : Collections.emptyList();
		}

		@Override
		public boolean isAmbientOcclusion() {
			return true;
		}

		@Override
		public boolean isGui3d() {
			return true;
		}

		@Override
		public boolean isBuiltInRenderer() {
			return false;
		}

		@Override
		public TextureAtlasSprite getParticleTexture() {
			return quads.get(0).getSprite();
		}

		@Override
		public ItemOverrideList getOverrides() {
			return ItemOverrideList.NONE;
		}
	}

	@BeforeEach
	void setUp() {
		McBootstrap.ensure();
		gate = new FakeGate();
		LayerRouter.setGateForTests(gate);
		overlaySource = TestSprites.create("test:overlay_source");
		IBakedModel model = new SingleQuadModel(TestQuads.fullFace(EnumFacing.UP, overlaySource, -1));
		ModelProbe.setModelLookupForTests(state -> model);
		LayerTargetingProcessor overlay = new LayerTargetingProcessor() {
			@Override
			public BlockRenderLayer getTargetLayer() {
				return BlockRenderLayer.TRANSLUCENT;
			}

			@Override
			public ProcessingResult processQuad(BakedQuad quad, TextureAtlasSprite sprite, net.minecraft.world.IBlockAccess level, net.minecraft.util.math.BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, int pass, ProcessingContext context) {
				return ProcessingResult.NEXT_PROCESSOR;
			}
		};
		QuadProcessors.reload(List.of(TestProcessors.holder(overlay, TestProcessors.sprites(true, overlaySource))));
		LayerRouter.refreshActive();
	}

	@AfterEach
	void tearDown() {
		ModelProbe.setModelLookupForTests(null);
		PipelineHarness.reset();
	}

	@Test
	void fastPathOffGrantsNothing() {
		gate.fastPath(false);
		assertFalse(LayerRouter.allowExtraLayer(Blocks.STONE.getDefaultState(), BlockRenderLayer.TRANSLUCENT));
	}

	@Test
	void layerTargetingProcessorAddsOnlyItsTargetLayer() {
		IBlockState stone = Blocks.STONE.getDefaultState();
		assertTrue(LayerRouter.extraPossible);
		assertTrue(LayerRouter.allowExtraLayer(stone, BlockRenderLayer.TRANSLUCENT));
		assertFalse(LayerRouter.allowExtraLayer(stone, BlockRenderLayer.CUTOUT));
		assertFalse(LayerRouter.allowExtraLayer(stone, BlockRenderLayer.CUTOUT_MIPPED));
		assertFalse(LayerRouter.allowExtraLayer(stone, BlockRenderLayer.SOLID));
		assertEquals(LayerRouter.bit(BlockRenderLayer.TRANSLUCENT), LayerRouter.extraMask(stone));
	}

	@Test
	void forcedVanillaBlocksGetNothing() {
		gate.forceVanilla(Blocks.STONE);
		assertFalse(LayerRouter.allowExtraLayer(Blocks.STONE.getDefaultState(), BlockRenderLayer.TRANSLUCENT));
	}

	@Test
	void nonModelRenderTypesGetNothing() {
		assertFalse(LayerRouter.allowExtraLayer(Blocks.WATER.getDefaultState(), BlockRenderLayer.TRANSLUCENT));
		assertFalse(LayerRouter.allowExtraLayer(Blocks.WATER.getDefaultState(), BlockRenderLayer.SOLID));
	}

	@Test
	void nativeMaskBypassesTheMixinWithoutRecursing() {
		MixedInBlock block = new MixedInBlock();
		IBlockState state = block.getDefaultState();

		assertTrue(block.canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT));
		assertTrue(LayerRouter.isNativeLayer(state, BlockRenderLayer.SOLID));
		assertFalse(LayerRouter.isNativeLayer(state, BlockRenderLayer.TRANSLUCENT));
		assertEquals(LayerRouter.bit(BlockRenderLayer.SOLID), LayerRouter.nativeMask(state));
	}

	@Test
	void shaderOverrideLayerCountsAsNative() {
		gate.override(Blocks.GLASS, BlockRenderLayer.TRANSLUCENT);
		assertTrue(LayerRouter.isNativeLayer(Blocks.GLASS.getDefaultState(), BlockRenderLayer.TRANSLUCENT));
		assertTrue(LayerRouter.isNativeLayer(Blocks.GLASS.getDefaultState(), BlockRenderLayer.CUTOUT));
		assertFalse(LayerRouter.isNativeLayer(Blocks.GLASS.getDefaultState(), BlockRenderLayer.SOLID));
	}
}
