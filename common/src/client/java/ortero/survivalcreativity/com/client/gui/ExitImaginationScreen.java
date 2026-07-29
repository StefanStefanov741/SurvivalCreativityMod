package ortero.survivalcreativity.com.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown when leaving imagination edit with unsaved changes.
 */
public class ExitImaginationScreen extends Screen {
	private final Runnable onSave;
	private final Runnable onDiscard;

	public ExitImaginationScreen(Runnable onSave, Runnable onDiscard) {
		super(Component.translatable("screen.survivalcreativitymod.exit_title"));
		this.onSave = onSave;
		this.onDiscard = onDiscard;
	}

	@Override
	protected void init() {
		int y = height / 2 + 8;
		addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.exit_save"), button -> {
			onClose();
			onSave.run();
		}).bounds(width / 2 - 100, y, 200, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.exit_discard"), button -> {
			onClose();
			onDiscard.run();
		}).bounds(width / 2 - 100, y + 24, 200, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.exit_keep"), button -> onClose())
			.bounds(width / 2 - 100, y + 48, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
		graphics.centeredText(
			font,
			Component.translatable("screen.survivalcreativitymod.exit_hint"),
			width / 2,
			height / 2 - 22,
			0xFFAAAAAA
		);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
