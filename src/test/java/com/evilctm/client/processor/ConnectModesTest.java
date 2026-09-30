/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.evilctm.client.model.ReloadEpoch;
import com.evilctm.client.properties.BasicConnectingCtmProperties.ConnectionType;
import com.evilctm.client.util.SpriteCalculator;
import com.evilctm.testutil.FakeBlockAccess;
import com.evilctm.testutil.McBootstrap;
import com.evilctm.testutil.TestQuads;
import com.evilctm.testutil.TestSprites;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectModesTest {
	private static final class FaceModel implements IBakedModel {
		final Map<EnumFacing, List<BakedQuad>> quads = new HashMap<>();
		final AtomicInteger calls = new AtomicInteger();

		@Override
		public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
			calls.incrementAndGet();
			return side == null ? Collections.emptyList() : quads.getOrDefault(side, Collections.emptyList());
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
			return null;
		}

		@Override
		public ItemOverrideList getOverrides() {
			return ItemOverrideList.NONE;
		}
	}

	private FakeBlockAccess world;
	private final Map<IBlockState, FaceModel> models = new HashMap<>();

	@BeforeEach
	void setUp() {
		McBootstrap.ensure();
		world = new FakeBlockAccess();
		models.clear();
		SpriteCalculator.lookup = models::get;
		ReloadEpoch.bump();
	}

	@AfterEach
	void tearDown() {
		SpriteCalculator.lookup = com.evilctm.client.layer.ModelProbe::model;
		ReloadEpoch.bump();
	}

	private static IBlockState glass(EnumDyeColor color) {
		return Blocks.STAINED_GLASS.getDefaultState().withProperty(BlockStainedGlass.COLOR, color);
	}

	private boolean connects(ConnectionType type, IBlockState a, IBlockState b, TextureAtlasSprite sprite) {
		BlockPos p = BlockPos.ORIGIN;
		BlockPos q = p.east();
		world.set(p, a).set(q, b);
		return type.shouldConnect(world, p, a, a, q, EnumFacing.NORTH, sprite);
	}

	@Test
	void blockModeComparesBlockAndMeta() {
		assertTrue(connects(ConnectionType.BLOCK, glass(EnumDyeColor.WHITE), glass(EnumDyeColor.WHITE), null));
		assertFalse(connects(ConnectionType.BLOCK, glass(EnumDyeColor.WHITE), glass(EnumDyeColor.RED), null));
		assertFalse(connects(ConnectionType.BLOCK, glass(EnumDyeColor.WHITE), Blocks.GLASS.getDefaultState(), null));
	}

	@Test
	void tileModeConnectsOnSharedSprite() {
		TextureAtlasSprite shared = TestSprites.create("test:shared");
		TextureAtlasSprite other = TestSprites.create("test:other");
		IBlockState stone = Blocks.STONE.getDefaultState();
		IBlockState dirt = Blocks.DIRT.getDefaultState();
		IBlockState sand = Blocks.SAND.getDefaultState();
		FaceModel dirtModel = new FaceModel();
		dirtModel.quads.put(EnumFacing.NORTH, List.of(TestQuads.fullFace(EnumFacing.NORTH, shared, -1)));
		FaceModel sandModel = new FaceModel();
		sandModel.quads.put(EnumFacing.NORTH, List.of(TestQuads.fullFace(EnumFacing.NORTH, other, -1)));
		models.put(dirt, dirtModel);
		models.put(sand, sandModel);
		assertTrue(connects(ConnectionType.TILE, stone, dirt, shared));
		assertFalse(connects(ConnectionType.TILE, stone, sand, shared));
	}

	@Test
	void materialModeConnectsGlassToStainedGlass() {
		assertTrue(connects(ConnectionType.MATERIAL, Blocks.GLASS.getDefaultState(), glass(EnumDyeColor.RED), null));
		assertFalse(connects(ConnectionType.MATERIAL, Blocks.GLASS.getDefaultState(), Blocks.STONE.getDefaultState(), null));
	}

	@Test
	void stateModeRequiresIdenticalStates() {
		assertTrue(connects(ConnectionType.STATE, glass(EnumDyeColor.RED), glass(EnumDyeColor.RED), null));
		assertFalse(connects(ConnectionType.STATE, glass(EnumDyeColor.RED), glass(EnumDyeColor.BLUE), null));
	}

	@Test
	void spriteCacheIsInvalidatedByEpochBump() {
		TextureAtlasSprite first = TestSprites.create("test:first");
		TextureAtlasSprite second = TestSprites.create("test:second");
		IBlockState dirt = Blocks.DIRT.getDefaultState();
		FaceModel model = new FaceModel();
		model.quads.put(EnumFacing.NORTH, List.of(TestQuads.fullFace(EnumFacing.NORTH, first, -1)));
		models.put(dirt, model);
		TextureAtlasSprite[] a = SpriteCalculator.getSprites(dirt, EnumFacing.NORTH);
		assertArrayEquals(new TextureAtlasSprite[] {first}, a);
		int calls = model.calls.get();
		assertSame(a, SpriteCalculator.getSprites(dirt, EnumFacing.NORTH));
		assertTrue(model.calls.get() == calls);

		model.quads.put(EnumFacing.NORTH, List.of(TestQuads.fullFace(EnumFacing.NORTH, second, -1)));
		assertArrayEquals(new TextureAtlasSprite[] {first}, SpriteCalculator.getSprites(dirt, EnumFacing.NORTH));
		ReloadEpoch.bump();
		assertArrayEquals(new TextureAtlasSprite[] {second}, SpriteCalculator.getSprites(dirt, EnumFacing.NORTH));
	}

	@Test
	void missingModelYieldsEmptySprites() {
		assertTrue(SpriteCalculator.getSprites(Blocks.DIRT.getDefaultState(), EnumFacing.UP).length == 0);
	}
}
