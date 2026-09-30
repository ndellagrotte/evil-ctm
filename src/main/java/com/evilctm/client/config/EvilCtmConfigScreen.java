/* Evil CTM. SPDX-License-Identifier: LGPL-3.0-only. Derived from CleanContinuity / NeoContinuity / Continuity (LGPL-3.0); see NOTICE.md. */
package com.evilctm.client.config;

import java.util.List;

import com.evilctm.client.compat.demonica.RenderPathStatus;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * One toggle per boolean option (keys {@code options.evilctm.<key>}), then Done. Options read only while rules are
 * loaded ({@code builtin_default_rules}, {@code ctm_mod_textures}) trigger one resource reload when the screen closes.
 */
public class EvilCtmConfigScreen extends GuiScreen {
	private static final int DONE_ID = 1000;
	private static final int STATUS_Y = 42;
	private static final int LINE_HEIGHT = 10;
	private static final int BUTTON_HEIGHT = 20;
	private static final int MIN_PITCH = 21;
	private static final int MAX_PITCH = 24;

	private final GuiScreen parent;
	private final EvilCtmConfig config;
	private final List<Option.BooleanOption> toggles = new ObjectArrayList<>();
	private final boolean initialBuiltinRules;
	private final boolean initialCtmModTextures;
	private RenderPathStatus.Problem status = RenderPathStatus.Problem.OK;

	public EvilCtmConfigScreen(GuiScreen parent, EvilCtmConfig config) {
		this.parent = parent;
		this.config = config;
		this.initialBuiltinRules = config.builtinDefaultRules.get();
		this.initialCtmModTextures = config.ctmModTextures.get();
	}

	@Override
	public void initGui() {
		buttonList.clear();
		toggles.clear();
		status = RenderPathStatus.evaluate();
		for (Option<?> option : config.getOptionMapView().values()) {
			if (option instanceof Option.BooleanOption booleanOption) {
				toggles.add(booleanOption);
			}
		}
		Layout layout = layout(height, statusLines().size(), toggles.size());
		for (int i = 0; i < toggles.size(); i++) {
			Option.BooleanOption option = toggles.get(i);
			int row = layout.twoColumns() ? i / 2 : i;
			int x = layout.twoColumns() ? (i % 2 == 0 ? width / 2 - 155 : width / 2 + 5) : width / 2 - 100;
			buttonList.add(new GuiButton(i, x, layout.top() + row * layout.pitch(), layout.twoColumns() ? 150 : 200, BUTTON_HEIGHT, optionText(option)));
		}
		buttonList.add(new GuiButton(DONE_ID, width / 2 - 100, layout.doneY(), 200, BUTTON_HEIGHT, I18n.format("gui.done")));
	}

	/** Where the toggles and Done go below {@code statusLines} lines of status text; one column while it fits. */
	record Layout(int top, int pitch, boolean twoColumns, int doneY) {
	}

	static Layout layout(int height, int statusLines, int count) {
		int top = STATUS_Y + statusLines * LINE_HEIGHT + 6;
		// room for the toggles above Done (its 8 px gap and height) and a 4 px margin
		int available = height - 4 - (BUTTON_HEIGHT + 8) - top;
		int pitch = Math.min(MAX_PITCH, available / Math.max(1, count));
		boolean twoColumns = pitch < MIN_PITCH;
		int rows = twoColumns ? (count + 1) / 2 : count;
		if (twoColumns) {
			pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, available / Math.max(1, rows)));
		}
		int doneY = top + Math.max(0, rows - 1) * pitch + BUTTON_HEIGHT + 8;
		return new Layout(top, pitch, twoColumns, doneY);
	}

	private List<String> statusLines() {
		List<String> lines = new ObjectArrayList<>();
		if (status == RenderPathStatus.Problem.OK) {
			lines.add(I18n.format("evilctm.status.ok"));
		} else {
			lines.addAll(fontRenderer.listFormattedStringToWidth(I18n.format(status.translationKey()), width - 40));
			lines.addAll(fontRenderer.listFormattedStringToWidth(I18n.format(status.hintKey()), width - 40));
		}
		return lines;
	}

	@Override
	public void onGuiClosed() {
		// Done, Escape or any other way out: rules are only (re)loaded by a resource reload.
		if (config.builtinDefaultRules.get() != initialBuiltinRules || config.ctmModTextures.get() != initialCtmModTextures) {
			Minecraft.getMinecraft().scheduleResourcesRefresh();
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button.id == DONE_ID) {
			config.save();
			mc.displayGuiScreen(parent);
			return;
		}
		if (button.id >= 0 && button.id < toggles.size()) {
			Option.BooleanOption option = toggles.get(button.id);
			option.set(!option.get());
			config.save();
			button.displayString = optionText(option);
			reloadRenderers();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();
		drawCenteredString(fontRenderer, I18n.format("options.evilctm.title"), width / 2, 30, 0xFFFFFF);
		int y = STATUS_Y;
		int color = status == RenderPathStatus.Problem.OK ? 0x55FF55 : 0xFF5555;
		for (String line : statusLines()) {
			drawCenteredString(fontRenderer, line, width / 2, y, color);
			y += LINE_HEIGHT;
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
		for (GuiButton button : buttonList) {
			if (button.id >= 0 && button.id < toggles.size() && button.isMouseOver()) {
				String key = "options.evilctm." + toggles.get(button.id).getKey() + ".tooltip";
				if (I18n.hasKey(key)) {
					drawHoveringText(fontRenderer.listFormattedStringToWidth(I18n.format(key), 200), mouseX, mouseY);
				}
			}
		}
	}

	private static String optionText(Option.BooleanOption option) {
		return I18n.format("options.evilctm." + option.getKey()) + ": " + I18n.format(option.get() ? "options.on" : "options.off");
	}

	private static void reloadRenderers() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.renderGlobal != null) {
			mc.renderGlobal.loadRenderers();
		}
	}
}
