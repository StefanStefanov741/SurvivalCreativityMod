package ortero.survivalcreativity.com.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SaveImaginationScreen extends Screen {
	private final String initialName;
	private final Consumer<String> onConfirm;
	private EditBox nameBox;

	public SaveImaginationScreen(String initialName, Consumer<String> onConfirm) {
		super(Component.translatable("screen.survivalcreativitymod.save"));
		this.initialName = initialName;
		this.onConfirm = onConfirm;
	}

	@Override
	protected void init() {
		nameBox = new EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20,
			Component.translatable("screen.survivalcreativitymod.name"));
		nameBox.setValue(initialName);
		nameBox.setMaxLength(64);
		addRenderableWidget(nameBox);

		addRenderableWidget(Button.builder(Component.translatable("screen.survivalcreativitymod.confirm"), button -> {
			onConfirm.accept(nameBox.getValue());
			onClose();
		}).bounds(width / 2 - 100, height / 2 + 20, 95, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
			.bounds(width / 2 + 5, height / 2 + 20, 95, 20).build());

		setInitialFocus(nameBox);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
