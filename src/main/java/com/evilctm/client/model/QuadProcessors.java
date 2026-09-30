/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.model;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.evilctm.api.client.CachingPredicates;
import com.evilctm.api.client.LayerTargetingProcessor;
import com.evilctm.api.client.QuadProcessor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.common.property.IExtendedBlockState;

/**
 * The published processor set. A reload builds a new immutable {@link Tables} (with a fresh slice cache) and
 * publishes it in one volatile write; readers take {@link #current()} once per call.
 */
public final class QuadProcessors {
	private static volatile Tables current = Tables.EMPTY;

	private QuadProcessors() {
	}

	public static Tables current() {
		return current;
	}

	public static long generation() {
		return current.generation;
	}

	/** Publishes a new processor set and bumps {@link ReloadEpoch}. */
	public static synchronized void reload(List<ProcessorHolder> processorHolders) {
		current = new Tables(processorHolders.toArray(new ProcessorHolder[0]), current.generation + 1);
		ReloadEpoch.bump();
	}

	/** The cache key for a state: the clean state of an extended state, otherwise the state itself. */
	public static IBlockState cacheKey(IBlockState state) {
		return state instanceof IExtendedBlockState extended ? extended.getClean() : state;
	}

	/** Kept for CleanContinuity-style callers: the slice for {@code state} and {@code sprite} in the current tables. */
	public static Slice getSlice(IBlockState state, TextureAtlasSprite sprite) {
		return current.slice(state, sprite);
	}

	static Slice computeSlice(ProcessorHolder[] holders, IBlockState state, TextureAtlasSprite sprite) {
		List<QuadProcessor> processorList = new ObjectArrayList<>();
		List<QuadProcessor> multipassProcessorList = new ObjectArrayList<>();

		for (ProcessorHolder holder : holders) {
			QuadProcessor processor = holder.processor();
			CachingPredicates predicates = holder.predicates();
			if (!predicates.affectsBlockStates() || predicates.affectsBlockState(state)) {
				if (predicates.affectsSprites()) {
					if (predicates.affectsSprite(sprite)) {
						processorList.add(processor);
						if (predicates.isValidForMultipass()) {
							multipassProcessorList.add(processor);
						}
					}
				} else {
					processorList.add(processor);
				}
			}
		}

		if (processorList.isEmpty()) {
			return Slice.EMPTY;
		}
		QuadProcessor[] processors = processorList.toArray(new QuadProcessor[0]);
		QuadProcessor[] multipassProcessors = multipassProcessorList.toArray(new QuadProcessor[0]);
		return new Slice(processors, multipassProcessors);
	}

	public record ProcessorHolder(QuadProcessor processor, CachingPredicates predicates) {
	}

	public record Slice(QuadProcessor[] processors, QuadProcessor[] multipassProcessors) {
		public static final Slice EMPTY = new Slice(new QuadProcessor[0], new QuadProcessor[0]);
	}

	/** One published processor set plus its (state, sprite) slice cache. */
	public static final class Tables {
		public static final Tables EMPTY = new Tables(new ProcessorHolder[0], 0);

		final ProcessorHolder[] holders;
		public final long generation;
		public final boolean anyLayerTargeting;
		final ConcurrentHashMap<IBlockState, ConcurrentHashMap<TextureAtlasSprite, Slice>> cache = new ConcurrentHashMap<>();

		Tables(ProcessorHolder[] holders, long generation) {
			this.holders = holders;
			this.generation = generation;
			boolean layerTargeting = false;
			for (ProcessorHolder holder : holders) {
				if (holder.processor() instanceof LayerTargetingProcessor) {
					layerTargeting = true;
					break;
				}
			}
			this.anyLayerTargeting = layerTargeting;
		}

		/** The processors for {@code state} (any form; keyed by {@link #cacheKey}) and {@code sprite}. */
		public Slice slice(IBlockState state, TextureAtlasSprite sprite) {
			if (holders.length == 0) {
				return Slice.EMPTY;
			}
			IBlockState key = cacheKey(state);
			ConcurrentHashMap<TextureAtlasSprite, Slice> inner = cache.get(key);
			if (inner == null) {
				ConcurrentHashMap<TextureAtlasSprite, Slice> created = new ConcurrentHashMap<>(4);
				inner = cache.putIfAbsent(key, created);
				if (inner == null) {
					inner = created;
				}
			}
			Slice slice = inner.get(sprite);
			if (slice == null) {
				Slice computed = computeSlice(holders, key, sprite);
				slice = inner.putIfAbsent(sprite, computed);
				if (slice == null) {
					slice = computed;
				}
			}
			return slice;
		}

		public boolean isEmpty() {
			return holders.length == 0;
		}

		public int size() {
			return holders.length;
		}
	}
}
