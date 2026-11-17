# Mass Farmland Planting + Mass Hoeing (Forge 1.20.1)

**Minecraft Java 1.20.1** | **Forge 47.x** | **Java JDK 17** | **Mod version 1.1.0**

This source project implements two independent **Shift + right-click** actions:

| Feature | Item held | Click on | Area | Behavior |
| --- | --- | --- | --- | --- |
| Mass planting | Compatible seeds/crop block item | Farmland | 8 x 8 | Plant up to 64 crops on connected farmland |
| Mass hoeing | Vanilla or vanilla-derived `HoeItem` | Dirt, grass block, dirt path, coarse dirt, or rooted dirt | 8 x 8 | Process all eligible soil cells, even disconnected patches; clear plants when needed |

## How the 8 x 8 area is positioned

Both features use the clicked block as the anchor. X/Z offsets are **-3 through +4** from that block. Only blocks at the **same Y level** are considered. The square contains 64 positions; neither operation affects the terrain beyond its 8 x 8 region. Even-sized squares cannot be perfectly centered on one block.

## Mass planting (retained from the original feature, changed to 8 x 8)

1. Hold seeds or a supported crop item; **Shift + right-click farmland**.
2. A 4-direction flood fill finds *connected farmland* in the 8 x 8 square. Farmland with existing crops still acts as a connection, but is not replanted.
3. The closest empty, viable connected cells are planted first, up to **64 successful placements**.
4. Uses items from the clicked hand and then matching item + NBT stacks elsewhere in the normal 36-slot inventory.
5. Uses the real `ItemStack.useOn(UseOnContext)` path. It leaves existing plants and unrelated blocks untouched.

The supported seed items follow the prior rules: the item must be a `BlockItem`, its block must implement Forge `IPlantable`, and the farmland must support it. Some modded crops use different placement APIs and cannot be guaranteed.

## Mass hoeing (NEW)

1. Hold a **hoe** (an instance of `HoeItem`, including typical vanilla or subclassed hoes); **Shift + right-click one of the eligible soil blocks**.
2. The mod examines **all 64 positions** in the 8 x 8 square at the clicked block's Y level, nearest first. **Disconnected patches are included.**
3. Eligible soil: **grass block, dirt, dirt path, coarse dirt, and rooted dirt**. Already-tilled farmland, stone, sand, and other blocks are skipped.
4. If a supported plant is directly above an eligible soil cell, the mod breaks the plant **using the server's normal player block-break operation** before attempting to hoe. This produces normal survival drops, honors block-break events and protected areas, and lets Minecraft update the other half of double-height plants. This may also break dependent vertical plant segments as in vanilla.
5. Recognized plants include normal grass, flowers, ferns, tall grass/double flowers, saplings, mushrooms, crops (if one occurs above eligible soil), bamboo, sugar cane, big dripleaves, and other `BushBlock`-based plants. Plant or crop blocks above **existing farmland are not harvested**, since farmland isn't a hoeable soil block.
6. It **never intentionally breaks logs, leaves, chests, stone, water, snow, unknown blocks, or block entities** to clear an obstructed soil cell. It skips soil with those obstructions.
7. Once there is air above the soil, the mod calls the hoe's normal `ItemStack.useOn` behavior. Forge's `HOE_TILL` preview is checked to skip ineffective actions. The result exactly follows vanilla: **grass, dirt, and dirt path -> farmland**; **coarse dirt and rooted dirt -> dirt first**, requiring a second activation if you want farmland. Rooted dirt's hanging-roots item uses vanilla behavior.
8. There is **no artificial durability damage** in the mass-hoeing loop. The normal hoe use is responsible for per-operation damage, Unbreaking, and Creative mode. Breaking a plant with the hoe can additionally cause normal mining-tool wear where vanilla does so. The batch stops when the tool breaks or changes.
9. Only the server modifies the world. The triggering click is intercepted on both client and server to avoid one-block duplicate actions.

### Important bounds and safety

- Plant clearing only occurs above a *vanilla hoeable* soil block inside the square. There is no indiscriminate area clearing.
- Normal drops are subject to game mode and loot tables (e.g. Creative normally does not generate survival drops).
- Player/item interaction permissions are checked; plant breaking uses `ServerPlayerGameMode.destroyBlock`, which runs the regular server-side Forge block-break hooks. **Protection mods with custom/nonstandard hooks still need live testing.**
- Mods with unusual custom plant block classes might not be recognized automatically. Unknown or solid blocks are preserved.
- You must click the **soil block**, not the plant, to activate the feature; targeting a tall plant may require clicking the side of its soil.

## Building on Windows

You need a **JDK 17** (not just a Java runtime) and an internet connection for the first build.

1. Extract the ZIP and open PowerShell or Command Prompt in the project folder.
2. Run `build.bat` (or `./build.bat` in PowerShell).
3. The script obtains the official Forge 1.20.1-47.4.28 MDK Gradle wrapper, verifies its checksum, and runs the Gradle build.
4. The resulting installable mod, **if compilation succeeds**, is at:

   `build/libs/massfarmland-1.0.0.jar`

***sorry i just too lazy to modify the .jar into 1.1.0, u just can easily download it in version_log/ ***

On Linux: `bash build.sh` (requires `curl`, `unzip`, `sha1sum`, JDK 17). On macOS `sha1sum` may require coreutils. After Gradle bootstrapping, `gradlew.bat runClient` starts a development client.

## Installing

Install Minecraft Forge **1.20.1 / Forge 47.x**, then place the built JAR in your Minecraft `mods` folder. Install the same JAR on both client and dedicated server for multiplayer.

## Recommended manual in-game tests

1. Plant 64 empty connected farmland cells in an 8 x 8 square with 64 seeds; no more than 64 are planted.
2. Check planting skips already occupied cells, doesn't cross a dirt gap, and doesn't extend past the 8 x 8 boundary.
3. Mass-hoe an 8 x 8 mix of grass, dirt, and dirt path, separated by stone; all eligible cells transform, stone does not.
4. Mass-hoe coarse dirt and rooted dirt: both become **ordinary dirt first**; repeat to get farmland.
5. Put saplings, regular grass, flowers, and a two-block tall flower on eligible dirt. Confirm drops and both halves of tall plant vanish.
6. Put stone, water, snow, chests or leaves immediately above other eligible dirt. These cells should remain unchanged; no protected or solid block should be broken.
7. Check a hoe with one durability left stops after breaking, rather than continuing as an empty stack.
8. Check both main-hand/off-hand usage, Survival, Creative, and multiplayer.
9. Test protected areas, custom crop mods, and Unbreaking: confirm normal protection and enchantment behavior.
10. Confirm Shift + right-click with seeds affects planting only; Shift + right-click with a hoe affects tilling only.

## Validation status

Source files and ZIP layout were inspected, but **the Forge Gradle build and Minecraft gameplay tests have not been run** in the packaging environment because Forge/Gradle dependencies cannot be downloaded here. This ZIP contains Java sources and build scripts, not a ready-made mod JAR. Please build with JDK 17 and run the in-game tests above; share the build log if compilation fails.s