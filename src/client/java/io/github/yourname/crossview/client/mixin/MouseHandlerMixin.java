package io.github.arturopakastur.crossview.client.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.arturopakastur.crossview.client.CrossViewConfig;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * IMPROVEMENT over the original: in Side By Side both halves of the screen are interactive.
 *
 * The original left the cursor position stretched across the whole window, so only the left
 * half lined up with the menu. Here the cursor's x position is folded back into one eye's width
 * first, so hovering or clicking in either half hits the same button.
 *
 * The window getters are already halved in this mode (see WindowMixin), so getScreenWidth() is
 * the width of ONE eye in window coordinates.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(
			method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D",
			at = @At("HEAD"),
			cancellable = true
	)
	private static void crossview$bothHalves(Window window, double x, CallbackInfoReturnable<Double> cir) {
		if (!CrossViewConfig.isSideBySide()) {
			return;
		}

		double eyeWidth = window.getScreenWidth();
		if (eyeWidth <= 0.0) {
			return;
		}

		double xInEye = ((x % eyeWidth) + eyeWidth) % eyeWidth;
		cir.setReturnValue(xInEye * window.getGuiScaledWidth() / eyeWidth);
	}
}
