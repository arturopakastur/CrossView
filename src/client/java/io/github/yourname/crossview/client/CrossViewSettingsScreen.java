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
 * one 180x20 row per option, starting at y = 25 and spaced 25 apart (squeezed or moved up if the screen is too short), centered on the screen. There is no "Done" button; press Esc to close.
 * Row order, labels and value text match the original (plus the Dynamic rows). Crosshair Depth and Desaturate are sliders.
 */
public class CrossViewSettingsScreen extends Screen {
	private static final int ROW_WIDTH = 180;
	private static final int ROW_HEIGHT = 20;
	private static final int TOP = 25;
	private static final int SPACING = 25;

	/** Builds a slider for a row. */
	private interface SliderMaker {
		AbstractSliderButton make(int x, int y, int width, int height);
	}

	/**
	 * One row is either a button (a name, a way to show the current value, and what a click does)
	 * or a slider.
	 */
	private record Row(String name, Supplier<String> value, Runnable onPress, SliderMaker slider) {
		Row(String name, Supplier<String> value, Runnable onPress) {
			this(name, value, onPress, null);
		}

		static Row ofSlider(SliderMaker slider) {
			return new Row(null, null, null, slider);
		}
	}

	private final List<Row> rows = new ArrayList<>();
	/** One entry per row, in the same order; null where the row is a slider. */
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
		rows.add(Row.ofSlider(CrosshairDepthSlider::new));
		// New (not in the original): Side By Side only. Crosshair Depth then acts as an offset from the automatic depth.
		rows.add(new Row("Dynamic Crosshair", () -> onOff(CrossViewConfig.dynamicCrosshair),
				() -> CrossViewConfig.dynamicCrosshair = !CrossViewConfig.dynamicCrosshair));
		rows.add(new Row("Dynamic Speed", CrossViewConfig::dynamicSpeedLabel,
				CrossViewConfig::cycleDynamicSpeed));
		rows.add(new Row("Anaglyph Type", () -> CrossViewConfig.anaglyphType.label,
				CrossViewConfig::cycleAnaglyphType));
		rows.add(new Row("Legacy 3D", () -> onOff(CrossViewConfig.legacy3d),
				() -> CrossViewConfig.legacy3d = !CrossViewConfig.legacy3d));
		rows.add(Row.ofSlider(DesaturateSlider::new));
	}

	@Override
	protected void init() {
		buttons.clear();
		int x = this.width / 2 - ROW_WIDTH / 2;

		// Rows start at y = 25 and are 25 apart like the original. When that does not fit (GUI scale 4 is
		// only 270 units tall) first the gaps shrink (down to 2 units), then the whole list moves up
		// (down to y = 4), and only as a last resort the gaps shrink further (down to 1 unit).
		int span = rows.size() - 1;
		int bottomLimit = this.height - 4;
		int top = TOP;
		int spacing = Math.clamp((bottomLimit - ROW_HEIGHT - top) / span, ROW_HEIGHT + 2, SPACING);
		if (top + span * spacing + ROW_HEIGHT > bottomLimit) {
			top = Math.max(4, bottomLimit - ROW_HEIGHT - span * spacing);
		}
		if (top + span * spacing + ROW_HEIGHT > bottomLimit) {
			spacing = Math.max(ROW_HEIGHT + 1, (bottomLimit - ROW_HEIGHT - top) / span);
		}

		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			int y = top + i * spacing;

			if (row.slider() != null) {
				buttons.add(null);
				this.addRenderableWidget(row.slider().make(x, y, ROW_WIDTH, ROW_HEIGHT));
				continue;
			}

			Button button = Button.builder(label(row), btn -> {
				row.onPress().run();
				refreshLabels();
			}).bounds(x, y, ROW_WIDTH, ROW_HEIGHT).build();

			buttons.add(button);
			this.addRenderableWidget(button);
		}
	}

	private static Component label(Row row) {
		return Component.literal(row.name() + ": " + row.value().get());
	}

	private void refreshLabels() {
		for (int i = 0; i < buttons.size(); i++) {
			Button button = buttons.get(i);
			if (button != null) {
				button.setMessage(label(rows.get(i)));
			}
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
	 * The "Crosshair Depth" slider: -50 to +50 in steps of 1, 0 in the middle, shown as
	 * "Crosshair Depth: N". The handle snaps to whole numbers. With Dynamic Crosshair on, this is
	 * the offset from the automatic depth. The arrow keys move it by 1.
	 */
	private static final class CrosshairDepthSlider extends AbstractSliderButton {
		private static final int MIN = -50;
		private static final int MAX = 50;

		CrosshairDepthSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(), (CrossViewConfig.crosshairDepth - MIN) / (double) (MAX - MIN));
			updateMessage();
		}

		private int depth() {
			return MIN + (int) Math.round(this.value * (MAX - MIN));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal("Crosshair Depth: " + depth()));
		}

		@Override
		protected void applyValue() {
			int depth = depth();
			CrossViewConfig.setCrosshairDepth(depth);
			// Snap the handle to the whole number that is shown and saved.
			this.value = (depth - MIN) / (double) (MAX - MIN);
		}
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
