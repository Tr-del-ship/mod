package dev.skyblock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SkyblockState {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DEFAULT_REALM = "main";
	private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("simple-skyblock.json");
	private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir().resolve("simple-skyblock-data.json");
	private static Settings settings = new Settings();
	private static Data data = new Data();
	private static MinecraftServer server;

	private SkyblockState() {}

	static void load(MinecraftServer minecraftServer) {
		server = minecraftServer;
		settings = read(CONFIG_FILE, Settings.class, new Settings());
		if (settings.dimension == null || Identifier.tryParse(settings.dimension) == null) settings.dimension = "skyblock:skyblock";
		settings.islandY = Math.max(-63, Math.min(318, settings.islandY));
		settings.islandSpacing = Math.max(64, settings.islandSpacing);
		settings.islandRadius = Math.max(1, Math.min(settings.islandRadius, 16));
		if (settings.topBlock == null) settings.topBlock = "minecraft:grass_block";
		if (settings.fillBlock == null) settings.fillBlock = "minecraft:dirt";
		if (settings.starterItems == null) settings.starterItems = new ArrayList<>();
		data = read(DATA_FILE, Data.class, new Data());
		if (data.realms == null) data.realms = new HashMap<>();
		if (data.playerRealms == null) data.playerRealms = new HashMap<>();
		if (data.invitations == null) data.invitations = new HashMap<>();
		if (!data.realms.containsKey(DEFAULT_REALM)) data.realms.put(DEFAULT_REALM, new Realm(DEFAULT_REALM));
		if (!Files.exists(CONFIG_FILE)) write(CONFIG_FILE, settings);
		save();
	}

	static void save() {
		write(DATA_FILE, data);
	}

	private static <T> T read(Path path, Class<T> type, T fallback) {
		if (!Files.exists(path)) return fallback;
		try {
			T result = GSON.fromJson(Files.readString(path), type);
			return result == null ? fallback : result;
		} catch (IOException | JsonParseException exception) {
			SimpleSkyblock.LOGGER.error("Could not read {}. Using default values.", path, exception);
			return fallback;
		}
	}

	private static void write(Path path, Object value) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(value));
		} catch (IOException exception) {
			SimpleSkyblock.LOGGER.error("Could not write {}.", path, exception);
		}
	}

	static ServerWorld skyWorld() {
		Identifier id = Identifier.tryParse(settings.dimension);
		if (id == null || server == null) return null;
		return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
	}

	static String currentRealm(ServerPlayerEntity player) {
		String realm = data.playerRealms.get(player.getUuid().toString());
		return realm != null && data.realms.containsKey(realm) ? realm : DEFAULT_REALM;
	}

	static boolean createRealm(String name) {
		if (data.realms.containsKey(name)) return false;
		data.realms.put(name, new Realm(name));
		save();
		return true;
	}

	static List<String> realmNames() {
		return data.realms.keySet().stream().sorted().toList();
	}

	static boolean joinRealm(ServerPlayerEntity player, String name) {
		if (!data.realms.containsKey(name)) return false;
		data.playerRealms.put(player.getUuid().toString(), name);
		save();
		return true;
	}

	static Island islandFor(ServerPlayerEntity player) {
		Realm realm = data.realms.get(currentRealm(player));
		return realm == null ? null : islandFor(realm, player.getUuid().toString());
	}

	static Island islandForOwner(String realmName, UUID owner) {
		Realm realm = data.realms.get(realmName);
		return realm == null ? null : realm.islands.get(owner.toString());
	}

	private static Island islandFor(Realm realm, String playerId) {
		Island owned = realm.islands.get(playerId);
		if (owned != null) return owned;
		for (Island island : realm.islands.values()) {
			if (island.members.contains(playerId)) return island;
		}
		return null;
	}

	static boolean isOwner(ServerPlayerEntity player, Island island) {
		return island.owner.equals(player.getUuid().toString());
	}

	static Island createIsland(ServerPlayerEntity player) {
		String realmName = currentRealm(player);
		Realm realm = data.realms.get(realmName);
		String owner = player.getUuid().toString();
		Island existing = islandFor(realm, owner);
		if (existing != null) return existing;

		int spacing = Math.max(settings.islandSpacing, 64);
		int x = data.nextIsland++ * spacing;
		Island island = new Island(owner, x, settings.islandY, 0);
		realm.islands.put(owner, island);
		buildIsland(island);
		save();
		return island;
	}

	private static void buildIsland(Island island) {
		ServerWorld world = skyWorld();
		if (world == null) return;
		int radius = Math.max(1, Math.min(settings.islandRadius, 16));
		Block top = blockFromConfig(settings.topBlock, Blocks.GRASS_BLOCK);
		Block fill = blockFromConfig(settings.fillBlock, Blocks.DIRT);
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				world.setBlockState(new BlockPos(island.x + x, island.y - 1, island.z + z), fill.getDefaultState(), Block.NOTIFY_ALL);
				world.setBlockState(new BlockPos(island.x + x, island.y, island.z + z), top.getDefaultState(), Block.NOTIFY_ALL);
			}
		}
		if (settings.starterChest) {
			BlockPos chestPos = new BlockPos(island.x + radius - 1, island.y + 1, island.z);
			world.setBlockState(chestPos, Blocks.CHEST.getDefaultState(), Block.NOTIFY_ALL);
			if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
				int slot = 0;
				for (StarterItem starterItem : settings.starterItems) {
					if (starterItem.item == null) continue;
					Identifier itemId = Identifier.tryParse(starterItem.item);
					if (itemId == null || !Registries.ITEM.containsId(itemId) || slot >= chest.size()) continue;
					var item = Registries.ITEM.get(itemId);
					int count = Math.max(1, Math.min(starterItem.count, item.getMaxCount()));
					chest.setStack(slot++, new ItemStack(item, count));
				}
				chest.markDirty();
			}
		}
	}

	private static Block blockFromConfig(String name, Block fallback) {
		Identifier id = Identifier.tryParse(name);
		return id != null && Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : fallback;
	}

	static void setHome(ServerPlayerEntity player, Island island) {
		island.homeX = player.getX();
		island.homeY = player.getY();
		island.homeZ = player.getZ();
		save();
	}

	static boolean invite(ServerPlayerEntity owner, ServerPlayerEntity invitee) {
		Island island = islandFor(owner);
		if (island == null || !isOwner(owner, island) || owner.getUuid().equals(invitee.getUuid())) return false;
		data.invitations.put(invitee.getUuid().toString(), currentRealm(owner) + ":" + island.owner);
		save();
		return true;
	}

	static boolean acceptInvite(ServerPlayerEntity player) {
		String value = data.invitations.remove(player.getUuid().toString());
		if (value == null) return false;
		int separator = value.indexOf(':');
		if (separator < 0) return false;
		String realmName = value.substring(0, separator);
		String owner = value.substring(separator + 1);
		Realm realm = data.realms.get(realmName);
		Island island = realm == null ? null : realm.islands.get(owner);
		if (island == null) return false;
		if (!island.members.contains(player.getUuid().toString())) island.members.add(player.getUuid().toString());
		data.playerRealms.put(player.getUuid().toString(), realmName);
		save();
		return true;
	}

	static boolean deleteIsland(ServerPlayerEntity player) {
		String realmName = currentRealm(player);
		Realm realm = data.realms.get(realmName);
		String owner = player.getUuid().toString();
		Island island = realm == null ? null : realm.islands.get(owner);
		if (island == null) return false;
		ServerWorld world = skyWorld();
		if (world != null) {
			int radius = Math.max(1, Math.min(settings.islandRadius, 16));
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					world.setBlockState(new BlockPos(island.x + x, island.y - 1, island.z + z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
					world.setBlockState(new BlockPos(island.x + x, island.y, island.z + z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				}
			}
			world.setBlockState(new BlockPos(island.x + radius - 1, island.y + 1, island.z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		}
		realm.islands.remove(owner);
		data.invitations.values().removeIf(invitation -> invitation.endsWith(":" + owner));
		for (String member : island.members) data.playerRealms.remove(member, realmName);
		save();
		return true;
	}

	static void leaveIsland(ServerPlayerEntity player) {
		Island island = islandFor(player);
		if (island == null || isOwner(player, island)) return;
		island.members.remove(player.getUuid().toString());
		save();
	}

	static String dimensionId() {
		return settings.dimension;
	}

	static int islandY() {
		return settings.islandY;
	}

	static final class Island {
		String owner;
		int x;
		int y;
		int z;
		double homeX;
		double homeY;
		double homeZ;
		List<String> members = new ArrayList<>();

		Island(String owner, int x, int y, int z) {
			this.owner = owner;
			this.x = x;
			this.y = y;
			this.z = z;
			this.homeX = x + 0.5;
			this.homeY = y + 1;
			this.homeZ = z + 0.5;
		}
	}

	private static final class Realm {
		String name;
		Map<String, Island> islands = new HashMap<>();

		Realm(String name) {
			this.name = name;
		}
	}

	private static final class Data {
		int nextIsland = 1;
		Map<String, Realm> realms = new HashMap<>();
		Map<String, String> playerRealms = new HashMap<>();
		Map<String, String> invitations = new HashMap<>();
	}

	private static final class Settings {
		String dimension = "skyblock:skyblock";
		int islandY = 96;
		int islandSpacing = 512;
		int islandRadius = 3;
		String topBlock = "minecraft:grass_block";
		String fillBlock = "minecraft:dirt";
		boolean starterChest = true;
		List<StarterItem> starterItems = new ArrayList<>(List.of(
			new StarterItem("minecraft:water_bucket", 1),
			new StarterItem("minecraft:lava_bucket", 1),
			new StarterItem("minecraft:oak_sapling", 1),
			new StarterItem("minecraft:ice", 2)
		));
	}

	private static final class StarterItem {
		String item;
		int count;

		StarterItem(String item, int count) {
			this.item = item;
			this.count = count;
		}
	}
}