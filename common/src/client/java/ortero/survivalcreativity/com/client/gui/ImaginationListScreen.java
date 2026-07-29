package ortero.survivalcreativity.com.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.client.imagination.HologramSettings;
import ortero.survivalcreativity.com.client.imagination.Imagination;
import ortero.survivalcreativity.com.client.imagination.ImaginationManager;
import ortero.survivalcreativity.com.client.imagination.ImaginationShare;
import ortero.survivalcreativity.com.client.imagination.ImaginationStorage;

public class ImaginationListScreen extends Screen {
	private static final int LIST_START_Y = 44;
	private static final int CONTROLS_HEIGHT = 148;
	private static final int LEFT = 8;
	private static final int SLICE_FIELD_WIDTH = 48;
	private static final int ARROW_SIZE = 18;
	private static final int TOGGLE_WIDTH = 148;

	private final List<Imagination> imaginations;
	private final String worldLabel;
	private int scroll;

	private final List<EditBox> sliceFields = new ArrayList<>();

	public ImaginationListScreen(List<Imagination> imaginations) {
		super(Component.translatable("screen.survivalcreativitymod.list"));
		this.imaginations = new ArrayList<>(imaginations);
		this.worldLabel = ImaginationStorage.worldLabel(Minecraft.getInstance());
	}

