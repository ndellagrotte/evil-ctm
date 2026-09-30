/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.evilctm.api.client.ProcessingDataKeyRegistry;
import com.evilctm.client.processor.ProcessingDataKeys;
import com.evilctm.impl.client.ProcessingContextImpl;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class EvilCtmClientProcessingDataTest {
	@Test
	void registerLoadersInitializesProcessingDataBeforeContextUse() {
		EvilCtmClient.registerLoaders();

		assertTrue(ProcessingDataKeyRegistry.get().getRegisteredAmount() > 0);
		ProcessingContextImpl context = new ProcessingContextImpl();
		assertInstanceOf(BlockPos.MutableBlockPos.class, context.getData(ProcessingDataKeys.MUTABLE_POS));
	}
}
