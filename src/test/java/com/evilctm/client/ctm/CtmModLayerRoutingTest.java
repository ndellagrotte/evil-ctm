/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.ctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.evilctm.client.layer.LayerRouter;
import com.evilctm.client.layer.ModelProbe;
import com.evilctm.client.model.BakedQuadLightmap;
import com.evilctm.client.model.ReloadEpoch;
import com.evilctm.client.pipeline.QuadPipeline;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.FakeGate;
import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.PipelineHarness;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import com.google.gson.JsonParser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** CTM-mod {@code layer} metadata end to end: layer source, LayerRouter masks and the pipeline's per-quad filter. */
class CtmModLayerRoutingTest {
	private static final BlockPos POS = new BlockPos(3, 70, 3);

	private FakeGate gate;
	private TextureAtlasSprite glow;
	private IBlockState stone;
	private FakeBlockAccess access;
	private int lookups;

	private static final class OneQuadModel implements IBakedModel {
		private final List<BakedQuad> quads;

		OneQuadModel(BakedQuad quad) {
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
		McBootstrap.ensure();
		gate = new FakeGate();
		LayerRouter.setGateForTests(gate);
		glow = TestSprites.create("test:glow_e");
		stone = Blocks.STONE.getDefaultState();
		access = new FakeBlockAccess().set(POS, stone);
		IBakedModel model = new OneQuadModel(TestQuads.fullFace(EnumFacing.UP, glow, -1));
		lookups = 0;
		ModelProbe.setModelLookupForTests(s -> {
			lookups++;
			return model;
		});
	}

	@AfterEach
	void tearDown() {
		ModelProbe.setModelLookupForTests(null);
		CtmRenderLayerRouter.reload(List.of());
		PipelineHarness.reset();
	}

	private void load(String json) {
		CtmDefinition def = CtmMcmetaParser.parse(new ResourceLocation("test:glow_e"),
				JsonParser.parseString(json).getAsJsonObject(), "test", 0);
		CtmRenderLayerRouter.reload(List.of(def));
		LayerRouter.refreshActive();
	}

	private List<BakedQuad> run(BlockRenderLayer layer) {
		return QuadPipeline.transform(stone, POS, access, layer, null, List.of(TestQuads.fullFace(EnumFacing.UP, glow, -1)));
	}

	@Test
	void translucentDefinitionOnSolidBlockMovesTheQuadToTranslucent() {
		load("{\"layer\":\"TRANSLUCENT\"}");

		assertTrue(CtmRenderLayerRouter.active());
		assertTrue(LayerRouter.extraPossible);
		assertTrue(LayerRouter.allowExtraLayer(stone, BlockRenderLayer.TRANSLUCENT));
		assertFalse(LayerRouter.allowExtraLayer(stone, BlockRenderLayer.CUTOUT));
		assertEquals(LayerRouter.bit(BlockRenderLayer.TRANSLUCENT), LayerRouter.extraMask(stone));
		assertTrue(run(BlockRenderLayer.SOLID).isEmpty());
		List<BakedQuad> translucent = run(BlockRenderLayer.TRANSLUCENT);
		assertEquals(1, translucent.size());
		assertSame(glow, translucent.get(0).getSprite());
	}

	@Test
	void emissiveFallbackStaysFullbrightInTheNativeLayer() {
		load("{\"layer\":\"TRANSLUCENT\",\"extra\":{\"emissive_fallback\":true}}");

		List<BakedQuad> nativeOut = run(BlockRenderLayer.SOLID);
		assertEquals(1, nativeOut.size());
		BakedQuad expected = BakedQuadLightmap.withMinimum(TestQuads.fullFace(EnumFacing.UP, glow, -1), 15, 15);
		assertEquals(expected.getVertexData().length, nativeOut.get(0).getVertexData().length);
		assertTrue(java.util.Arrays.equals(expected.getVertexData(), nativeOut.get(0).getVertexData()));
		assertEquals(1, run(BlockRenderLayer.TRANSLUCENT).size());
	}

	@Test
	void fastPathOffGrantsNoExtraLayerAndKeepsNativeOnly() {
		load("{\"layer\":\"TRANSLUCENT\"}");
		gate.fastPath(false);

		assertFalse(LayerRouter.allowExtraLayer(stone, BlockRenderLayer.TRANSLUCENT));
	}

	@Test
	void modelLayerProbeIsCachedUntilTheReloadEpochChanges() {
		load("{\"layer\":\"TRANSLUCENT\"}");
		int nativeMask = LayerRouter.nativeMask(stone);

		int first = CtmRenderLayerRouter.extraLayerMask(stone, nativeMask);
		int afterFirst = lookups;
		assertEquals(first, CtmRenderLayerRouter.extraLayerMask(stone, nativeMask));
		assertEquals(afterFirst, lookups);

		ReloadEpoch.bump();
		assertEquals(first, CtmRenderLayerRouter.extraLayerMask(stone, nativeMask));
		assertTrue(lookups > afterFirst);
	}

	@Test
	void noLayerDefinitionsMeansInactive() {
		CtmRenderLayerRouter.reload(List.of());
		assertFalse(CtmRenderLayerRouter.active());
		assertEquals(0, CtmRenderLayerRouter.extraLayerMask(stone, 0));
	}
}
