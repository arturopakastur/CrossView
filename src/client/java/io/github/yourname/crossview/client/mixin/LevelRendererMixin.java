package io.github.arturopakastur.crossview.client.mixin;

import io.github.arturopakastur.crossview.client.CrossViewGizmos;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.SimpleGizmoCollector.GizmoInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	/**
	 * The game queues this frame's debug shapes (hitboxes, chunk borders, ...) here, and the first
	 * level render empties the queue. Keep a copy so the second eye can draw them as well.
	 */
	@Inject(method = "addMainThreadGizmos", at = @At("HEAD"))
	private void crossview$captureGizmos(List<GizmoInstance> mainThreadGizmos, CallbackInfo ci) {
		CrossViewGizmos.capture((LevelRenderer) (Object) this, mainThreadGizmos);
	}
}
