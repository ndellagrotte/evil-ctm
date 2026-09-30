/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.api.client;

public interface CtmLoader<T extends CtmProperties> {
	CtmProperties.Factory<T> getPropertiesFactory();

	QuadProcessor.Factory<T> getProcessorFactory();

	CachingPredicates.Factory<T> getPredicatesFactory();
}
