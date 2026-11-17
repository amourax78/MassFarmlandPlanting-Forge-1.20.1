package com.example.massfarmland;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.IPlantable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All constants and the flood-fill/placement logic live here. */
public final class PlantingService {
    public static final int AREA = 8;
    public static final int MAX_PLANTS = AREA * AREA;
    public static final int MAX_OFFSET = 8;
    public static final int MIN_OFFSET = -7;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private PlantingService() {
    }

    /**
     * A four-way flood fill, limited to farmland at the exact same Y and inside
     * a true circular horizontal radius (dx^2 + dz^2 <= 64).
     *
     * All connected farmland is included in traversal, even if already planted;
     * this is important because crops must not split an otherwise connected field.
     * The result is sorted by geometric distance, not BFS path length.
     */
    public static List<BlockPos> connectedFarmland(Level level, BlockPos origin) {
        List<BlockPos> result = new ArrayList<>();
        if (!level.isLoaded(origin) || !level.getBlockState(origin).is(Blocks.FARMLAND)) {
            return result;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos immutableOrigin = origin.immutable();
        visited.add(immutableOrigin);
        queue.add(immutableOrigin);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            result.add(current);
            for (Direction direction : HORIZONTAL) {
                BlockPos next = current.relative(direction);
                int dx = next.getX() - origin.getX();
                int dz = next.getZ() - origin.getZ();
                if (dx < MIN_OFFSET || dx > MAX_OFFSET
                        || dz < MIN_OFFSET || dz > MAX_OFFSET) {
                    continue;
                }
                if (visited.add(next) && level.isLoaded(next)
                        && level.getBlockState(next).is(Blocks.FARMLAND)) {
                    queue.addLast(next);
                }
            }
        }

        result.sort(Comparator
                .comparingInt((BlockPos p) -> horizontalDistanceSquared(p, origin))
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return result;
    }

    private static int horizontalDistanceSquared(BlockPos pos, BlockPos origin) {
        int dx = pos.getX() - origin.getX();
        int dz = pos.getZ() - origin.getZ();
        return dx * dx + dz * dz;
    }

    /** Checks that the crop has an empty block and the farmland supports it. */
    public static boolean canPlantHere(Level level, BlockPos farmland, IPlantable plant) {
        BlockPos above = farmland.above();
        if (!level.isLoaded(farmland) || !level.isLoaded(above)) {
            return false;
        }
        BlockState soil = level.getBlockState(farmland);
        return soil.is(Blocks.FARMLAND)
                && level.getBlockState(above).isAir()
                && soil.canSustainPlant(level, farmland, Direction.UP, plant);
    }

    /**
     * Reuses the real ItemStack.useOn -> Forge placement path, rather than
     * setting a crop block directly. Therefore item behavior, survival checks,
     * item consumption and Forge placement events are respected.
     *
     * Vanilla-style BlockItems use the explicit ItemStack in UseOnContext, so
     * we can consume extra matching stacks from inventory without swapping
     * the player's selected slot or changing which hand they appear to hold.
     */
    public static int plantRegion(ServerPlayer player, InteractionHand hand,
            List<BlockPos> connected, ItemStack identity,
            IPlantable plant) {
        Level level = player.level();
        int planted = 0;
        boolean attempted = false;

        for (BlockPos farmland : connected) {
            if (planted >= MAX_PLANTS) {
                break;
            }
            if (!canPlantHere(level, farmland, plant)) {
                continue;
            }

            ItemStack source = findMatchingStack(player, hand, identity);
            if (source.isEmpty()) {
                break;
            }

            BlockPos above = farmland.above();
            // Simulate a right-click on the TOP of THIS farmland (not the air block).
            BlockHitResult hit = new BlockHitResult(
                    new Vec3(farmland.getX() + 0.5D, farmland.getY() + 1.0D,
                            farmland.getZ() + 0.5D),
                    Direction.UP, farmland, false);
            UseOnContext context = new UseOnContext(level, player, hand, source, hit);
            attempted = true;
            InteractionResult result = source.useOn(context);

            // Success alone is not enough: a modded item might do something else.
            // Verify that a block was actually placed in the previously empty space.
            if (result.consumesAction() && !level.getBlockState(above).isAir()) {
                planted++;
            }
        }

        if (attempted) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
        return planted;
    }

    /** First the triggering hand, then matching stacks in the player's 36 slots. */
    private static ItemStack findMatchingStack(ServerPlayer player, InteractionHand hand,
            ItemStack identity) {
        ItemStack held = player.getItemInHand(hand);
        if (matches(identity, held)) {
            return held;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (matches(identity, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean matches(ItemStack identity, ItemStack candidate) {
        return !candidate.isEmpty() && ItemStack.isSameItemSameTags(identity, candidate);
    }
}
