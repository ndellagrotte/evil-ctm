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
import com.evilctm.impl.client.EmissiveSpriteApiImpl;
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

	@Test
	void solidBlockEmitsNothingEmissiveInSolid() {
		List<BakedQuad> out = run(stone, BlockRenderLayer.SOLID);
		assertEquals(1, out.size());
		assertSame(baseQuad, out.get(0));
	}

	@Test
	void solidBlockEmitsOnlyTheEmissiveQuadInCutoutMipped() {
		assertEquals(LayerRouter.bit(BlockRenderLayer.CUTOUT_MIPPED), LayerRouter.extraMask(stone));
		List<BakedQuad> out = run(stone, BlockRenderLayer.CUTOUT_MIPPED);
		assertEquals(1, out.size());
		assertSame(glow, out.get(0).getSprite());
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
				Map<TextureAtlasSprite, TextureAtlasSprite> view = EmissiveSpriteApiImpl.INSTANCE.snapshot();
				int size = view.size();
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
