package com.example.massfarmland;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Independent trigger: no change to the existing planting event/seed handling. */
@Mod.EventBusSubscriber(modid = MassFarmlandMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HoeingHandler {
    private HoeingHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickHoeableSoil(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos origin = event.getPos();
        ItemStack held = player.getItemInHand(event.getHand());

        if (!player.isShiftKeyDown() || !player.getAbilities().mayBuild
                || !(held.getItem() instanceof HoeItem)) {
            return;
        }
        if (event.getUseBlock() == Event.Result.DENY || event.getUseItem() == Event.Result.DENY
                || !level.isLoaded(origin) || !HoeingService.isTriggerSoil(level.getBlockState(origin))) {
            return;
        }
        if (!HoeingService.hasPotentialTarget(level, origin)) {
            return; // Preserve normal behavior when no eligible soil is present.
        }

        // Both logical sides suppress the one-block vanilla use. The server
        // executes the complete region only once and synchronizes the world.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            HoeingService.hoeRegion(serverPlayer, event.getHand(), origin);
        }
    }
}
