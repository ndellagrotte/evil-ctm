/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import com.evilctm.client.layer.EmissiveLayerSource;
import com.evilctm.client.layer.LayerRouter;
import com.evilctm.client.layer.ModelProbe;
import com.evilctm.client.model.EmissiveBakedQuad;
import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.impl.client.EmissiveSpriteApiImpl;
import com.evilctm.impl.client.ProcessingContextImpl;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmissivePassTest {
	private static final BlockPos POS = new BlockPos(3, 64, 3);

	private PipelineHarness harness;
	private IBlockState stone;
	private IBlockState glass;
	private FakeBlockAccess access;
	private TextureAtlasSprite base;
	private TextureAtlasSprite glow;
	private BakedQuad baseQuad;

	private static final class Model implements IBakedModel {
		private final List<BakedQuad> quads;

		Model(BakedQuad quad) {
			quads = List.of(quad);
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
		harness = new PipelineHarness();
		harness.gate(new FakeGate());
		stone = Blocks.STONE.getDefaultState();
		glass = Blocks.GLASS.getDefaultState();
		access = new FakeBlockAccess().set(POS, stone);
		base = TestSprites.create("test:ore");
		glow = TestSprites.create("test:ore_e");
		baseQuad = TestQuads.fullFace(EnumFacing.NORTH, base, 3);
		EmissiveSpriteApiImpl.INSTANCE.publish(Map.of(base, glow));
		ModelProbe.setModelLookupForTests(s -> new Model(baseQuad));
		harness.publish();
	}

	@AfterEach
	void tearDown() {
		EmissiveSpriteApiImpl.INSTANCE.clear();
		ModelProbe.setModelLookupForTests(null);
		PipelineHarness.reset();
	}

	private List<BakedQuad> run(IBlockState state, BlockRenderLayer layer) {
		access.set(POS, state);
		return harness.run(state, POS, access, layer, EnumFacing.NORTH, List.of(baseQuad));
	}

	private static final int SOLID_MASK = LayerRouter.bit(BlockRenderLayer.SOLID);

	private List<BakedQuad> pass(@Nullable List<BakedQuad> out, List<BakedQuad> original, @Nullable List<BakedQuad> baseOutputs, BlockRenderLayer layer, boolean nativeLayer, int mask, boolean defer, ProcessingContextImpl ctx) {
		return EmissivePass.apply(out, original, baseOutputs, layer, nativeLayer, mask, defer, ctx);
	}

	@Test
	void solidNativeEmitsCompanionInSolidWhenExtraLayerIsNotGranted() {
		ProcessingContextImpl ctx = new ProcessingContextImpl().begin(null, BlockRenderLayer.SOLID);
		List<BakedQuad> out = pass(null, List.of(baseQuad), null, BlockRenderLayer.SOLID, true, SOLID_MASK, false, ctx);
		assertEquals(2, out.size());
		assertSame(glow, out.get(1).getSprite());
	}

	@Test
	void solidNativeEmitsNothingInSolidWhenExtraLayerIsGranted() {
		ProcessingContextImpl ctx = new ProcessingContextImpl().begin(null, BlockRenderLayer.SOLID);
		assertEquals(null, pass(null, List.of(baseQuad), null, BlockRenderLayer.SOLID, true, SOLID_MASK, true, ctx));
	}

	@Test
	void sevenArgFormDefersSolidOnlyWhenConfigAllowsExtraLayers() {
		EvilCtmConfig cfg = EvilCtmConfig.INSTANCE;
		boolean prev = cfg.extraLayers.get();
		try {
			ProcessingContextImpl ctx = new ProcessingContextImpl().begin(null, BlockRenderLayer.SOLID);
			cfg.extraLayers.set(false);
			List<BakedQuad> out = EmissivePass.apply(null, List.of(baseQuad), null, BlockRenderLayer.SOLID, true, SOLID_MASK, ctx);
			assertEquals(2, out.size());
			cfg.extraLayers.set(true);
			assertEquals(null, EmissivePass.apply(null, List.of(baseQuad), null, BlockRenderLayer.SOLID, true, SOLID_MASK, ctx));
		} finally {
			cfg.extraLayers.set(prev);
		}
	}

	@Test
	void pipelineEmitsSolidCompanionInSolidPass() {
		List<BakedQuad> out = run(stone, BlockRenderLayer.SOLID);
		assertEquals(2, out.size());
		assertSame(baseQuad, out.get(0));
		assertSame(glow, out.get(1).getSprite());
	}

	@Test
	void extraMaskRequestsCutoutMippedForSolidBlockWithPair() {
		assertEquals(LayerRouter.bit(BlockRenderLayer.CUTOUT_MIPPED), LayerRouter.extraMask(stone));
	}

	@Test
	void extraMaskForCtmTileOnlyPairNeedsTheFlag() {
		assertTrue(harness.addRule("minecraft:optifine/ctm/ore/ore.properties", "method=random\nmatchBlocks=stone\ntiles=test:ore_a test:ore_b\n"));
		harness.publish();
		TextureAtlasSprite tile = TestSprites.create("minecraft:evilctm_reserved/ctm/tile");
		TextureAtlasSprite tileGlow = TestSprites.create("test:tile_e");
		EmissiveSpriteApiImpl.INSTANCE.publish(Map.of(tile, tileGlow));
		LayerRouter.refreshActive();
		assertTrue(EmissiveSpriteApiImpl.hasCtmTilePairs());
		assertEquals(LayerRouter.bit(BlockRenderLayer.CUTOUT_MIPPED), EmissiveLayerSource.INSTANCE.extraLayerMask(stone, SOLID_MASK));
		EmissiveSpriteApiImpl.INSTANCE.publish(Map.of(TestSprites.create("test:other"), tileGlow));
		LayerRouter.refreshActive();
		assertFalse(EmissiveSpriteApiImpl.hasCtmTilePairs());
		assertEquals(0, EmissiveLayerSource.INSTANCE.extraLayerMask(stone, SOLID_MASK));
	}

	@Test
	void nonNativeCutoutMippedPassEmitsCompanionsOfBaseOutputsForSolidBlock() {
		TextureAtlasSprite tile = TestSprites.create("minecraft:evilctm_reserved/ctm/t2");
		TextureAtlasSprite tileGlow = TestSprites.create("test:t2_e");
		EmissiveSpriteApiImpl.INSTANCE.publish(Map.of(tile, tileGlow));
		BakedQuad tileQuad = TestQuads.fullFace(EnumFacing.NORTH, tile, 3);
		ProcessingContextImpl ctx = new ProcessingContextImpl().begin(null, BlockRenderLayer.CUTOUT_MIPPED);
		List<BakedQuad> out = pass(null, List.of(baseQuad), List.of(tileQuad), BlockRenderLayer.CUTOUT_MIPPED, false, SOLID_MASK, true, ctx);
		assertEquals(1, out.size());
		assertSame(tileGlow, out.get(0).getSprite());
	}

	@Test
	void nonNativeCutoutMippedPassOnCutoutNativeBlockAddsNoBaseCompanions() {
		int cutoutMask = LayerRouter.bit(BlockRenderLayer.CUTOUT);
		ProcessingContextImpl ctx = new ProcessingContextImpl().begin(null, BlockRenderLayer.CUTOUT_MIPPED);
		assertEquals(null, pass(null, List.of(baseQuad), List.of(baseQuad), BlockRenderLayer.CUTOUT_MIPPED, false, cutoutMask, true, ctx));
		// base quads kept in the pass (out) are not re-emitted either
		assertEquals(null, pass(null, List.of(baseQuad), null, BlockRenderLayer.CUTOUT_MIPPED, false, cutoutMask, true, ctx));
		List<BakedQuad> kept = new java.util.ArrayList<>(List.of(baseQuad));
		assertSame(kept, pass(kept, List.of(baseQuad), null, BlockRenderLayer.CUTOUT_MIPPED, false, cutoutMask, true, ctx));
		assertEquals(1, kept.size());
	}

	@Test
	void overlayGetsCompanionInItsOwnLayer() {
		ProcessingContextImpl ctx = new ProcessingContextImpl().begin(null, BlockRenderLayer.CUTOUT_MIPPED);
		ctx.emitOverlay(BlockRenderLayer.CUTOUT_MIPPED, baseQuad);
		List<BakedQuad> out = pass(new java.util.ArrayList<>(List.of(baseQuad)), List.of(baseQuad), null, BlockRenderLayer.CUTOUT_MIPPED, false, SOLID_MASK, true, ctx);
		assertEquals(2, out.size());
		assertSame(glow, out.get(1).getSprite());
	}

	@Test
	void cutoutNativeBlockEmitsEmissiveInTheSamePass() {
		assertEquals(0, LayerRouter.extraMask(glass));
		List<BakedQuad> out = run(glass, BlockRenderLayer.CUTOUT);
		assertEquals(2, out.size());
		assertSame(baseQuad, out.get(0));
		assertSame(glow, out.get(1).getSprite());
	}

	@Test
	void layerSourceIsInactiveWithoutPairs() {
		assertTrue(EmissiveLayerSource.INSTANCE.active());
		EmissiveSpriteApiImpl.INSTANCE.clear();
		assertFalse(EmissiveLayerSource.INSTANCE.active());
	}

	@Test
	void emissiveQuadIsFullBrightWithEmissiveSpriteAndInheritsShading() {
		BakedQuad emissive = EmissiveBakedQuad.create(baseQuad, glow);
		assertSame(glow, emissive.getSprite());
		assertEquals(baseQuad.getTintIndex(), emissive.getTintIndex());
		assertEquals(baseQuad.getFace(), emissive.getFace());
		assertEquals(baseQuad.shouldApplyDiffuseLighting(), emissive.shouldApplyDiffuseLighting());
		int stride = emissive.getFormat().getIntegerSize();
		int uv1 = emissive.getFormat().getUvOffsetById(1) / 4;
		for (int v = 0; v < 4; v++) {
			assertEquals(0x00F000F0, emissive.getVertexData()[v * stride + uv1]);
		}
	}

	@Test
	void publishIsAtomic() throws Exception {
		TextureAtlasSprite[] keys = new TextureAtlasSprite[32];
		Map<TextureAtlasSprite, TextureAtlasSprite> big = new IdentityHashMap<>();
		TextureAtlasSprite tile = TestSprites.create("minecraft:evilctm_reserved/x");
		for (int i = 0; i < keys.length; i++) {
			keys[i] = TestSprites.create("test:k" + i);
			big.put(keys[i], glow);
		}
		big.put(tile, glow);
		AtomicBoolean stop = new AtomicBoolean();
		AtomicReference<String> failure = new AtomicReference<>();
		Thread reader = new Thread(() -> {
			while (!stop.get()) {
				EmissiveSpriteApiImpl.View v = EmissiveSpriteApiImpl.INSTANCE.view();
				Map<TextureAtlasSprite, TextureAtlasSprite> view = v.pairs();
				int size = view.size();
				if (v.ctmTilePairs() != (size != 0)) {
					failure.set("flag/map mismatch: " + size + " " + v.ctmTilePairs());
					return;
				}
				boolean full = view.containsKey(tile);
				for (TextureAtlasSprite k : keys) {
					full &= view.containsKey(k);
				}
				if (size != 0 && !(size == keys.length + 1 && full)) {
					failure.set("partial map: " + size);
					return;
				}
			}
		});
		EmissiveSpriteApiImpl.INSTANCE.publish(Map.of());
		reader.start();
		for (int i = 0; i < 20_000; i++) {
			EmissiveSpriteApiImpl.INSTANCE.publish(i % 2 == 0 ? big : Map.of());
		}
		stop.set(true);
		reader.join();
		assertEquals(null, failure.get());
	}

	@Test
	void ctmTilePairsAreDetectedAtPublish() {
		assertFalse(EmissiveSpriteApiImpl.hasCtmTilePairs());
		TextureAtlasSprite tile = TestSprites.create("minecraft:evilctm_reserved/ctm/ore");
		EmissiveSpriteApiImpl.INSTANCE.publish(Map.of(tile, glow));
		assertTrue(EmissiveSpriteApiImpl.hasCtmTilePairs());
	}
}
