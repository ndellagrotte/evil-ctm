/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.api.client;

import javax.annotation.Nullable;

import com.evilctm.impl.client.CtmLoaderRegistryImpl;

public interface CtmLoaderRegistry {
	static CtmLoaderRegistry get() {
		return CtmLoaderRegistryImpl.INSTANCE;
	}

	void registerLoader(String method, CtmLoader<?> loader);

	@Nullable
	CtmLoader<?> getLoader(String method);
}
