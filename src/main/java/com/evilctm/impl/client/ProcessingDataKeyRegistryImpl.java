/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.impl.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.evilctm.api.client.ProcessingDataKey;
import com.evilctm.api.client.ProcessingDataKeyRegistry;
import net.minecraft.util.ResourceLocation;

public final class ProcessingDataKeyRegistryImpl implements ProcessingDataKeyRegistry {
	public static final ProcessingDataKeyRegistryImpl INSTANCE = new ProcessingDataKeyRegistryImpl();

	private final Map<ResourceLocation, ProcessingDataKey<?>> keyMap = new ConcurrentHashMap<>();
	private final List<ProcessingDataKey<?>> allResettable = new CopyOnWriteArrayList<>();
	private final List<ProcessingDataKey<?>> allResettableView = Collections.unmodifiableList(allResettable);

	private volatile int registeredAmount = 0;
	private volatile boolean frozen;

	@Override
	public synchronized <T> ProcessingDataKey<T> registerKey(ResourceLocation id, Supplier<T> valueSupplier, Consumer<T> valueResetAction) {
		if (frozen) {
			throw new IllegalArgumentException("Cannot register processing data key for ID '" + id + "' to frozen registry");
		}
		ProcessingDataKey<?> oldKey = keyMap.get(id);
		if (oldKey != null) {
			throw new IllegalArgumentException("Cannot override processing data key registration for ID '" + id + "'");
		}
		ProcessingDataKeyImpl<T> key = new ProcessingDataKeyImpl<>(id, registeredAmount, valueSupplier, valueResetAction);
		keyMap.put(id, key);
		if (valueResetAction != null) {
			allResettable.add(key);
		}
		registeredAmount++;
		return key;
	}

	@Override
	@Nullable
	public ProcessingDataKey<?> getKey(ResourceLocation id) {
		return keyMap.get(id);
	}

	@Override
	public int getRegisteredAmount() {
		return registeredAmount;
	}

	public void init() {
	}

	public void setFrozen() {
		frozen = true;
	}

	public List<ProcessingDataKey<?>> getAllResettable() {
		return allResettableView;
	}
}
