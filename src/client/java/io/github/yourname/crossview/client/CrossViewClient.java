package io.github.arturopakastur.crossview.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.arturopakastur.crossview.CrossView;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

public class CrossViewClient implements ClientModInitializer {
	private static KeyMapping openSettingsKey;
	private static KeyMapping toggle3DKey;

	@Override
	public void onInitializeClient() {
		// Groups our keys together under one heading in Options > Controls > Key Binds.
		KeyMapping.Category category = KeyMapping.Category.register(CrossView.id("main"));

		// Original mod: G opened the 3D settings screen, H toggled 3D on/off.
		openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.crossview.open_settings",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_G,
				category
		));

		toggle3DKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.crossview.toggle_3d",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_H,
				category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Wait for the game to finish loading before turning Side By Side on (see CrossViewConfig.gameReady).
			if (!CrossViewConfig.gameReady && client.isGameLoadFinished()) {
				CrossViewConfig.gameReady = true;
				if (CrossViewConfig.mode == CrossViewConfig.Mode.SIDE_BY_SIDE) {
					// Same thing the settings screen does when the mode changes: recompute sizes.
					client.resizeGui();
				}
			}

			while (toggle3DKey.consumeClick()) {
				CrossViewConfig.enabled = !CrossViewConfig.enabled;
				CrossViewConfig.save();
				if (client.player != null) {
					client.player.sendSystemMessage(Component.literal(
							"CrossView 3D: " + (CrossViewConfig.enabled ? "ON" : "OFF")));
				}
			}

			while (openSettingsKey.consumeClick()) {
				// Original: G opened the 3D Settings screen (only reachable in-game, no parent screen).
				client.gui.setScreen(new CrossViewSettingsScreen());
			}
		});

		CrossView.LOGGER.info("CrossView client initialized");
	}
}
