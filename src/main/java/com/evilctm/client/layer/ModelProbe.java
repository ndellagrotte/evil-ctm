/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

/**
 * Model access off the render path. Every probe forces the Forge render layer to {@code null} (so multi-layer models
 * return all their quads), restores it afterwards and never throws.
 */
public final class ModelProbe {
	private static final Function<IBlockState, IBakedModel> DEFAULT_LOOKUP =
			state -> Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(state);

	private static volatile Function<IBlockState, IBakedModel> lookup = DEFAULT_LOOKUP;

	private ModelProbe() {
	}

	/** The block model for {@code state}, or {@code null} when it cannot be looked up. */
	@Nullable
	public static IBakedModel model(IBlockState state) {
		try {
			return lookup.apply(state);
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static List<BakedQuad> quads(@Nullable IBakedModel model, IBlockState state, @Nullable EnumFacing face) {
		if (model == null) {
			return Collections.emptyList();
		}
		BlockRenderLayer previous = MinecraftForgeClient.getRenderLayer();
		try {
			ForgeHooksClient.setRenderLayer(null);
			List<BakedQuad> quads = model.getQuads(state, face, 42L);
			return quads != null ? quads : Collections.emptyList();
		} catch (RuntimeException e) {
			return Collections.emptyList();
		} finally {
			ForgeHooksClient.setRenderLayer(previous);
		}
	}

	/** Test hook: replaces the model lookup; {@code null} restores the block renderer dispatcher. */
	public static void setModelLookupForTests(@Nullable Function<IBlockState, IBakedModel> testLookup) {
		lookup = testLookup != null ? testLookup : DEFAULT_LOOKUP;
	}
}
