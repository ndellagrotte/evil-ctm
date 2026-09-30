/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.testutil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;

/** An {@link IExtendedBlockState} proxy around a plain state: {@code getClean()} returns it, other calls delegate. */
public final class FakeExtendedState {
	private FakeExtendedState() {
	}

	public static IExtendedBlockState wrap(IBlockState clean) {
		return (IExtendedBlockState) Proxy.newProxyInstance(FakeExtendedState.class.getClassLoader(), new Class<?>[]{IExtendedBlockState.class}, (proxy, method, args) -> {
			switch (method.getName()) {
				case "getClean":
					return clean;
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				case "toString":
					return "FakeExtendedState[" + clean + "]";
				default:
					break;
			}
			if (method.getDeclaringClass() == IExtendedBlockState.class) {
				return null;
			}
			try {
				return method.invoke(clean, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		});
	}
}
