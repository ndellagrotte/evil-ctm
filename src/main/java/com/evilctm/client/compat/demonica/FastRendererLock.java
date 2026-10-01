/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.compat.demonica;

import java.util.List;

import com.demonica.runtime.DemonicaRuntime;
import com.evilctm.client.EvilCtmClient;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.taumc.celeritas.api.OptionGroupConstructionEvent;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.Option;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionStorage;

/**
 * Keeps Demonica's fast block renderer on while Evil CTM is installed, since S20 is Evil CTM's only render path.
 * The setting is switched on in memory (Demonica reads it per block while meshing, so no reload is needed), and the
 * video options tick box is replaced with a greyed-out copy so it cannot be switched off. Demonica writes its whole
 * options object when any of its settings is saved, so the forced value reaches {@code demonica-options.json} then.
 *
 * <p>{@link DemonicaRuntime} is Demonica internals (checked against 0.6.0); both steps are guarded against
 * {@link LinkageError} and only log on failure, leaving {@link RenderPathStatus} to report the result.
 */
public final class FastRendererLock {
	/** Demonica's id for the option; it replaces Celeritas's own {@code celeritas:fast_block_renderer}. */
	private static final String OPTION_PATH = "fast_block_renderer";

	private static boolean registered;

	private FastRendererLock() {
	}

	public static synchronized void install() {
		if (registered) {
			return;
		}
		registered = true;
		force();
		try {
			OptionGroupConstructionEvent.BUS.addListener(FastRendererLock::onGroup);
		} catch (LinkageError | RuntimeException e) {
			EvilCtmClient.LOGGER.warn("Could not lock the fast block renderer option in the video settings screen", e);
		}
	}

	private static void force() {
		try {
			DemonicaRuntime.options().performance.useFastBlockRenderer = true;
		} catch (LinkageError | RuntimeException e) {
			EvilCtmClient.LOGGER.error("Could not switch on Demonica's fast block renderer. Is this Demonica version supported?", e);
		}
	}

	private static boolean enabled() {
		try {
			return DemonicaRuntime.options().performance.useFastBlockRenderer;
		} catch (LinkageError | RuntimeException e) {
			return false;
		}
	}

	// Registered after Demonica's listener (Demonica registers at construction, this at preInit), so Demonica's tick
	// box is already in the group. Any option with this path is replaced, whichever mod owns it.
	private static void onGroup(OptionGroupConstructionEvent event) {
		List<Option<?>> options = event.getOptions();
		for (int i = 0; i < options.size(); i++) {
			Option<?> option = options.get(i);
			OptionIdentifier<?> id = option.getId();
			if (id != null && OPTION_PATH.equals(id.getPath()) && id.getType() == boolean.class) {
				force();
				options.set(i, locked(option));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Option<Boolean> locked(Option<?> original) {
		return OptionImpl.createBuilder(boolean.class, (OptionStorage<Object>) original.getStorage())
			.setId(original.getId().cast())
			.setName(original.getName())
			.setTooltip(TextComponent.translatable("options.evilctm.fast_block_renderer_locked.tooltip"))
			.setControl(TickBoxControl::new)
			.setImpact(original.getImpact())
			.setBinding((storage, value) -> force(), storage -> enabled())
			.setEnabled(false)
			.build();
	}
}
