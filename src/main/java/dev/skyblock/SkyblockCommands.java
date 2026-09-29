package dev.skyblock;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.regex.Pattern;

final class SkyblockCommands {
	private static final Pattern REALM_NAME = Pattern.compile("[a-z0-9_-]{1,24}");

	private SkyblockCommands() {}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("skyblock")
			.then(CommandManager.literal("create").executes(context -> create(context.getSource())))
			.then(CommandManager.literal("home").executes(context -> home(context.getSource())))
			.then(CommandManager.literal("sethome").executes(context -> setHome(context.getSource())))
			.then(CommandManager.literal("invite").then(CommandManager.argument("player", EntityArgumentType.player())
				.executes(context -> invite(context.getSource(), EntityArgumentType.getPlayer(context, "player")))))
			.then(CommandManager.literal("accept").executes(context -> accept(context.getSource())))
			.then(CommandManager.literal("visit").then(CommandManager.argument("player", EntityArgumentType.player())
				.executes(context -> visit(context.getSource(), EntityArgumentType.getPlayer(context, "player")))))
			.then(CommandManager.literal("leave").executes(context -> leave(context.getSource())))
			.then(CommandManager.literal("info").executes(context -> info(context.getSource())))
			.then(CommandManager.literal("delete").then(CommandManager.literal("confirm")
				.executes(context -> delete(context.getSource()))))
			.then(CommandManager.literal("realm")
				.then(CommandManager.literal("create").then(CommandManager.argument("name", StringArgumentType.word())
					.executes(context -> createRealm(context.getSource(), StringArgumentType.getString(context, "name")))))
				.then(CommandManager.literal("list").executes(context -> listRealms(context.getSource())))
				.then(CommandManager.literal("join").then(CommandManager.argument("name", StringArgumentType.word())
					.executes(context -> joinRealm(context.getSource(), StringArgumentType.getString(context, "name")))))));
	}

	private static int create(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		ServerWorld world = SkyblockState.skyWorld();
		if (world == null) return error(source, "Skyblock dimension is unavailable. Check the server log and restart the world.");
		SkyblockState.Island island = SkyblockState.createIsland(player);
		teleport(player, world, island.homeX, island.homeY, island.homeZ);
		source.sendFeedback(() -> Text.literal("Island ready in realm '" + SkyblockState.currentRealm(player) + "'."), false);
		return 1;
	}

	private static int home(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		SkyblockState.Island island = SkyblockState.islandFor(player);
		if (island == null) return error(source, "You do not have an island here. Use /skyblock create.");
		ServerWorld world = SkyblockState.skyWorld();
		if (world == null) return error(source, "Skyblock dimension is unavailable.");
		teleport(player, world, island.homeX, island.homeY, island.homeZ);
		return 1;
	}

	private static int setHome(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		SkyblockState.Island island = SkyblockState.islandFor(player);
		if (island == null || !SkyblockState.isOwner(player, island)) return error(source, "Only your own island owner can set its home.");
		if (!player.getWorld().getRegistryKey().getValue().toString().equals(SkyblockState.dimensionId())) {
			return error(source, "You must be in the skyblock dimension to set your island home.");
		}
		SkyblockState.setHome(player, island);
		source.sendFeedback(() -> Text.literal("Island home updated."), false);
		return 1;
	}

	private static int invite(ServerCommandSource source, ServerPlayerEntity invitee) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!SkyblockState.invite(player, invitee)) return error(source, "Create your island first. Only its owner can invite players.");
		invitee.sendMessage(Text.literal(player.getName().getString() + " invited you to their skyblock island. Use /skyblock accept."));
		source.sendFeedback(() -> Text.literal("Invitation sent to " + invitee.getName().getString() + "."), false);
		return 1;
	}

	private static int accept(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!SkyblockState.acceptInvite(player)) return error(source, "You have no valid island invitation.");
		source.sendFeedback(() -> Text.literal("Island invitation accepted. Use /skyblock home to travel there."), false);
		return 1;
	}

	private static int visit(ServerCommandSource source, ServerPlayerEntity target) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		SkyblockState.Island island = SkyblockState.islandForOwner(SkyblockState.currentRealm(target), target.getUuid());
		if (island == null) return error(source, "That player does not own an island in their active realm.");
		ServerWorld world = SkyblockState.skyWorld();
		if (world == null) return error(source, "Skyblock dimension is unavailable.");
		teleport(player, world, island.homeX, island.homeY, island.homeZ);
		return 1;
	}

	private static int leave(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		SkyblockState.leaveIsland(player);
		ServerWorld overworld = source.getServer().getOverworld();
		BlockPos spawn = overworld.getSpawnPos();
		teleport(player, overworld, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
		return 1;
	}

	private static int info(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		SkyblockState.Island island = SkyblockState.islandFor(player);
		if (island == null) return error(source, "No island in realm '" + SkyblockState.currentRealm(player) + "'.");
		String owner = source.getServer().getUserCache().getByUuid(java.util.UUID.fromString(island.owner))
			.map(profile -> profile.getName()).orElse("Unknown");
		source.sendFeedback(() -> Text.literal("Realm: " + SkyblockState.currentRealm(player) + " | Owner: " + owner + " | Island home: " + island.homeX + ", " + island.homeY + ", " + island.homeZ), false);
		return 1;
	}

	private static int delete(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!SkyblockState.deleteIsland(player)) return error(source, "You do not own an island in this realm.");
		source.sendFeedback(() -> Text.literal("Your island and its starter platform were deleted."), false);
		return 1;
	}

	private static int createRealm(ServerCommandSource source, String name) throws CommandSyntaxException {
		source.getPlayerOrThrow();
		if (!REALM_NAME.matcher(name).matches()) return error(source, "Use 1-24 lowercase letters, numbers, underscores, or hyphens for a realm name.");
		if (!SkyblockState.createRealm(name)) return error(source, "That realm already exists.");
		source.sendFeedback(() -> Text.literal("Realm '" + name + "' created. Join it with /skyblock realm join " + name + "."), false);
		return 1;
	}

	private static int listRealms(ServerCommandSource source) throws CommandSyntaxException {
		source.getPlayerOrThrow();
		source.sendFeedback(() -> Text.literal("Realms: " + String.join(", ", SkyblockState.realmNames())), false);
		return 1;
	}

	private static int joinRealm(ServerCommandSource source, String name) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!SkyblockState.joinRealm(player, name)) return error(source, "No realm named '" + name + "'.");
		source.sendFeedback(() -> Text.literal("Active realm set to '" + name + "'. Use /skyblock create or /skyblock home."), false);
		return 1;
	}

	private static void teleport(ServerPlayerEntity player, ServerWorld world, double x, double y, double z) {
		player.teleport(world, x, y, z, EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch());
	}

	private static int error(ServerCommandSource source, String message) {
		source.sendError(Text.literal(message));
		return 0;
	}
}