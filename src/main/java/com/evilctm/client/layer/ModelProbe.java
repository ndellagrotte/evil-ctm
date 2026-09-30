/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.layer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.evilctm.client.model.ReloadEpoch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.WeightedBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

/**
 * Model access off the render path. Every probe sets the Forge render layer it is asked for ({@code null} makes
 * multi-layer models return all their quads), restores it afterwards and never throws.
 */
public final class ModelProbe {
	private static final Function<IBlockState, IBakedModel> DEFAULT_LOOKUP =
			state -> Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(state);
	private static final EnumFacing[] FACES_AND_NULL = {EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, null};
	private static final long DEFAULT_SEED = 42L;
	/** Blocks with more states than this are probed through the asked state only. */
	static final int MAX_PROBED_STATES = 256;
	/**
	 * Seeds probed for a {@link WeightedBakedModel}, which picks {@code abs((int) rand >> 16) % totalWeight}: seeds
	 * {@code i << 16} for {@code i < WEIGHTED_SEEDS} reach every variant of a model whose weights sum to at most this.
	 */
	static final int WEIGHTED_SEEDS = 32;

	private static volatile Function<IBlockState, IBakedModel> lookup = DEFAULT_LOOKUP;
	private static volatile SpriteCache spriteCache = new SpriteCache(Long.MIN_VALUE);

	/**
	 * A sprite some model of the block uses, with the state whose model uses it and the render layers (as a
	 * {@code 1 << ordinal} mask) in which that model returns it. Layer-agnostic models return every sprite in every
	 * layer; layer-aware ones (Forge's multi-layer model) only in the layers that really draw it.
	 */
	public record ProbedSprite(IBlockState state, TextureAtlasSprite sprite, int layers) {
	}

	private ModelProbe() {
	}

	/** The block model for {@code state}, or {@code null} when it cannot be looked up. */
	@Nullable
	public static IBakedModel model(IBlockState state) {
		try {
			return lookup.apply(state);
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return null;
		}
	}

	/** The quads of {@code model} with the Forge render layer set to {@code null} (all layers of a multi-layer model). */
	public static List<BakedQuad> quads(@Nullable IBakedModel model, IBlockState state, @Nullable EnumFacing face) {
		return quads(model, state, face, null, DEFAULT_SEED);
	}

	/** The quads of {@code model} as the chunk mesher sees them in {@code layer} ({@code null} = all layers). */
	public static List<BakedQuad> quads(@Nullable IBakedModel model, IBlockState state, @Nullable EnumFacing face, @Nullable BlockRenderLayer layer, long seed) {
		if (model == null) {
			return Collections.emptyList();
		}
		BlockRenderLayer previous;
		try {
			previous = MinecraftForgeClient.getRenderLayer();
			ForgeHooksClient.setRenderLayer(layer);
		} catch (RuntimeException | LinkageError e) {
			return Collections.emptyList();
		}
		try {
			List<BakedQuad> quads = model.getQuads(state, face, seed);
			return quads != null ? quads : Collections.emptyList();
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return Collections.emptyList();
		} finally {
			ForgeHooksClient.setRenderLayer(previous);
		}
	}

	/**
	 * Every sprite the chunk mesher may see for the block of {@code rawState}: the union over all of the block's valid
	 * states (actual-state-only properties such as {@code snowy} included), the variants of weighted models and every
	 * face, keyed per render layer. Extra layers are granted from this, not from the raw state's model alone, because
	 * the mesher renders the actual state's model with a position seed. Cached per block until the next
	 * {@link ReloadEpoch} bump. Never throws.
	 */
	public static List<ProbedSprite> blockSprites(IBlockState rawState) {
		try {
			Collection<IBlockState> valid = rawState.getBlock().getBlockState().getValidStates();
			boolean wholeBlock = valid.size() <= MAX_PROBED_STATES;
			Object key = wholeBlock ? rawState.getBlock() : rawState;
			SpriteCache cache = spriteCache();
			List<ProbedSprite> cached = cache.map.get(key);
			if (cached != null) {
				return cached;
			}
			List<ProbedSprite> computed = computeSprites(wholeBlock ? valid : List.of(rawState));
			List<ProbedSprite> previous = cache.map.putIfAbsent(key, computed);
			return previous != null ? previous : computed;
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return Collections.emptyList();
		}
	}

	private static List<ProbedSprite> computeSprites(Collection<IBlockState> states) {
		Map<StateSprite, int[]> found = new LinkedHashMap<>();
		for (IBlockState state : states) {
			IBakedModel model = model(state);
			if (model == null) {
				continue;
			}
			int seeds = model instanceof WeightedBakedModel ? WEIGHTED_SEEDS : 1;
			for (BlockRenderLayer layer : LayerRouter.LAYERS) {
				int bit = LayerRouter.bit(layer);
				for (EnumFacing face : FACES_AND_NULL) {
					for (int i = 0; i < seeds; i++) {
						long seed = seeds == 1 ? DEFAULT_SEED : (long) i << 16;
						for (BakedQuad quad : quads(model, state, face, layer, seed)) {
							TextureAtlasSprite sprite = quad.getSprite();
							if (sprite != null) {
								found.computeIfAbsent(new StateSprite(state, sprite), k -> new int[1])[0] |= bit;
							}
						}
					}
				}
			}
		}
		List<ProbedSprite> out = new ArrayList<>(found.size());
		for (Map.Entry<StateSprite, int[]> e : found.entrySet()) {
			out.add(new ProbedSprite(e.getKey().state(), e.getKey().sprite(), e.getValue()[0]));
		}
		return List.copyOf(out);
	}

	private static SpriteCache spriteCache() {
		SpriteCache c = spriteCache;
		long epoch = ReloadEpoch.current();
		if (c.epoch != epoch) {
			c = new SpriteCache(epoch);
			spriteCache = c;
		}
		return c;
	}

	/** Test hook: replaces the model lookup; {@code null} restores the block renderer dispatcher. Drops cached probes. */
	public static void setModelLookupForTests(@Nullable Function<IBlockState, IBakedModel> testLookup) {
		lookup = testLookup != null ? testLookup : DEFAULT_LOOKUP;
		spriteCache = new SpriteCache(Long.MIN_VALUE);
	}

	private record StateSprite(IBlockState state, TextureAtlasSprite sprite) {
	}

	private static final class SpriteCache {
		final long epoch;
		final ConcurrentHashMap<Object, List<ProbedSprite>> map = new ConcurrentHashMap<>();

		SpriteCache(long epoch) {
			this.epoch = epoch;
		}
	}
}
