/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.ctm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.evilctm.client.config.EvilCtmConfig;
import com.evilctm.client.layer.ModelProbe;
import com.evilctm.client.model.ReloadEpoch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;

/**
 * Per-texture layer rules from CTM-mod metadata. Whether a layer is native to a block comes from
 * {@code LayerRouter.isNativeLayer}; this class only knows which sprites want which layer and which models use them.
 */
public final class CtmRenderLayerRouter {
	private static final EnumFacing[] FACES_AND_NULL = {EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH,
			EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, null};

	private static volatile Snapshot snapshot = new Snapshot(Long.MIN_VALUE, Map.of(), 0);

	private CtmRenderLayerRouter() {
	}

	public static void reload(List<CtmDefinition> definitions) {
		Map<String, LayerRule> next = new HashMap<>();
		int targets = 0;
		for (CtmDefinition definition : definitions) {
			if (definition.getLayer() != null) {
				LayerRule rule = new LayerRule(definition.getLayer(), definition.hasEmissiveFallback());
				if (next.putIfAbsent(definition.getResourceId().toString(), rule) == null) {
					targets |= 1 << rule.layer().ordinal();
				}
			}
		}
		snapshot = new Snapshot(ReloadEpoch.current(), Map.copyOf(next), targets);
	}

	/** The current snapshot; rules carry over an epoch change but the per-state model cache starts empty. */
	private static Snapshot snapshot() {
		Snapshot s = snapshot;
		long epoch = ReloadEpoch.current();
		if (s.epoch != epoch) {
			s = new Snapshot(epoch, s.spriteLayers, s.targetMask);
			snapshot = s;
		}
		return s;
	}

	/** True when any loaded CTM-mod definition carries a {@code layer}. */
	public static boolean active() {
		return !snapshot.spriteLayers.isEmpty();
	}

	/**
	 * Layers (as a {@code 1 << ordinal} mask) that {@code state}'s model uses through a sprite with a layer rule and
	 * that are not in {@code nativeMask}.
	 */
	public static int extraLayerMask(IBlockState state, int nativeMask) {
		Snapshot s = snapshot();
		if (s.targetMask == 0) {
			return 0;
		}
		return modelLayerMask(s, state) & s.targetMask & ~nativeMask;
	}

	public static boolean allowAdditionalLayer(IBlockState state, BlockRenderLayer layer) {
		EvilCtmConfig cfg = EvilCtmConfig.INSTANCE;
		if (!cfg.connectedTextures.get() || !cfg.ctmModTextures.get()) {
			return false;
		}
		Snapshot s = snapshot();
		int bit = 1 << layer.ordinal();
		return (s.targetMask & bit) != 0 && (modelLayerMask(s, state) & bit) != 0;
	}

	public static boolean shouldRender(@Nullable TextureAtlasSprite sprite, BlockRenderLayer layer, boolean routedLayer) {
		LayerRule rule = ruleFor(sprite);
		return rule == null ? !routedLayer : rule.layer() == layer || (rule.emissiveFallback() && !routedLayer);
	}

	public static boolean shouldProcessWrappedOverlay(@Nullable TextureAtlasSprite sprite, BlockRenderLayer layer,
			boolean routedLayer) {
		return shouldRender(sprite, layer, routedLayer);
	}

	public static boolean shouldGenerateSuffixOverlay(@Nullable TextureAtlasSprite sprite) {
		return sprite != null && ruleFor(sprite) == null;
	}

	public static boolean shouldFullbrightEmissiveFallback(@Nullable TextureAtlasSprite sprite, boolean routedLayer) {
		LayerRule rule = ruleFor(sprite);
		return rule != null && rule.emissiveFallback() && !routedLayer;
	}

	@Nullable
	private static LayerRule ruleFor(@Nullable TextureAtlasSprite sprite) {
		return sprite == null ? null : snapshot.spriteLayers.get(sprite.getIconName());
	}

	private static int modelLayerMask(Snapshot s, IBlockState state) {
		Integer cached = s.modelLayers.get(state);
		if (cached != null) {
			return cached;
		}
		int mask = findModelLayers(s, state);
		s.modelLayers.putIfAbsent(state, mask);
		return mask;
	}

	private static int findModelLayers(Snapshot s, IBlockState state) {
		IBakedModel model = ModelProbe.model(state);
		if (model == null) {
			return 0;
		}
		int found = 0;
		for (EnumFacing face : FACES_AND_NULL) {
			for (BakedQuad quad : ModelProbe.quads(model, state, face)) {
				TextureAtlasSprite sprite = quad.getSprite();
				if (sprite != null) {
					LayerRule rule = s.spriteLayers.get(sprite.getIconName());
					if (rule != null) {
						found |= 1 << rule.layer().ordinal();
					}
				}
			}
		}
		return found;
	}

	private record LayerRule(BlockRenderLayer layer, boolean emissiveFallback) {
	}

	private static final class Snapshot {
		final long epoch;
		final Map<String, LayerRule> spriteLayers;
		final int targetMask;
		final ConcurrentHashMap<IBlockState, Integer> modelLayers = new ConcurrentHashMap<>();

		Snapshot(long epoch, Map<String, LayerRule> spriteLayers, int targetMask) {
			this.epoch = epoch;
			this.spriteLayers = spriteLayers;
			this.targetMask = targetMask;
		}
	}
}
