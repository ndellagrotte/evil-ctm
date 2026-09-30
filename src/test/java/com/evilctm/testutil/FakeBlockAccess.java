/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.testutil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

/** A map-backed world: AIR by default, one configurable biome, combined light 0xF000F0. Thread-safe for reads. */
public class FakeBlockAccess implements IBlockAccess {
	private final Map<BlockPos, IBlockState> states = new ConcurrentHashMap<>();
	private final Map<BlockPos, TileEntity> tileEntities = new HashMap<>();
	private Biome biome;

	public FakeBlockAccess() {
		McBootstrap.ensure();
		biome = Biomes.PLAINS;
	}

	public FakeBlockAccess set(BlockPos pos, IBlockState state) {
		states.put(pos.toImmutable(), state);
		return this;
	}

	public FakeBlockAccess setTileEntity(BlockPos pos, TileEntity tileEntity) {
		tileEntities.put(pos.toImmutable(), tileEntity);
		return this;
	}

	public FakeBlockAccess biome(Biome biome) {
		this.biome = biome;
		return this;
	}

	@Nullable
	@Override
	public TileEntity getTileEntity(BlockPos pos) {
		return tileEntities.get(pos);
	}

	@Override
	public int getCombinedLight(BlockPos pos, int lightValue) {
		return 0xF000F0;
	}

	@Override
	public IBlockState getBlockState(BlockPos pos) {
		IBlockState state = states.get(pos);
		return state != null ? state : Blocks.AIR.getDefaultState();
	}

	@Override
	public boolean isAirBlock(BlockPos pos) {
		return getBlockState(pos).getBlock() == Blocks.AIR;
	}

	@Override
	public Biome getBiome(BlockPos pos) {
		return biome;
	}

	@Override
	public int getStrongPower(BlockPos pos, EnumFacing direction) {
		return 0;
	}

	@Override
	public WorldType getWorldType() {
		return WorldType.DEFAULT;
	}

	@Override
	public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
		return getBlockState(pos).isSideSolid(this, pos, side);
	}
}
