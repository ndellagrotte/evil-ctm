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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;

/**
 * Per-texture layer rules from CTM-mod metadata. Whether a layer is native to a block comes from
 * {@code LayerRouter.isNativeLayer}; this class only knows which sprites want which layer and which models use them.
 */
public final class CtmRenderLayerRouter {
	/** The published rules; written only by {@link #reload}, so a stale cache refresh can never bring old rules back. */
	private static volatile Rules rules = new Rules(Map.of(), 0);
	private static volatile Snapshot snapshot = new Snapshot(Long.MIN_VALUE, rules);

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
		Rules r = new Rules(Map.copyOf(next), targets);
		rules = r;
		snapshot = new Snapshot(ReloadEpoch.current(), r);
	}

	/**
	 * The per-epoch model cache for the current rules. The rules always come from {@link #rules}, never from the cache
	 * that was read, so a racing refresh can at worst install an extra empty cache; one built for other rules or an
	 * older epoch is replaced on the next call.
	 */
	private static Snapshot snapshot() {
		Snapshot s = snapshot;
		Rules r = rules;
		long epoch = ReloadEpoch.current();
		if (s.epoch != epoch || s.rules != r) {
			s = new Snapshot(epoch, r);
			snapshot = s;
		}
		return s;
	}

	/** True when any loaded CTM-mod definition carries a {@code layer}. */
	public static boolean active() {
		return !rules.spriteLayers().isEmpty();
	}

	/**
	 * Layers (as a {@code 1 << ordinal} mask) that {@code state}'s model uses through a sprite with a layer rule and
	 * that are not in {@code nativeMask}.
	 */
	public static int extraLayerMask(IBlockState state, int nativeMask) {
		Snapshot s = snapshot();
		if (s.rules.targetMask() == 0) {
			return 0;
		}
		return modelLayerMask(s, state) & s.rules.targetMask() & ~nativeMask;
	}

	public static boolean allowAdditionalLayer(IBlockState state, BlockRenderLayer layer) {
		EvilCtmConfig cfg = EvilCtmConfig.INSTANCE;
		if (!cfg.connectedTextures.get() || !cfg.ctmModTextures.get()) {
			return false;
		}
		Snapshot s = snapshot();
		int bit = 1 << layer.ordinal();
		return (s.rules.targetMask() & bit) != 0 && (modelLayerMask(s, state) & bit) != 0;
	}

	public static boolean shouldRender(@Nullable TextureAtlasSprite sprite, BlockRenderLayer layer, boolean routedLayer) {
		LayerRule rule = ruleFor(sprite);
		return rule == null ? !routedLayer : rule.layer() == layer || (rule.emissiveFallback() && !routedLayer);
	}

	/**
	 * {@link #shouldRender}, where a rule whose layer is not in {@code builtMask} (the layers the block is really meshed
	 * in) counts as no rule: the quad stays in its native layers instead of vanishing.
	 */
	public static boolean shouldRender(@Nullable TextureAtlasSprite sprite, BlockRenderLayer layer, boolean routedLayer, int builtMask) {
		LayerRule rule = effectiveRule(sprite, builtMask);
		return rule == null ? !routedLayer : rule.layer() == layer || (rule.emissiveFallback() && !routedLayer);
	}

	@Nullable
	private static LayerRule effectiveRule(@Nullable TextureAtlasSprite sprite, int builtMask) {
		LayerRule rule = ruleFor(sprite);
		return rule != null && (builtMask & 1 << rule.layer().ordinal()) != 0 ? rule : null;
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
		return sprite == null ? null : rules.spriteLayers().get(sprite.getIconName());
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

	/** Rule layers used by any model of the block, counted only where the model returns the sprite in that layer. */
	private static int findModelLayers(Snapshot s, IBlockState state) {
		try {
			int found = 0;
			for (ModelProbe.ProbedSprite probed : ModelProbe.blockSprites(state)) {
				LayerRule rule = s.rules.spriteLayers().get(probed.sprite().getIconName());
				if (rule != null) {
					found |= (1 << rule.layer().ordinal()) & probed.layers();
				}
			}
			return found;
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return 0;
		}
	}

	private record LayerRule(BlockRenderLayer layer, boolean emissiveFallback) {
	}

	private record Rules(Map<String, LayerRule> spriteLayers, int targetMask) {
	}

	private static final class Snapshot {
		final long epoch;
		final Rules rules;
		final ConcurrentHashMap<IBlockState, Integer> modelLayers = new ConcurrentHashMap<>();

		Snapshot(long epoch, Rules rules) {
			this.epoch = epoch;
			this.rules = rules;
		}
	}
}
