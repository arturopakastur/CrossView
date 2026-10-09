package io.github.arturopakastur.crossview.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.Window;
import io.github.arturopakastur.crossview.client.CrossViewConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In Side By Side the game thinks the window is half as wide, exactly like the original mod did
 * (its window class halved the same three getters). Camera aspect, GUI scale, menus and the main
 * render target all derive from these, so everything lays out for ONE eye automatically.
 *
 * The real window and the swapchain are not affected: the surface sizes itself from the window
 * handle, not from these getters.
 */
@Mixin(Window.class)
public class WindowMixin {
	@ModifyReturnValue(method = "getWidth()I", at = @At("RETURN"))
	private int crossview$halveWidth(int original) {
		return CrossViewConfig.isSideBySide() ? original / 2 : original;
	}

	@ModifyReturnValue(method = "getScreenWidth()I", at = @At("RETURN"))
	private int crossview$halveScreenWidth(int original) {
		return CrossViewConfig.isSideBySide() ? original / 2 : original;
	}

	@ModifyReturnValue(method = "getGuiScaledWidth()I", at = @At("RETURN"))
	private int crossview$halveGuiScaledWidth(int original) {
		return CrossViewConfig.isSideBySide() ? original / 2 : original;
	}
}
