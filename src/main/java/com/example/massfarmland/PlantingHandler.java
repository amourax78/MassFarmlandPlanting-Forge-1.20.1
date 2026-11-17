package com.example.massfarmland;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Client: suppress the normal single-seed placement when mass planting applies.
 * Server: perform all placements authoritatively so inventory and blocks stay synced.
 */
@Mod.EventBusSubscriber(modid = MassFarmlandMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlantingHandler {
    private PlantingHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickFarmland(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos start = event.getPos();
        ItemStack held = player.getItemInHand(event.getHand());

        if (!player.isShiftKeyDown() || !player.getAbilities().mayBuild || held.isEmpty()) {
            return;
        }

        // Don't override another mod's explicit prohibition of block or item interaction.
        if (event.getUseBlock() == Event.Result.DENY || event.getUseItem() == Event.Result.DENY) {
            return;
        }
        if (!level.isLoaded(start) || !level.getBlockState(start).is(Blocks.FARMLAND)) {
            return;
        }

        // Only seed-like BlockItems whose associated block implements IPlantable.
        // This includes vanilla wheat, beetroot, melon, pumpkin, carrot, potato, etc.
        if (!(held.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof IPlantable plant)) {
            return;
        }

        List<BlockPos> region = PlantingService.connectedFarmland(level, start);
        boolean hasTarget = false;
        for (BlockPos farmland : region) {
            if (PlantingService.canPlantHere(level, farmland, plant)) {
                hasTarget = true;
                break;
            }
        }
        if (!hasTarget) {
            // Preserve normal Forge/vanilla behavior if there is nothing to mass-plant.
            return;
        }

        // Cancel on BOTH logical sides: otherwise vanilla may make a ghost crop client-side
        // after the server has already planted the region.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Keep a snapshot of the original item identity, since the held stack may empty.
            ItemStack identity = held.copy();
            PlantingService.plantRegion(serverPlayer, event.getHand(), region, identity, plant);
        }
    }
}
