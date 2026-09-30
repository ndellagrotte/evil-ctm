/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.proxy;

import com.evilctm.client.EvilCtmClient;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy implements IProxy {
	@Override
	public void preInit(FMLPreInitializationEvent event) {
		EvilCtmClient.preInit();
	}

	@Override
	public void init(FMLInitializationEvent event) {
		EvilCtmClient.init();
	}

	@Override
	public void postInit(FMLPostInitializationEvent event) {
		EvilCtmClient.postInit();
	}
}
