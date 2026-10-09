package io.github.arturopakastur.crossview.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arturopakastur.crossview.client.CrossViewAnaglyph;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/**
	 * GameRenderer.render() calls renderLevel() once per frame. We wrap that single call so the
	 * world can be drawn once per eye. In every mode except Anaglyph this just runs the original.
	 */
	@WrapOperation(
			method = "render()V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V")
	)
	private void crossview$renderLevel(GameRenderer instance, Operation<Void> original) {
		CrossViewAnaglyph.renderLevel(instance, () -> original.call(instance));
	}
}
