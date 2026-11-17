package com.example.massfarmland;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shift + right-click a vanilla hoeable soil block with a hoe to till every
 * eligible position within the 8x8 square (same Y), including separated
 * patches.
 * Plant removal and tilling both happen on the logical server.
 */
public final class HoeingService {
    public static final int AREA_SIZE = 8;
    public static final int MAX_HOE_OPERATIONS = AREA_SIZE * AREA_SIZE;
    // 8 is even: the clicked block is at offset (0,0), square offsets -3 through
    // +4.
    private static final int MIN_OFFSET = -((AREA_SIZE - 1) / 2);
    private static final int MAX_OFFSET = AREA_SIZE / 2;

    private HoeingService() {
    }

    /**
     * Only begin the bulk action when the clicked block itself accepts a vanilla
     * hoe.
     */
    public static boolean isTriggerSoil(BlockState state) {
        return isVanillaHoeableSoil(state);
    }

    /**
     * These are the vanilla 1.20.1 hoeable blocks. Coarse and rooted dirt yield
     * ordinary dirt on the FIRST hoe use, not farmland; HoeItem.useOn decides.
     */
    private static boolean isVanillaHoeableSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT);
    }

    /**
     * Includes disconnected patches, unlike mass planting's farmland flood fill.
     */
    public static List<BlockPos> squarePositions(BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>(MAX_HOE_OPERATIONS);
        for (int dx = MIN_OFFSET; dx <= MAX_OFFSET; dx++) {
            for (int dz = MIN_OFFSET; dz <= MAX_OFFSET; dz++) {
                positions.add(origin.offset(dx, 0, dz).immutable());
            }
        }
        // Nearby blocks first, deterministic tie-breaking.
        positions.sort(Comparator
                .comparingInt((BlockPos p) -> distanceSquared(origin, p))
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return positions;
    }

    private static int distanceSquared(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Ground vegetation, crops, saplings, and the standing forms that do not
     * subclass BushBlock. No logs, leaves, solid structures, or block entities.
     */
    public static boolean isClearablePlant(BlockState state) {
        return state.getBlock() instanceof BushBlock
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.BIG_DRIPLEAF)
                || state.is(Blocks.BIG_DRIPLEAF_STEM);
    }

    /** Avoid clearing anything if we cannot even consider this a hoe operation. */
    private static boolean isCandidate(Level level, BlockPos soilPos) {
        BlockPos above = soilPos.above();
        if (!level.isLoaded(soilPos) || !level.isLoaded(above)) {
            return false;
        }
        if (!isVanillaHoeableSoil(level.getBlockState(soilPos))) {
            return false;
        }
        BlockState cover = level.getBlockState(above);
        // Only empty air or recognized plants may occupy the space above soil.
        // Water, solid blocks, snow, and anything unknown are NEVER removed.
        return cover.isAir() || (isClearablePlant(cover) && !cover.hasBlockEntity()
                && level.getFluidState(above).isEmpty());
    }

    public static boolean hasPotentialTarget(Level level, BlockPos origin) {
        for (BlockPos pos : squarePositions(origin)) {
            if (isCandidate(level, pos)) {
                return true;
            }
        }
        return false;
    }

    private static UseOnContext context(Level level, ServerPlayer player,
            InteractionHand hand, ItemStack tool, BlockPos soilPos) {
        BlockHitResult hit = new BlockHitResult(
                new Vec3(soilPos.getX() + 0.5D, soilPos.getY() + 1.0D,
                        soilPos.getZ() + 0.5D),
                Direction.UP, soilPos, false);
        return new UseOnContext(level, player, hand, tool, hit);
    }

    /**
     * Attempts to hoe up to 64 cells. The ordinary HoeItem.useOn method handles
     * correct transformations and durability (including Unbreaking and Creative).
     * Plant clearing uses ServerPlayerGameMode.destroyBlock so normal drops and
     * Forge block-break protection/events are respected.
     */
    public static int hoeRegion(ServerPlayer player, InteractionHand hand, BlockPos origin) {
        Level level = player.level();
        ItemStack originalTool = player.getItemInHand(hand);
        if (!(originalTool.getItem() instanceof HoeItem)) {
            return 0;
        }

        int changed = 0;
        for (BlockPos soilPos : squarePositions(origin)) {
            // A hoe broken by a previous operation must not be replaced silently.
            if (changed >= MAX_HOE_OPERATIONS || originalTool.isEmpty()
                    || player.getItemInHand(hand) != originalTool) {
                break;
            }
            if (!isCandidate(level, soilPos)) {
                continue;
            }
            if (!level.mayInteract(player, soilPos)
                    || !player.mayUseItemAt(soilPos, Direction.UP, originalTool)) {
                continue;
            }

            BlockPos above = soilPos.above();
            UseOnContext use = context(level, player, hand, originalTool, soilPos);
            BlockState cover = level.getBlockState(above);
            BlockState before = level.getBlockState(soilPos);

            if (cover.isAir()) {
                // Ask Forge for a dry-run result; skip denied/ineffective actions.
                BlockState preview = before.getToolModifiedState(use, ToolActions.HOE_TILL, true);
                if (preview == null || preview.equals(before)) {
                    continue;
                }
            } else {
                // A plant blocks vanilla's air-above check. Only the explicitly
                // known vanilla hoeable soils are eligible for this clearing path.
                if (!isClearablePlant(cover) || cover.hasBlockEntity()
                        || !level.mayInteract(player, above)
                        || !player.mayUseItemAt(above, Direction.UP, originalTool)) {
                    continue;
                }
                // Normal player break: drops items once and handles double-high
                // plants and their neighbor updates. Never bypass a denied break.
                if (!player.gameMode.destroyBlock(above) || !level.getBlockState(above).isAir()) {
                    continue;
                }
                // The break may have changed soil or the item; check again.
                if (originalTool.isEmpty() || player.getItemInHand(hand) != originalTool
                        || !isVanillaHoeableSoil(level.getBlockState(soilPos))) {
                    continue;
                }
                before = level.getBlockState(soilPos);
                use = context(level, player, hand, originalTool, soilPos);
                BlockState preview = before.getToolModifiedState(use, ToolActions.HOE_TILL, true);
                if (preview == null || preview.equals(before)) {
                    continue;
                }
            }

            InteractionResult result = originalTool.useOn(use);
            if (result.consumesAction() && !level.getBlockState(soilPos).equals(before)) {
                changed++;
            }
        }

        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return changed;
    }
}
