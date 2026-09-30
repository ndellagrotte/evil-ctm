/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.impl.client;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.evilctm.api.client.ProcessingDataKey;
import com.evilctm.api.client.ProcessingDataKeyRegistry;
import com.evilctm.api.client.QuadProcessor;
import com.evilctm.client.model.QuadProcessors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.BlockRenderLayer;

/**
 * Per-thread processing state for one {@code transform} call. The pipeline pools one instance per thread and brackets
 * every call with {@link #begin} and {@link #end}; {@code end} drops every reference so nothing outlives the call.
 */
public class ProcessingContextImpl implements QuadProcessor.ProcessingContext {
	protected final List<BakedQuad> extraQuads = new ObjectArrayList<>();
	protected final List<BlockRenderLayer> overlayLayers = new ObjectArrayList<>();
	protected final List<BakedQuad> overlayQuads = new ObjectArrayList<>();
	/** Scratch output of one quad's processing chain (pipeline-internal). */
	protected final List<BakedQuad> chainOutput = new ObjectArrayList<>();
	protected Object[] processingData = new Object[Math.max(ProcessingDataKeyRegistry.get().getRegisteredAmount(), 4)];
	@Nullable
	protected BakedQuad replacement;
	@Nullable
	protected BlockRenderLayer currentLayer;
	@Nullable
	protected QuadProcessors.Tables tables;

	@Override
	public List<BakedQuad> getExtraQuads() {
		return extraQuads;
	}

	@Override
	public void replaceQuad(BakedQuad quad) {
		replacement = quad;
	}

	@Override
	public void emitOverlay(BlockRenderLayer layer, BakedQuad quad) {
		overlayLayers.add(layer);
		overlayQuads.add(quad);
	}

	@Override
	@Nullable
	public BlockRenderLayer currentLayer() {
		return currentLayer;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getData(ProcessingDataKey<T> key) {
		int index = key.getRawId();
		if (index >= processingData.length) {
			processingData = Arrays.copyOf(processingData, Math.max(index + 1, processingData.length * 2));
		}
		T data = (T) processingData[index];
		if (data == null) {
			data = key.getValueSupplier().get();
			processingData[index] = data;
		}
		return data;
	}

	/** Starts a call. {@code tables} is the processor snapshot the call reads. */
	public ProcessingContextImpl begin(@Nullable QuadProcessors.Tables tables, @Nullable BlockRenderLayer layer) {
		this.tables = tables;
		this.currentLayer = layer;
		return this;
	}

	/** Ends a call: clears every list and reference and resets resettable data. */
	public void end() {
		extraQuads.clear();
		chainOutput.clear();
		overlayLayers.clear();
		overlayQuads.clear();
		replacement = null;
		currentLayer = null;
		tables = null;
		resetData();
	}

	/** Kept for CleanContinuity callers and tests: clears the extras and resettable data. */
	public void reset() {
		extraQuads.clear();
		replacement = null;
		resetData();
	}

	@Nullable
	public QuadProcessors.Tables tables() {
		return tables;
	}

	public void clearReplacement() {
		replacement = null;
	}

	/** Returns the pending replacement and clears it. */
	@Nullable
	public BakedQuad takeReplacement() {
		BakedQuad r = replacement;
		replacement = null;
		return r;
	}

	public List<BakedQuad> chainOutput() {
		return chainOutput;
	}

	public int overlayCount() {
		return overlayQuads.size();
	}

	public BlockRenderLayer overlayLayer(int i) {
		return overlayLayers.get(i);
	}

	public BakedQuad overlayQuad(int i) {
		return overlayQuads.get(i);
	}

	public boolean isClean() {
		return extraQuads.isEmpty() && chainOutput.isEmpty() && overlayQuads.isEmpty() && replacement == null && currentLayer == null && tables == null;
	}

	protected void resetData() {
		List<ProcessingDataKey<?>> allResettable = ProcessingDataKeyRegistryImpl.INSTANCE.getAllResettable();
		for (int i = 0, n = allResettable.size(); i < n; i++) {
			resetData(allResettable.get(i));
		}
	}

	protected <T> void resetData(ProcessingDataKey<T> key) {
		T value = getDataOrNull(key);
		if (value != null) {
			key.getValueResetAction().accept(value);
		}
	}

	@SuppressWarnings("unchecked")
	@Nullable
	protected <T> T getDataOrNull(ProcessingDataKey<T> key) {
		int index = key.getRawId();
		return index < processingData.length ? (T) processingData[index] : null;
	}
}