	@Override
	protected void init() {
		clearWidgets();
		sliceFields.clear();

		int controlsTop = height - CONTROLS_HEIGHT;
		int visible = Math.max(1, (controlsTop - LIST_START_Y - 8) / 24);
		int end = Math.min(imaginations.size(), scroll + visible);

		for (int i = scroll; i < end; i++) {
			Imagination imagination = imaginations.get(i);
			int row = i - scroll;
			int y = LIST_START_Y + row * 24;
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
			}).bounds(width / 2 - 240, LIST_START_Y, 20, 20).build());

			addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
				scroll = Math.min(Math.max(0, imaginations.size() - visible), scroll + 1);
				rebuildWidgets();
			}).bounds(width / 2 - 240, LIST_START_Y + 24, 20, 20).build());
		}

		addLeftControls(controlsTop);
		addOpacitySlider();
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
			.bounds(width / 2 - 50, height - 28, 100, 20).build());
	}

	private void addLeftControls(int controlsTop) {
		addRenderableWidget(new IconToggleButton(
			LEFT,
			controlsTop,
			TOGGLE_WIDTH,
			20,
			new ItemStack(Items.CONCRETE.pick(DyeColor.CYAN)),
			Component.translatable("screen.survivalcreativitymod.toggle_place_blocks"),
			Component.translatable("screen.survivalcreativitymod.hide_placements_tooltip"),
			HologramSettings::hidePlacements,
			b -> HologramSettings.toggleHidePlacements()
		));

		addRenderableWidget(new IconToggleButton(
			LEFT + TOGGLE_WIDTH + 4,
			controlsTop,
			TOGGLE_WIDTH,
			20,
			new ItemStack(Items.IRON_PICKAXE),
			Component.translatable("screen.survivalcreativitymod.toggle_break_blocks"),
			Component.translatable("screen.survivalcreativitymod.hide_breaks_tooltip"),
			HologramSettings::hideBreaks,
			b -> HologramSettings.toggleHideBreaks()
		));

		addRenderableWidget(Button.builder(
			Component.translatable("screen.survivalcreativitymod.slice_clear"),
			b -> resetSliceToCurrentHologram()
		).bounds(LEFT + (TOGGLE_WIDTH + 4) * 2, controlsTop, 80, 20)
			.tooltip(Tooltip.create(Component.translatable("screen.survivalcreativitymod.slice_clear_tooltip")))
			.build());

		int row0 = controlsTop + 26;
		int colW = 40 + ARROW_SIZE + SLICE_FIELD_WIDTH + ARROW_SIZE + 8;
		addSliceField('x', HologramSettings::sliceMinX, HologramSettings::setSliceMinX, LEFT, row0);
		addSliceField('x', HologramSettings::sliceMaxX, HologramSettings::setSliceMaxX, LEFT + colW, row0);

		addSliceField('y', HologramSettings::sliceMinY, HologramSettings::setSliceMinY, LEFT, row0 + 20);
		addSliceField('y', HologramSettings::sliceMaxY, HologramSettings::setSliceMaxY, LEFT + colW, row0 + 20);

		addSliceField('z', HologramSettings::sliceMinZ, HologramSettings::setSliceMinZ, LEFT, row0 + 40);
		addSliceField('z', HologramSettings::sliceMaxZ, HologramSettings::setSliceMaxZ, LEFT + colW, row0 + 40);
	}

	private void resetSliceToCurrentHologram() {
		Imagination preview = ImaginationManager.INSTANCE.preview();
		if (preview != null) {
			HologramSettings.applySliceFromImagination(preview);
		} else {
			HologramSettings.clearSlice();
		}
		syncSliceFieldsFromSettings();
	}

	private void syncSliceFieldsFromSettings() {
		Integer[] values = {
			HologramSettings.sliceMinX(),
			HologramSettings.sliceMaxX(),
			HologramSettings.sliceMinY(),
			HologramSettings.sliceMaxY(),
			HologramSettings.sliceMinZ(),
			HologramSettings.sliceMaxZ()
		};
		for (int i = 0; i < sliceFields.size() && i < values.length; i++) {
			EditBox box = sliceFields.get(i);
			Integer value = values[i];
			box.setValue(value != null ? Integer.toString(value) : "");
		}
	}

	private void addSliceField(
		char axis,
		Supplier<@Nullable Integer> getter,
		Consumer<@Nullable Integer> setter,
		int x,
		int y
	) {
		EditBox box = new EditBox(font, x + 40 + ARROW_SIZE, y, SLICE_FIELD_WIDTH, 18,
			Component.literal("slice-" + axis));
		box.setMaxLength(8);
		Integer current = getter.get();
		if (current != null) {
			box.setValue(Integer.toString(current));
		}
		box.setResponder(text -> {
			if (text == null || text.isEmpty() || "-".equals(text)) {
				setter.accept(null);
				return;
			}
			try {
				setter.accept(Integer.parseInt(text));
			} catch (NumberFormatException ignored) {
				// Keep last valid bound while the player is mid-edit.
			}
		});
		sliceFields.add(box);
		addRenderableWidget(box);

		addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
			int next = HologramSettings.nudgeSliceBound(getter.get(), -1, axis);
			setter.accept(next);
			box.setValue(Integer.toString(next));
		}).bounds(x + 40, y, ARROW_SIZE, 18).build());

		addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
			int next = HologramSettings.nudgeSliceBound(getter.get(), 1, axis);
			setter.accept(next);
			box.setValue(Integer.toString(next));
		}).bounds(x + 40 + ARROW_SIZE + SLICE_FIELD_WIDTH, y, ARROW_SIZE, 18).build());
	}

	private void addOpacitySlider() {
		double sliderValue = (HologramSettings.opacityPercent() - HologramSettings.MIN_OPACITY_PERCENT)
			/ (double) (HologramSettings.MAX_OPACITY_PERCENT - HologramSettings.MIN_OPACITY_PERCENT);
		int sliderWidth = 180;
		int rightPadding = 24;
		addRenderableWidget(new AbstractSliderButton(
			width - sliderWidth - rightPadding,
			height - CONTROLS_HEIGHT,
			sliderWidth,
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
		graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("screen.survivalcreativitymod.list_world", worldLabel),
			width / 2, 20, 0xFFA0A0A0);
		graphics.centeredText(font, Component.translatable("screen.survivalcreativitymod.list_hint"),
			width / 2, 32, 0xFF808080);

		int controlsTop = height - CONTROLS_HEIGHT;
		int row0 = controlsTop + 26;
		int colW = 40 + ARROW_SIZE + SLICE_FIELD_WIDTH + ARROW_SIZE + 8;
		drawSliceLabel(graphics, "min X", LEFT, row0);
		drawSliceLabel(graphics, "max X", LEFT + colW, row0);
		drawSliceLabel(graphics, "min Y", LEFT, row0 + 20);
		drawSliceLabel(graphics, "max Y", LEFT + colW, row0 + 20);
		drawSliceLabel(graphics, "min Z", LEFT, row0 + 40);
		drawSliceLabel(graphics, "max Z", LEFT + colW, row0 + 40);

		if (imaginations.isEmpty()) {
			graphics.centeredText(font, Component.translatable("screen.survivalcreativitymod.empty_list"),
				width / 2, LIST_START_Y + 40, 0xFFA0A0A0);
		}
	}

	private void drawSliceLabel(GuiGraphicsExtractor graphics, String label, int fieldX, int y) {
		graphics.text(font, label, fieldX, y + 5, 0xFFA0A0A0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
