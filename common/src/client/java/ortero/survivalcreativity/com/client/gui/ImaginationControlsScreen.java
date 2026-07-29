package ortero.survivalcreativity.com.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import ortero.survivalcreativity.com.client.imagination.ImaginationWorldControls;

/**
 * Categorized imagination-only world controls (time, weather, cleanup, fire).
 */
public class ImaginationControlsScreen extends Screen {
	private static final int BUTTON_W = 100;
	private static final int BUTTON_H = 20;
	private static final int GAP = 4;
	private static final int SECTION_GAP = 18;

	private final Screen parent;

	public ImaginationControlsScreen(Screen parent) {
		super(Component.translatable("screen.survivalcreativitymod.controls_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ImaginationWorldControls controls = ImaginationWorldControls.INSTANCE;
		int colW = BUTTON_W * 3 + GAP * 2;
		int left = width / 2 - colW / 2;
		int y = 36;

		y = addSection(left, y, "screen.survivalcreativitymod.controls_time", new ButtonSpec[]{
			new ButtonSpec("screen.survivalcreativitymod.time_day", () -> controls.setDay(minecraft)),
			new ButtonSpec("screen.survivalcreativitymod.time_night", () -> controls.setNight(minecraft)),
			new ButtonSpec("screen.survivalcreativitymod.time_freeze", () -> controls.toggleFreeze(minecraft))
		});

		y = addSection(left, y, "screen.survivalcreativitymod.controls_weather", new ButtonSpec[]{
			new ButtonSpec("screen.survivalcreativitymod.weather_clear", () -> controls.setClearWeather(minecraft)),
			new ButtonSpec("screen.survivalcreativitymod.weather_rain", () -> controls.setRain(minecraft)),
			new ButtonSpec("screen.survivalcreativitymod.weather_thunder", () -> controls.setThunder(minecraft))
		});

		y = addSection(left, y, "screen.survivalcreativitymod.controls_cleanup", new ButtonSpec[]{
			new ButtonSpec("screen.survivalcreativitymod.cleanup_friendly", () -> controls.removeFriendlyMobs(minecraft)),
			new ButtonSpec("screen.survivalcreativitymod.cleanup_hostile", () -> controls.removeHostileMobs(minecraft)),
			new ButtonSpec("screen.survivalcreativitymod.cleanup_items", () -> controls.removeDroppedItems(minecraft)),
			new ButtonSpec("screen.survivalcreativitymod.cleanup_fires", () -> controls.extinguishFires(minecraft))
		});

		addSection(left, y, "screen.survivalcreativitymod.controls_fire", new ButtonSpec[]{
			new ButtonSpec("screen.survivalcreativitymod.fire_spread", () -> controls.toggleFireSpread(minecraft))
		});

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(width / 2 - 50, height - 28, 100, 20).build());
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private int addSection(int left, int y, String titleKey, ButtonSpec[] specs) {
		int rowY = y + 14;
		for (int i = 0; i < specs.length; i++) {
			int col = i % 3;
			int row = i / 3;
			int x = left + col * (BUTTON_W + GAP);
			int by = rowY + row * (BUTTON_H + GAP);
			ButtonSpec spec = specs[i];
			addRenderableWidget(Button.builder(Component.translatable(spec.labelKey()), b -> spec.action().run())
				.bounds(x, by, BUTTON_W, BUTTON_H).build());
		}
		int rows = (specs.length + 2) / 3;
		return rowY + rows * (BUTTON_H + GAP) + SECTION_GAP;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
		graphics.centeredText(
			font,
			Component.translatable("screen.survivalcreativitymod.controls_hint"),
			width / 2,
			24,
			0xFFA0A0A0
		);

		int colW = BUTTON_W * 3 + GAP * 2;
		int left = width / 2 - colW / 2;
		int y = 36;
		y = drawSectionTitle(graphics, left, y, "screen.survivalcreativitymod.controls_time", 3);
		y = drawSectionTitle(graphics, left, y, "screen.survivalcreativitymod.controls_weather", 3);
		y = drawSectionTitle(graphics, left, y, "screen.survivalcreativitymod.controls_cleanup", 4);
		drawSectionTitle(graphics, left, y, "screen.survivalcreativitymod.controls_fire", 1);
	}

	private int drawSectionTitle(GuiGraphicsExtractor graphics, int left, int y, String key, int buttonCount) {
		graphics.text(font, Component.translatable(key), left, y, 0xFFE0E0E0);
		int rows = (buttonCount + 2) / 3;
		return y + 14 + rows * (BUTTON_H + GAP) + SECTION_GAP;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record ButtonSpec(String labelKey, Runnable action) {
	}
}
