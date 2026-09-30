/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.api.client;

public interface ProcessingDataProvider {
	<T> T getData(ProcessingDataKey<T> key);
}
