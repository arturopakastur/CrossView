package io.github.arturopakastur.crossview.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The "3D Settings" screen, laid out exactly like the original mod's screen:
 * one 180x20 row per option, starting at y = 25 and spaced 25 apart, centered on the screen. There is no "Done" button; press Esc to close.
 * Row order, labels and value text match the original.
 */
public class CrossViewSettingsScreen extends Screen {
	private static final int ROW_WIDTH = 180;
	private static final int ROW_HEIGHT = 20;
	private static final int TOP = 25;
	private static final int SPACING = 25;

	/** One row = a name, a way to show the current value, and what a click does. */
	private record Row(String name, Supplier<String> value, Runnable onPress) {
	}

	private final List<Row> rows = new ArrayList<>();
	private final List<Button> buttons = new ArrayList<>();

	public CrossViewSettingsScreen() {
		super(Component.literal("3D Settings"));

		// Same order as the original option table.
		rows.add(new Row("3D Mode", () -> CrossViewConfig.mode.label, () -> {
			CrossViewConfig.cycleMode();
			onModeChanged();
		}));
		rows.add(new Row("3D", () -> onOff(CrossViewConfig.enabled),
				() -> CrossViewConfig.enabled = !CrossViewConfig.enabled));
		rows.add(new Row("Invert 3D", () -> onOff(CrossViewConfig.invert),
				() -> CrossViewConfig.invert = !CrossViewConfig.invert));
		rows.add(new Row("Vignette", () -> onOff(CrossViewConfig.vignette),
				() -> CrossViewConfig.vignette = !CrossViewConfig.vignette));
		rows.add(new Row("3D Power", () -> String.valueOf(CrossViewConfig.power),
				CrossViewConfig::cyclePower));
		rows.add(new Row("Crosshair", () -> onOff(CrossViewConfig.crosshair),
				() -> CrossViewConfig.crosshair = !CrossViewConfig.crosshair));
		rows.add(new Row("Crosshair Depth", () -> String.valueOf(CrossViewConfig.crosshairDepth),
				CrossViewConfig::cycleCrosshairDepth));
		rows.add(new Row("Anaglyph Type", () -> CrossViewConfig.anaglyphType.label,
				CrossViewConfig::cycleAnaglyphType));
		rows.add(new Row("Legacy 3D", () -> onOff(CrossViewConfig.legacy3d),
				() -> CrossViewConfig.legacy3d = !CrossViewConfig.legacy3d));
		// The last original option, "Desaturate", is a slider and is added in init().
	}

	@Override
	protected void init() {
		buttons.clear();
		int x = this.width / 2 - ROW_WIDTH / 2;

		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			int y = TOP + i * SPACING;

			Button button = Button.builder(label(row), btn -> {
				row.onPress().run();
				refreshLabels();
			}).bounds(x, y, ROW_WIDTH, ROW_HEIGHT).build();

			buttons.add(button);
			this.addRenderableWidget(button);
		}

		int sliderY = TOP + rows.size() * SPACING;
		this.addRenderableWidget(new DesaturateSlider(x, sliderY, ROW_WIDTH, ROW_HEIGHT));
	}

	private static Component label(Row row) {
		return Component.literal(row.name() + ": " + row.value().get());
	}

	private void refreshLabels() {
		for (int i = 0; i < buttons.size(); i++) {
			buttons.get(i).setMessage(label(rows.get(i)));
		}
	}

	private static String onOff(boolean value) {
		return value ? "ON" : "OFF";
	}

	/**
	 * Changing the mode changes how wide the game thinks the window is (Side By Side halves it),
	 * so the GUI scale and the open screen have to be recalculated, like the original did.
	 */
	private static void onModeChanged() {
		Minecraft.getInstance().resizeGui();
	}

	/** Settings are written to config/crossview.json whenever this screen is closed. */
	@Override
	public void removed() {
		super.removed();
		CrossViewConfig.save();
	}

	/**
	 * The "Desaturate" slider: 0-100, shown as "Desaturate: N". Unlike the original it takes
	 * effect instantly (no resource reload), see CrossViewDesaturate.
	 */
	private static final class DesaturateSlider extends AbstractSliderButton {
		DesaturateSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(), CrossViewConfig.desaturate / 100.0);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal("Desaturate: " + Math.round(this.value * 100)));
		}

		@Override
		protected void applyValue() {
			CrossViewConfig.setDesaturate((int) Math.round(this.value * 100));
		}
	}
}
