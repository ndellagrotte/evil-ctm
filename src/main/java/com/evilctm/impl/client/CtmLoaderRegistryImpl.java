/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.impl.client;

import java.util.Map;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import com.evilctm.api.client.CtmLoader;
import com.evilctm.api.client.CtmLoaderRegistry;

public final class CtmLoaderRegistryImpl implements CtmLoaderRegistry {
	public static final CtmLoaderRegistryImpl INSTANCE = new CtmLoaderRegistryImpl();

	private final Map<String, CtmLoader<?>> loaderMap = new Object2ObjectOpenHashMap<>();

	@Override
	public void registerLoader(String method, CtmLoader<?> loader) {
		loaderMap.put(method, loader);
	}

	@Override
	@Nullable
	public CtmLoader<?> getLoader(String method) {
		return loaderMap.get(method);
	}
}
