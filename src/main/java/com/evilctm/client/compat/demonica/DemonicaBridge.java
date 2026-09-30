/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.compat.demonica;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import com.demonica.celeritas.guard.QuarantineGuard;
import com.demonica.celeritas.terrain.ShaderBlockContexts;
import com.demonica.compat.FastBlockRendererCompat;
import com.demonica.compat.architecturecraft.ArchitectureCraftCompat;
import com.demonica.compat.snowrealmagic.SnowRealMagicCompat;
import com.demonica.runtime.DemonicaRuntime;
import com.evilctm.client.EvilCtmClient;
import com.evilctm.client.layer.LayerRouter;
import net.minecraft.block.Block;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;

/**
 * {@link RenderGate} over Demonica internals (not API; checked against Demonica 0.6.0). Every call is guarded against
 * {@link LinkageError}: on failure the bridge marks itself broken, logs once and returns the conservative answer
 * (fast path off, block forced to vanilla). A broken bridge cannot say whether a block has a shader layer override,
 * so it reports {@link #layerOverrideReliable()} false and every layer the block is meshed in counts as native.
 */
public final class DemonicaBridge implements RenderGate {
	private final AtomicBoolean loggedBroken = new AtomicBoolean();
	private volatile boolean broken;
	/** 0 = unresolved, 1 = accepted, 2 = rejected. */
	private volatile int quarantine;

	/** Reads the quarantine verdict of the active gate once; called from postInit, and lazily on first use otherwise. */
	public static void resolveQuarantine() {
		if (LayerRouter.gate() instanceof DemonicaBridge bridge) {
			bridge.accepted();
		}
	}

	private boolean accepted() {
		int q = quarantine;
		if (q == 0) {
			if (broken) {
				return false;
			}
			try {
				q = QuarantineGuard.current().accepted() ? 1 : 2;
			} catch (LinkageError | RuntimeException e) {
				markBroken("QuarantineGuard.current().accepted()", e);
				return false;
			}
			quarantine = q;
		}
		return q == 1;
	}

	@Override
	public boolean fastPathActive() {
		if (broken || !accepted()) {
			return false;
		}
		try {
			return DemonicaRuntime.options().performance.useFastBlockRenderer;
		} catch (LinkageError | RuntimeException e) {
			markBroken("DemonicaRuntime.options().performance.useFastBlockRenderer", e);
			return false;
		}
	}

	@Override
	public boolean blockForcedVanilla(Block block) {
		if (broken) {
			return true;
		}
		try {
			return SnowRealMagicCompat.shouldForceVanillaRender(block) || ArchitectureCraftCompat.shouldForceVanillaRender(block);
		} catch (LinkageError | RuntimeException e) {
			markBroken("*Compat.shouldForceVanillaRender", e);
			return true;
		}
	}

	@Override
	public boolean forcedVanillaAt(Block block, BlockPos pos) {
		if (broken) {
			return true;
		}
		try {
			return FastBlockRendererCompat.requiresVanillaRenderer(block, pos);
		} catch (LinkageError | RuntimeException e) {
			markBroken("FastBlockRendererCompat.requiresVanillaRenderer", e);
			return true;
		}
	}

	@Override
	public boolean layerOverrideReliable() {
		return !broken;
	}

	@Override
	@Nullable
	public BlockRenderLayer layerOverride(Block block) {
		if (broken) {
			return null;
		}
		try {
			return ShaderBlockContexts.layerOverride(block);
		} catch (LinkageError | RuntimeException e) {
			markBroken("ShaderBlockContexts.layerOverride", e);
			return null;
		}
	}

	@Override
	public RenderPathStatus.Problem probe() {
		boolean accepted = accepted();
		if (broken) {
			return RenderPathStatus.Problem.BRIDGE_BROKEN;
		}
		if (!accepted) {
			return RenderPathStatus.Problem.CELERITAS_REJECTED;
		}
		if (!fastPathActive()) {
			return broken ? RenderPathStatus.Problem.BRIDGE_BROKEN : RenderPathStatus.Problem.FAST_RENDERER_OFF;
		}
		return RenderPathStatus.Problem.OK;
	}

	private void markBroken(String where, Throwable t) {
		broken = true;
		if (loggedBroken.compareAndSet(false, true)) {
			EvilCtmClient.LOGGER.error("Demonica bridge call {} failed; extra layers are disabled and shader layer overrides are unknown, so CTM routing treats every layer a block is meshed in as native. Is this Demonica version supported?", where, t);
		}
	}
}
