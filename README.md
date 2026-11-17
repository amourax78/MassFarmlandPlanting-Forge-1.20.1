# Mass Farmland Planting — Forge 1.20.1

A mod that lets you plant multiple crops at once by **holding Shift and right-clicking farmland**. Designed for **Minecraft Java Edition 1.20.1 / Minecraft Forge 47.x / Java 17**.

## Key Features and Rules

- Activate the mod by **holding Shift** and **right-clicking a farmland block** with a compatible seed or crop item. You can click any face of the farmland block.
- Only farmland blocks on the **same Y-level** as the clicked block and connected in the **four cardinal directions (North / East / South / West)** are scanned. Diagonal connections do not count.
- The search area is limited to a **circular radius of 8 blocks** (the squared X-distance plus the squared Z-distance must not exceed 64).
- Planting order is based on **Euclidean distance from the clicked position**, nearest first. Ties are resolved by X and then Z coordinates to ensure a consistent order.
- A maximum of **64 crops per activation** can be planted. Blocks that already contain a crop or have another obstruction are skipped.
- The mod first uses the item stack **in the hand used to click**. When that stack runs out, it searches the **36 inventory slots** (hotbar and main inventory) for stacks with the **same item and NBT data**. It never switches to a different item type automatically. In Creative Mode, up to 64 blocks can still be planted per activation.
- No additional items, blocks, or textures are required. Existing crops are never destroyed.
- Planting is handled on the **server**. The client only prevents the default single-crop placement action to avoid duplicate planting or ghost crops.

### Supported Crops

Vanilla crops are supported, including wheat seeds, beetroot seeds, carrots, potatoes, melon seeds, pumpkin seeds, torchflower seeds, and pitcher pods, **provided they can actually be planted on farmland and there is enough room**.

The mod can also automatically recognize many seeds from other mods **when the item is a `BlockItem`, its block implements Forge's `IPlantable` interface, and the farmland supports that plant type**. These items are placed through `ItemStack.useOn(UseOnContext)` rather than `level.setBlock`, so normal placement behavior, item consumption, and Forge block-place events are preserved.

**Compatibility limitations:** Some mods use regular items (not `BlockItem`), custom placement APIs, or check the item held directly instead of using `UseOnContext`. Such mods may not be compatible. This mod does not guarantee compatibility with every farming or crop mod. If you use a land-protection mod that relies on its own events rather than Forge's block-place event, test the interaction on an actual server.

## Building on Windows

**Requirements:** JDK 17 (not a Java Runtime-only installation), an Internet connection for the first build, and PowerShell, which is included with Windows.

1. Extract the ZIP archive and open CMD or PowerShell in the project directory.
2. Run:

   ```bat
   .\build.bat
   ```

3. The script downloads the **official Gradle wrapper from the Forge 1.20.1-47.4.28 MDK**, **verifies its SHA-1 checksum** against the Forge website, and then runs `gradlew.bat build`.
4. The built mod JAR will be located at: `build/libs/massfarmland-1.0.0.jar`.

For subsequent builds, you can run `gradlew.bat build` directly once the wrapper has been initialized.

To launch the development client, run `gradlew.bat runClient` after the Gradle wrapper is available.

**Building on Linux/macOS:** Run `bash build.sh`. This requires `curl`, `unzip`, `sha1sum`, and JDK. On macOS, you may need to install coreutils to obtain `sha1sum`.

If you do not want to use the bootstrap script, download the **Forge 1.20.1-47.4.28 MDK** from Forge and copy these four wrapper files from it: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle/wrapper/gradle-wrapper.properties`. Do not overwrite `build.gradle`, the source code, or `mods.toml` with files from the MDK.

## Installation

1. Install **Minecraft Java Edition 1.20.1** with **Forge 47.x**.
2. Copy the built `massfarmland-1.0.0.jar` into the client's `mods` folder.
3. When playing on a **Forge dedicated server**, install the same JAR on the **server** as well (and on every participating client so that client-side handling remains consistent).
4. Launch the game, hold a compatible seed or crop item, then hold Shift and right-click farmland.

## In-Game Test Cases

1. Create a 9×9 farmland plot on the same Y-level and hold 64 wheat seeds: exactly the 64 nearest valid blocks should be planted.
2. Hold 3 wheat seeds and place another 61 in the inventory: the mod should plant up to 64 crops if enough valid blocks are available.
3. Hold carrots while carrying wheat seeds in the inventory: only carrots should be used; the mod must not switch crop types.
4. Connect farmland across a block that already has a crop: the mod should still find and plant on empty farmland beyond that block.
5. Place farmland one block away diagonally, with dirt separating it: it should not be considered connected.
6. Farmland on a different Y-level or more than 8 blocks from the clicked position should not be planted.
7. Blocks that already contain crops, stone, or another obstruction above them should not be replaced.
8. Test in Creative Mode, Survival Mode, multiplayer, and with a compatible crop mod.
9. Test in an area protected by a block-placement protection mod; crops must not appear in locations where placement is denied.
10. If Shift + right-click is used when there are no valid planting locations, the game should fall back to its normal single-crop placement behavior.

## Source Code Structure

- `MassFarmlandMod.java`: Minimal `@Mod` entry-point class.
- `PlantingHandler.java`: Handles `PlayerInteractEvent.RightClickBlock`, checks activation conditions, prevents the client's default placement action, and delegates planting to the server.
- `PlantingService.java`: Performs the connected-area BFS, sorts farmland by distance, searches the inventory for matching item stacks, and calls `ItemStack.useOn` for up to 64 successful planting operations.

The limits are defined by `PlantingService.RADIUS` and `PlantingService.MAX_PLANTS`. Change these values and rebuild the mod to adjust the search radius or maximum number of crops per activation.

## Verification Status

The types and methods used have been checked against the **Forge 1.20.1 API documentation**. The environment used to create the ZIP archive had no connection to Maven/Forge for downloading dependencies, so **the Gradle build and in-game tests could not be run in that environment**. No prebuilt JAR is included in the ZIP archive. Run `build.bat` on a machine with an Internet connection to verify the build. If the build fails, provide the build log for troubleshooting.
