package dev.skyblock;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleSkyblock implements ModInitializer {
	public static final String MOD_ID = "simpleskyblock";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(SkyblockState::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SkyblockState.save());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SkyblockCommands.register(dispatcher));
	}
}