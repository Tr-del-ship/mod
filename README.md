# Simple Skyblock

A small, server-side Fabric mod for Minecraft Java Edition 1.21.1. It adds a void skyblock dimension, configurable starter islands, island sharing, and named realms. Realms are isolated areas inside the skyblock dimension rather than separately registered dimensions, so they can be created without restarting the server.

## Build

Requires Java 21 or newer and Gradle. Run `gradle build`; the mod jar is written to `build/libs/`.

Install Fabric Loader and Fabric API on the server, then put the jar in `mods/`. The skyblock dimension is `skyblock:skyblock`.

## Commands

- `/skyblock create` creates your island in the current realm.
- `/skyblock home` teleports to your island or the island you joined.
- `/skyblock sethome` sets your island home at your current position.
- `/skyblock invite <player>` and `/skyblock accept` share island access.
- `/skyblock visit <player>` visits an island without joining it.
- `/skyblock leave` returns to the overworld spawn.
- `/skyblock info` shows the current realm and island owner.
- `/skyblock delete confirm` deletes your island.
- `/skyblock realm create <name>`, `/skyblock realm list`, and `/skyblock realm join <name>` manage realms.

Commands are available to players; no operator setup is required. The mod stores settings in `config/simple-skyblock.json` and world data in `config/simple-skyblock-data.json`. Settings are created with defaults on first server start.

## Configuration

Edit `config/simple-skyblock.json` and restart the server to change the void dimension, island height and spacing, platform size and blocks, or starter chest. For example:

```json
{
	"dimension": "skyblock:skyblock",
	"islandY": 96,
	"islandSpacing": 512,
	"islandRadius": 3,
	"topBlock": "minecraft:grass_block",
	"fillBlock": "minecraft:dirt",
	"starterChest": true,
	"starterItems": [
		{ "item": "minecraft:water_bucket", "count": 1 },
		{ "item": "minecraft:lava_bucket", "count": 1 },
		{ "item": "minecraft:oak_sapling", "count": 1 },
		{ "item": "minecraft:ice", "count": 2 }
	]
}
```