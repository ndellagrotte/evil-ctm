/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import java.util.EnumSet;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.evilctm.api.client.ProcessingDataProvider;
import com.evilctm.client.properties.BaseCtmProperties;
import com.evilctm.client.util.AxisUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.IWorldNameable;
import net.minecraft.world.biome.Biome;

public class BaseProcessingPredicate implements ProcessingPredicate {
	@Nullable
	protected EnumSet<EnumFacing> faces;
	@Nullable
	protected Predicate<Biome> biomePredicate;
	@Nullable
	protected IntPredicate heightPredicate;
	@Nullable
	protected Predicate<String> blockEntityNamePredicate;

	public BaseProcessingPredicate(@Nullable EnumSet<EnumFacing> faces, @Nullable Predicate<Biome> biomePredicate, @Nullable IntPredicate heightPredicate, @Nullable Predicate<String> blockEntityNamePredicate) {
		this.faces = faces;
		this.biomePredicate = biomePredicate;
		this.heightPredicate = heightPredicate;
		this.blockEntityNamePredicate = blockEntityNamePredicate;
	}

	@Override
	public boolean shouldProcessQuad(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, ProcessingDataProvider dataProvider) {
		if (heightPredicate != null && !heightPredicate.test(pos.getY())) {
			return false;
		}

		if (faces != null) {
			EnumFacing face = quad.getFace();
			if (face == null) {
				return false;
			}

			EnumFacing.Axis axis = AxisUtil.getAxis(appearanceState);
			if (axis == EnumFacing.Axis.X) {
				face = face.rotateAround(EnumFacing.Axis.Z);
			} else if (axis == EnumFacing.Axis.Z) {
				face = face.rotateAround(EnumFacing.Axis.X);
			}

			if (!faces.contains(face)) {
				return false;
			}
		}

		if (biomePredicate != null) {
			Biome biome = level.getBiome(pos);
			if (biome == null || !biomePredicate.test(biome)) {
				return false;
			}
		}

		if (blockEntityNamePredicate != null) {
			TileEntity blockEntity = level.getTileEntity(pos);
			if (blockEntity instanceof IWorldNameable nameable && nameable.hasCustomName()) {
				String blockEntityName = nameable.getDisplayName().getUnformattedText();
				if (blockEntityName == null || !blockEntityNamePredicate.test(blockEntityName)) {
					return false;
				}
			} else {
				return false;
			}
		}

		return true;
	}

	public static BaseProcessingPredicate fromProperties(BaseCtmProperties properties) {
		return new BaseProcessingPredicate(properties.getFaces(), properties.getBiomePredicate(), properties.getHeightPredicate(), properties.getBlockEntityNamePredicate());
	}
}
