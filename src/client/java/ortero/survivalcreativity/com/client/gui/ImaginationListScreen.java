package ortero.survivalcreativity.com.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.client.imagination.HologramSettings;
import ortero.survivalcreativity.com.client.imagination.Imagination;
import ortero.survivalcreativity.com.client.imagination.ImaginationManager;
import ortero.survivalcreativity.com.client.imagination.ImaginationShare;
import ortero.survivalcreativity.com.client.imagination.ImaginationStorage;

public class ImaginationListScreen extends Screen {
	private final List<Imagination> imaginations;
	private final String worldLabel;
	private int scroll;

	public ImaginationListScreen(List<Imagination> imaginations) {
		super(Component.translatable("screen.survivalcreativitymod.list"));
		this.imaginations = new ArrayList<>(imaginations);
		this.worldLabel = ImaginationStorage.worldLabel(Minecraft.getInstance());
	}

	@Override
	protected void init() {
		clearWidgets();
		int startY = 52;
		int visible = Math.max(1, (height - 140) / 24);
		int end = Math.min(imaginations.size(), scroll + visible);

		for (int i = scroll; i < end; i++) {
			Imagination imagination = imaginations.get(i);
			int row = i - scroll;
			int y = startY + row * 24;
			String label = imagination.name() + " (" + imagination.changes().size() + ")";

			addRenderableWidget(Button.builder(Component.literal(label), b -> {
				ImaginationManager.INSTANCE.startPreview(minecraft, imagination);
			}).bounds(width / 2 - 210, y, 115, 20).build());

			addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.blocks_btn"), b -> {
				minecraft.gui.setScreen(new ImaginationBlocksScreen(this, imagination));
			}).bounds(width / 2 - 90, y, 50, 20).build());

			addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.edit"), b -> {
				ImaginationManager.INSTANCE.enterEdit(minecraft, imagination);
			}).bounds(width / 2 - 35, y, 45, 20).build());

			addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.share"), b -> {
				ImaginationShare.share(minecraft, imagination);
			}).bounds(width / 2 + 15, y, 50, 20).build());

			addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.delete"), b -> {
				try {
					ImaginationStorage.delete(minecraft, imagination.id());
					imaginations.removeIf(entry -> entry.id().equals(imagination.id()));
					scroll = Math.min(scroll, Math.max(0, imaginations.size() - 1));
					rebuildWidgets();
				} catch (IOException e) {
					SurvivalCreativityMod.LOGGER.error("Failed to delete imagination", e);
				}
			}).bounds(width / 2 + 70, y, 55, 20).build());
		}

		if (imaginations.size() > visible) {
			addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
				scroll = Math.max(0, scroll - 1);
				rebuildWidgets();
			}).bounds(width / 2 - 240, startY, 20, 20).build());

			addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
				scroll = Math.min(Math.max(0, imaginations.size() - visible), scroll + 1);
				rebuildWidgets();
			}).bounds(width / 2 - 240, startY + 24, 20, 20).build());
		}

		double sliderValue = (HologramSettings.opacityPercent() - HologramSettings.MIN_OPACITY_PERCENT)
			/ (double) (HologramSettings.MAX_OPACITY_PERCENT - HologramSettings.MIN_OPACITY_PERCENT);
		addRenderableWidget(new AbstractSliderButton(
			width / 2 - 100,
			height - 62,
			200,
			20,
			opacityMessage(),
			sliderValue
		) {
			@Override
			protected void updateMessage() {
				setMessage(opacityMessage());
			}

			@Override
			protected void applyValue() {
				int percent = HologramSettings.MIN_OPACITY_PERCENT
					+ (int) Math.round(value * (HologramSettings.MAX_OPACITY_PERCENT - HologramSettings.MIN_OPACITY_PERCENT));
				HologramSettings.setOpacityPercent(Mth.clamp(
					percent,
					HologramSettings.MIN_OPACITY_PERCENT,
					HologramSettings.MAX_OPACITY_PERCENT
				));
			}
		});

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
			.bounds(width / 2 - 50, height - 36, 100, 20).build());
	}

	private static Component opacityMessage() {
		return Component.translatable(
			"screen.survivalcreativitymod.hologram_opacity",
			HologramSettings.opacityPercent()
		);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("screen.survivalcreativitymod.list_world", worldLabel),
			width / 2, 24, 0xFFA0A0A0);
		graphics.centeredText(font, Component.translatable("screen.survivalcreativitymod.list_hint"),
			width / 2, 36, 0xFF808080);
		if (imaginations.isEmpty()) {
			graphics.centeredText(font, Component.translatable("screen.survivalcreativitymod.empty_list"),
				width / 2, height / 2, 0xFFA0A0A0);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
