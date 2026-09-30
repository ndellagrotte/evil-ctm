/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.evilctm.api.client.LayerTargetingProcessor;
import com.evilctm.client.compat.demonica.RenderPathStatus;
import com.evilctm.client.model.QuadProcessors;
import com.evilctm.client.pipeline.QuadPipeline;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestProcessors;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.WeightedBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LayerRouterTest {
	private FakeGate gate;
	private TextureAtlasSprite overlaySource;

	/** A block whose own canRenderInLayer asks the router (must not recurse or count the answer as native). */
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

	/** Overrides canRenderInLayer without calling super, with a layer that can change at runtime (like leaves). */
	static class OverridingBlock extends Block {
		volatile BlockRenderLayer layer = BlockRenderLayer.SOLID;

		OverridingBlock() {
			super(Material.ROCK);
		}

		@Override
		public boolean canRenderInLayer(IBlockState state, BlockRenderLayer l) {
			return l == layer;
		}
	}

	@Test
	void blockOverridingCanRenderInLayerStillGetsItsExtraLayer() {
		OverridingBlock block = new OverridingBlock();
		IBlockState state = block.getDefaultState();
		assertFalse(block.canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT));
		// what the Celeritas call-site wrapper asks after the block's own answer was false
		assertTrue(LayerRouter.allowExtraLayer(state, BlockRenderLayer.TRANSLUCENT, new BlockPos(0, 64, 0)));
	}

	@Test
	void nativeMaskFollowsARuntimeLayerChangeWithoutAReload() {
		OverridingBlock block = new OverridingBlock();
		IBlockState state = block.getDefaultState();
		assertTrue(LayerRouter.isNativeLayer(state, BlockRenderLayer.SOLID));
		assertEquals(LayerRouter.bit(BlockRenderLayer.TRANSLUCENT), LayerRouter.extraMask(state));

		block.layer = BlockRenderLayer.TRANSLUCENT;
		assertFalse(LayerRouter.isNativeLayer(state, BlockRenderLayer.SOLID));
		assertTrue(LayerRouter.isNativeLayer(state, BlockRenderLayer.TRANSLUCENT));
		assertEquals(LayerRouter.bit(BlockRenderLayer.TRANSLUCENT), LayerRouter.nativeMask(state));
		assertEquals(0, LayerRouter.extraMask(state));
		assertFalse(LayerRouter.allowExtraLayer(state, BlockRenderLayer.TRANSLUCENT));

		BakedQuad quad = TestQuads.fullFace(EnumFacing.UP, overlaySource, -1);
		BlockPos pos = new BlockPos(1, 64, 1);
		FakeBlockAccess access = new FakeBlockAccess().set(pos, state);
		assertEquals(List.of(quad), QuadPipeline.transform(state, pos, access, BlockRenderLayer.TRANSLUCENT, null, List.of(quad)));
	}

	@Test
	void missingS20ApiGrantsNothing() {
		try {
			RenderPathStatus.recordApiMissing();
			LayerRouter.refreshActive();
			assertFalse(LayerRouter.extraPossible);
			assertFalse(LayerRouter.allowExtraLayer(Blocks.STONE.getDefaultState(), BlockRenderLayer.TRANSLUCENT));
		} finally {
			RenderPathStatus.clearApiMissingForTests();
			LayerRouter.refreshActive();
		}
	}

	@Test
	void positionSentToVanillaGetsNothing() {
		BlockPos pos = new BlockPos(5, 64, 5);
		gate.forceVanillaAt(pos);
		IBlockState stone = Blocks.STONE.getDefaultState();
		assertFalse(LayerRouter.allowExtraLayer(stone, BlockRenderLayer.TRANSLUCENT, pos));
		assertTrue(LayerRouter.allowExtraLayer(stone, BlockRenderLayer.TRANSLUCENT, pos.east()));
	}

	@Test
	void shaderOverrideSuppressesExtraLayers() {
		gate.override(Blocks.STONE, BlockRenderLayer.CUTOUT);
		assertFalse(LayerRouter.allowExtraLayer(Blocks.STONE.getDefaultState(), BlockRenderLayer.TRANSLUCENT));
		assertEquals(LayerRouter.bit(BlockRenderLayer.CUTOUT), LayerRouter.builtMask(Blocks.STONE.getDefaultState(), null));
	}

	@Test
	void unreadableOverrideCountsEveryLayerAsNative() {
		gate.overrideReliable(false);
		IBlockState stone = Blocks.STONE.getDefaultState();
		for (BlockRenderLayer layer : LayerRouter.LAYERS) {
			assertTrue(LayerRouter.isNativeLayer(stone, layer));
		}
		assertFalse(LayerRouter.allowExtraLayer(stone, BlockRenderLayer.TRANSLUCENT));
	}

	@Test
	void spriteOnlyInAnActualStateModelStillGrantsTheLayer() {
		IBakedModel withOverlay = new SingleQuadModel(TestQuads.fullFace(EnumFacing.UP, overlaySource, -1));
		IBakedModel plain = new SingleQuadModel(TestQuads.fullFace(EnumFacing.UP, TestSprites.create("test:plain"), -1));
		ModelProbe.setModelLookupForTests(s -> s.getBlock() == Blocks.GRASS && s.getValue(BlockGrass.SNOWY) ? withOverlay : plain);
		IBlockState raw = Blocks.GRASS.getDefaultState();
		assertFalse(raw.getValue(BlockGrass.SNOWY));
		assertTrue(LayerRouter.allowExtraLayer(raw, BlockRenderLayer.TRANSLUCENT));
	}

	@Test
	void spriteOnlyInAnotherWeightedVariantStillGrantsTheLayer() {
		IBakedModel plain = new SingleQuadModel(TestQuads.fullFace(EnumFacing.UP, TestSprites.create("test:plain"), -1));
		IBakedModel withOverlay = new SingleQuadModel(TestQuads.fullFace(EnumFacing.UP, overlaySource, -1));
		IBakedModel weighted = new WeightedBakedModel.Builder().add(plain, 1).add(withOverlay, 1).build();
		assertTrue(weighted.getQuads(null, null, 42L).get(0).getSprite() != overlaySource);
		ModelProbe.setModelLookupForTests(s -> weighted);
		assertTrue(LayerRouter.allowExtraLayer(Blocks.STONE.getDefaultState(), BlockRenderLayer.TRANSLUCENT));
	}

	@Test
	void modelThrowingLinkageErrorIsProbedOnceAndCachedAsEmpty() {
		java.util.concurrent.atomic.AtomicInteger lookups = new java.util.concurrent.atomic.AtomicInteger();
		IBakedModel broken = new SingleQuadModel(TestQuads.fullFace(EnumFacing.UP, overlaySource, -1)) {
			@Override
			public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
				throw new NoClassDefFoundError("optional/Dependency");
			}
		};
		ModelProbe.setModelLookupForTests(st -> {
			lookups.incrementAndGet();
			return broken;
		});
		IBlockState state = new OverridingBlock().getDefaultState();
		assertEquals(0, LayerRouter.extraMask(state));
		int afterFirst = lookups.get();
		assertEquals(0, LayerRouter.extraMask(state));
		assertEquals(afterFirst, lookups.get());

		com.evilctm.client.util.SpriteCalculator.ModelLookup previous = com.evilctm.client.util.SpriteCalculator.lookup;
		java.util.concurrent.atomic.AtomicInteger spriteLookups = new java.util.concurrent.atomic.AtomicInteger();
		try {
			com.evilctm.client.util.SpriteCalculator.lookup = st -> {
				spriteLookups.incrementAndGet();
				throw new NoClassDefFoundError("optional/Dependency");
			};
			IBlockState other = new OverridingBlock().getDefaultState();
			assertEquals(0, com.evilctm.client.util.SpriteCalculator.getSprites(other, EnumFacing.UP).length);
			assertEquals(0, com.evilctm.client.util.SpriteCalculator.getSprites(other, EnumFacing.DOWN).length);
			assertEquals(1, spriteLookups.get());
		} finally {
			com.evilctm.client.util.SpriteCalculator.lookup = previous;
		}
	}

	@Test
	void shaderOverrideLayerCountsAsNative() {
		gate.override(Blocks.GLASS, BlockRenderLayer.TRANSLUCENT);
		assertTrue(LayerRouter.isNativeLayer(Blocks.GLASS.getDefaultState(), BlockRenderLayer.TRANSLUCENT));
		assertTrue(LayerRouter.isNativeLayer(Blocks.GLASS.getDefaultState(), BlockRenderLayer.CUTOUT));
		assertFalse(LayerRouter.isNativeLayer(Blocks.GLASS.getDefaultState(), BlockRenderLayer.SOLID));
	}
}
