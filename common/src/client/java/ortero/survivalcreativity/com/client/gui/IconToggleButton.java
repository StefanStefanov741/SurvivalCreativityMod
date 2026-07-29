package ortero.survivalcreativity.com.client.gui;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Button with an item icon drawn inside, and an optional "active" highlight.
 * Label text stays fixed — active is shown visually, not by renaming.
 */
public class IconToggleButton extends Button {
	private final ItemStack icon;
	private final Supplier<Boolean> active;

	public IconToggleButton(
		int x,
		int y,
		int width,
		int height,
		ItemStack icon,
		Component label,
		Component tooltip,
		Supplier<Boolean> active,
		OnPress onPress
	) {
		super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
		this.icon = icon;
		this.active = active;
		setTooltip(Tooltip.create(tooltip));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractDefaultSprite(graphics);
		if (active.get()) {
			graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0x6622AA44);
		}
		graphics.item(icon, getX() + 3, getY() + 2);
		int textColor = active.get() ? 0xFFFFFF55 : 0xFFE0E0E0;
		graphics.text(
			Minecraft.getInstance().font,
			getMessage(),
			getX() + 22,
			getY() + (getHeight() - 8) / 2,
			textColor
		);
	}
}
