/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.impl.client;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.evilctm.api.client.ProcessingDataKey;
import net.minecraft.util.ResourceLocation;

public class ProcessingDataKeyImpl<T> implements ProcessingDataKey<T> {
	protected final ResourceLocation id;
	protected final int rawId;
	protected final Supplier<T> valueSupplier;
	protected final Consumer<T> valueResetAction;

	public ProcessingDataKeyImpl(ResourceLocation id, int rawId, Supplier<T> valueSupplier, Consumer<T> valueResetAction) {
		this.id = id;
		this.rawId = rawId;
		this.valueSupplier = valueSupplier;
		this.valueResetAction = valueResetAction;
	}

	@Override
	public ResourceLocation getId() {
		return id;
	}

	@Override
	public int getRawId() {
		return rawId;
	}

	@Override
	public Supplier<T> getValueSupplier() {
		return valueSupplier;
	}

	@Override
	@Nullable
	public Consumer<T> getValueResetAction() {
		return valueResetAction;
	}
}
