package ortero.survivalcreativity.com.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.client.imagination.Imagination;
import ortero.survivalcreativity.com.client.imagination.ImaginationManager;
import ortero.survivalcreativity.com.client.imagination.ImaginationStorage;

public class ImaginationListScreen extends Screen {
	private final List<Imagination> imaginations;
	private int scroll;

	public ImaginationListScreen(List<Imagination> imaginations) {
		super(Component.translatable("screen.survivalcreativitymod.list"));
		this.imaginations = new ArrayList<>(imaginations);
	}

	@Override
	protected void init() {
		clearWidgets();
		int startY = 48;
		int visible = Math.max(1, (height - 110) / 24);
		int end = Math.min(imaginations.size(), scroll + visible);

		for (int i = scroll; i < end; i++) {
			Imagination imagination = imaginations.get(i);
			int row = i - scroll;
			int y = startY + row * 24;
			String label = imagination.name() + " (" + imagination.changes().size() + ")";

			addRenderableWidget(Button.builder(Component.literal(label), b -> {
				ImaginationManager.INSTANCE.startPreview(minecraft, imagination);
			}).bounds(width / 2 - 200, y, 145, 20).build());

			addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.blocks_btn"), b -> {
				minecraft.gui.setScreen(new ImaginationBlocksScreen(this, imagination));
			}).bounds(width / 2 - 50, y, 55, 20).build());

			addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.edit"), b -> {
				ImaginationManager.INSTANCE.enterEdit(minecraft, imagination);
			}).bounds(width / 2 + 10, y, 50, 20).build());

			addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.delete"), b -> {
				try {
					ImaginationStorage.delete(minecraft, imagination.id());
					imaginations.removeIf(entry -> entry.id().equals(imagination.id()));
					scroll = Math.min(scroll, Math.max(0, imaginations.size() - 1));
					rebuildWidgets();
				} catch (IOException e) {
					SurvivalCreativityMod.LOGGER.error("Failed to delete imagination", e);
				}
			}).bounds(width / 2 + 65, y, 70, 20).build());
		}

		if (imaginations.size() > visible) {
			addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
				scroll = Math.max(0, scroll - 1);
				rebuildWidgets();
			}).bounds(width / 2 - 230, startY, 20, 20).build());

			addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
				scroll = Math.min(Math.max(0, imaginations.size() - visible), scroll + 1);
				rebuildWidgets();
			}).bounds(width / 2 - 230, startY + 24, 20, 20).build());
		}

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
			.bounds(width / 2 - 50, height - 36, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("screen.survivalcreativitymod.list_hint"),
			width / 2, 28, 0xFFA0A0A0);
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
