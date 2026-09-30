/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.util;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockQuartz;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

/** Resolves the orientation axis of logs, pillars and quartz pillars from a block state. */
public final class AxisUtil {
	private static final ConcurrentHashMap<Block, Optional<IProperty<?>>> PROPERTIES = new ConcurrentHashMap<>();

	private AxisUtil() {
	}

	/** The state's axis, or null when the state has none (or {@code LOG_AXIS=NONE}, or a non-pillar quartz). */
	@Nullable
	public static EnumFacing.Axis getAxis(IBlockState state) {
		IProperty<?> property = PROPERTIES.computeIfAbsent(state.getBlock(), block -> findProperty(state)).orElse(null);
		if (property == null) {
			return null;
		}
		Object value = state.getValue(property);
		if (value instanceof EnumFacing.Axis axis) {
			return axis;
		}
		if (value instanceof BlockLog.EnumAxis logAxis) {
			return switch (logAxis) {
				case X -> EnumFacing.Axis.X;
				case Y -> EnumFacing.Axis.Y;
				case Z -> EnumFacing.Axis.Z;
				default -> null;
			};
		}
		if (value instanceof BlockQuartz.EnumType type) {
			return switch (type) {
				case LINES_X -> EnumFacing.Axis.X;
				case LINES_Y -> EnumFacing.Axis.Y;
				case LINES_Z -> EnumFacing.Axis.Z;
				default -> null;
			};
		}
		return null;
	}

	private static Optional<IProperty<?>> findProperty(IBlockState state) {
		for (IProperty<?> property : state.getPropertyKeys()) {
			Class<?> type = property.getValueClass();
			if (type == EnumFacing.Axis.class || type == BlockLog.EnumAxis.class || type == BlockQuartz.EnumType.class) {
				return Optional.of(property);
			}
		}
		return Optional.empty();
	}
}
