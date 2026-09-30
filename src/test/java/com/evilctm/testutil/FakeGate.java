/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.testutil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.evilctm.client.compat.demonica.RenderGate;
import com.evilctm.client.compat.demonica.RenderPathStatus;
import net.minecraft.block.Block;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;

/** A settable {@link RenderGate}. */
public class FakeGate implements RenderGate {
	public volatile boolean fastPath = true;
	public final Set<Block> forced = new HashSet<>();
	public final Map<Block, BlockRenderLayer> overrides = new HashMap<>();
	public volatile RenderPathStatus.Problem problem = RenderPathStatus.Problem.OK;
	public volatile boolean overrideReliable = true;
	public final Set<BlockPos> forcedPositions = new HashSet<>();

	public FakeGate fastPath(boolean on) {
		fastPath = on;
		return this;
	}

	public FakeGate override(Block block, BlockRenderLayer layer) {
		overrides.put(block, layer);
		return this;
	}

	public FakeGate forceVanilla(Block block) {
		forced.add(block);
		return this;
	}

	/** Sends only the block at {@code pos} to vanilla (as Demonica does next to a Component Model Hider hidden block). */
	public FakeGate forceVanillaAt(BlockPos pos) {
		forcedPositions.add(pos.toImmutable());
		return this;
	}

	public FakeGate overrideReliable(boolean reliable) {
		overrideReliable = reliable;
		return this;
	}

	@Override
	public boolean forcedVanillaAt(Block block, BlockPos pos) {
		return blockForcedVanilla(block) || forcedPositions.contains(pos.toImmutable());
	}

	@Override
	public boolean layerOverrideReliable() {
		return overrideReliable;
	}

	@Override
	public boolean fastPathActive() {
		return fastPath;
	}

	@Override
	public boolean blockForcedVanilla(Block block) {
		return forced.contains(block);
	}

	@Nullable
	@Override
	public BlockRenderLayer layerOverride(Block block) {
		return overrides.get(block);
	}

	@Override
	public RenderPathStatus.Problem probe() {
		return problem;
	}
}
