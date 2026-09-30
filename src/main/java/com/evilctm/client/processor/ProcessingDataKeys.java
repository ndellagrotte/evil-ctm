/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.processor;

import java.util.function.Supplier;

import com.evilctm.api.client.ProcessingDataKey;
import com.evilctm.api.client.ProcessingDataKeyRegistry;
import com.evilctm.client.EvilCtmClient;
import net.minecraft.util.math.BlockPos;

public final class ProcessingDataKeys {
	public static final ProcessingDataKey<BlockPos.MutableBlockPos> MUTABLE_POS = create("mutable_pos", BlockPos.MutableBlockPos::new);

	private static <T> ProcessingDataKey<T> create(String id, Supplier<T> valueSupplier) {
		return ProcessingDataKeyRegistry.get().registerKey(EvilCtmClient.asId(id), valueSupplier);
	}

	public static void init() {
	}

	private ProcessingDataKeys() {
	}
}
