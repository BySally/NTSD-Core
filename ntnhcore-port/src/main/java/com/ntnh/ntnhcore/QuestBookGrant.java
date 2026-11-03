package com.ntnh.ntnhcore;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** One starting book per player/world; the grant survives death, cloning and reconnects. */
final class QuestBookGrant {
    static final String KEY = "NTNHQuestBookGiven";

    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) grant(player);
    }

    static void grant(ServerPlayer player) {
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(KEY)) return;
        var stack = new ItemStack(PackContent.QUEST_BOOK.get());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        persisted.putBoolean(KEY, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    static void clone(PlayerEvent.Clone event) {
        var old = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!old.getBoolean(KEY)) return;
        var target = event.getEntity().getPersistentData();
        var persisted = target.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(KEY, true);
        target.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
