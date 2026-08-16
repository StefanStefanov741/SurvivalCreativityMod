package ortero.survivalcreativity.com.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import ortero.survivalcreativity.com.client.imagination.Imagination;
import ortero.survivalcreativity.com.client.imagination.ImaginationMaterials;

/**
 * Shows place (−) and break (+) material counts for one hologram, with block icons.
 */
public class ImaginationBlocksScreen extends Screen {
	private static final int ROW_HEIGHT = 18;

	private final Screen parent;
	private final Imagination imagination;
	private final ImaginationMaterials.Summary summary;
	private final List<Row> rows = new ArrayList<>();
	private int scroll;

	private record Row(String text, int color, ItemStack icon) {
	}

	public ImaginationBlocksScreen(Screen parent, Imagination imagination) {
		super(Component.translatable("screen.survivalcreativitymod.blocks_title", imagination.name()));
		this.parent = parent;
		this.imagination = imagination;
		this.summary = ImaginationMaterials.summarize(imagination);
		buildRows();
	}

	private void buildRows() {
		rows.clear();
		if (!summary.supported()) {
			rows.add(new Row(
				Component.translatable("screen.survivalcreativitymod.blocks_legacy").getString(),
				0xFFFFAA55,
				ItemStack.EMPTY
			));
			return;
		}
		if (!summary.toPlace().isEmpty()) {
			rows.add(new Row(Component.translatable("screen.survivalcreativitymod.blocks_place").getString(), 0xFFFFFFFF, ItemStack.EMPTY));
			for (ImaginationMaterials.Entry entry : summary.toPlace()) {
				rows.add(new Row(entry.line(), 0xFFFFAAAA, entry.icon()));
			}
		}
		if (!summary.toBreak().isEmpty()) {
			if (!rows.isEmpty()) {
				rows.add(new Row("", 0xFFE0E0E0, ItemStack.EMPTY));
			}
			rows.add(new Row(Component.translatable("screen.survivalcreativitymod.blocks_break").getString(), 0xFFFFFFFF, ItemStack.EMPTY));
			for (ImaginationMaterials.Entry entry : summary.toBreak()) {
				rows.add(new Row(entry.line(), 0xFFAAFFAA, entry.icon()));
			}
		}
		if (rows.isEmpty()) {
			rows.add(new Row(Component.translatable("screen.survivalcreativitymod.blocks_empty").getString(), 0xFFA0A0A0, ItemStack.EMPTY));
		}
	}

	@Override
	protected void init() {
		clearWidgets();
		int bottomY = height - 36;

		addRenderableWidget(Button.builder(
			Component.translatable("screen.survivalcreativitymod.blocks_back"),
			b -> onClose()
		).bounds(width / 2 - 160, bottomY, 100, 20).build());

		if (summary.supported()) {
			addRenderableWidget(Button.builder(
				Component.translatable("screen.survivalcreativitymod.blocks_copy"),
				b -> copyToClipboard()
			).bounds(width / 2 - 50, bottomY, 100, 20).build());

			addRenderableWidget(Button.builder(
				Component.translatable("screen.survivalcreativitymod.blocks_share"),
				b -> shareInChat()
			).bounds(width / 2 + 60, bottomY, 100, 20).build());
		}

		int visible = Math.max(1, (height - 90) / ROW_HEIGHT);
		if (rows.size() > visible) {
			addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
				scroll = Math.max(0, scroll - 1);
			}).bounds(width / 2 + 180, 48, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
				scroll = Math.min(Math.max(0, rows.size() - visible), scroll + 1);
			}).bounds(width / 2 + 180, 72, 20, 20).build());
		}
	}

	private void copyToClipboard() {
		String text = summary.asClipboardText(imagination.name());
		minecraft.keyboardHandler.setClipboard(text);
		if (minecraft.player != null) {
			minecraft.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.blocks_copied"));
		}
	}

	private void shareInChat() {
		if (minecraft.getConnection() == null) {
			return;
		}
		for (String message : summary.asChatMessages(imagination.name())) {
			minecraft.getConnection().sendChat(message);
		}
		if (minecraft.player != null) {
			minecraft.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.blocks_shared"));
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
		graphics.centeredText(
			font,
			Component.translatable(
				summary.supported()
					? "screen.survivalcreativitymod.blocks_hint"
					: "screen.survivalcreativitymod.blocks_legacy_hint"
			),
			width / 2,
			28,
			0xFFA0A0A0
		);

		int startY = 48;
		int visible = Math.max(1, (height - 90) / ROW_HEIGHT);
		int end = Math.min(rows.size(), scroll + visible);
		int x = width / 2 - 160;
		for (int i = scroll; i < end; i++) {
			Row row = rows.get(i);
			int y = startY + (i - scroll) * ROW_HEIGHT;
			if (!row.icon().isEmpty()) {
				graphics.item(row.icon(), x, y);
				graphics.text(font, row.text(), x + 20, y + 4, row.color());
			} else if (!row.text().isEmpty()) {
				// Wrap legacy message across the content width
				if (!summary.supported()) {
					graphics.text(font, row.text(), x, y + 4, row.color());
				} else {
					graphics.text(font, row.text(), x, y + 4, row.color());
				}
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
