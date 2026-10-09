package io.github.arturopakastur.crossview.client.mixin;

import io.github.arturopakastur.crossview.client.CrossViewConfig;
import io.github.arturopakastur.crossview.client.CrossViewSideBySide;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {
	/**
	 * "Crosshair" option: hides the crosshair when off.
	 * "Crosshair Depth": in Side By Side the crosshair is nudged sideways, opposite ways per eye,
	 * which makes it appear in front of or behind the screen.
	 */
	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void crossview$crosshairStart(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!CrossViewConfig.crosshair) {
			ci.cancel();
			return;
		}

		float shift = CrossViewSideBySide.crosshairShift();
		if (shift != 0.0F) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(shift, 0.0F);
		}
	}

	@Inject(method = "extractCrosshair", at = @At("RETURN"))
	private void crossview$crosshairEnd(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (CrossViewConfig.crosshair && CrossViewSideBySide.crosshairShift() != 0.0F) {
			graphics.pose().popMatrix();
		}
	}

	/**
	 * "Vignette" option. The original hid the vanilla vignette whenever its option was off, even
	 * with 3D switched off. IMPROVEMENT: it is now only hidden while a 3D mode is active, so with
	 * the mod set to Off the game looks exactly like vanilla.
	 */
	@Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
	private void crossview$vignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
		if (CrossViewConfig.mode != CrossViewConfig.Mode.OFF && !CrossViewConfig.vignette) {
			ci.cancel();
		}
	}
}
