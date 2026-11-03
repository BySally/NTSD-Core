package com.ntnh.ntnhcore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import java.util.UUID;

/** Isolated unconnected player objects: checks persisted grants and actual loaded recipe ingredients. */
final class BookLifecycleVerification {
    static void run(MinecraftServer server, JsonObject report, JsonArray errors) {
        var first = player(server, "NTNHBookCheckA");
        var second = player(server, "NTNHBookCheckB");
        QuestBookGrant.grant(first);
        QuestBookGrant.grant(second);
        boolean independent = books(first) == 1 && books(second) == 1;
        QuestBookGrant.grant(first);
        boolean once = books(first) == 1;
        first.getInventory().clearContent();
        var respawned = player(server, "NTNHBookCheckA");
        QuestBookGrant.clone(new PlayerEvent.Clone(respawned, first, true));
        QuestBookGrant.grant(respawned);
        boolean death = books(respawned) == 0;
        var saved = new net.minecraft.nbt.CompoundTag();
        first.saveWithoutId(saved);
        var restored = player(server, "NTNHBookCheckA");
        restored.load(saved);
        QuestBookGrant.grant(restored);
        boolean reconnect = books(restored) == 0;
        report.addProperty("quest_book_each_player_once", independent && once);
        report.addProperty("quest_book_not_reissued_after_death", death);
        report.addProperty("quest_book_grant_survives_player_save", reconnect);
        if (!independent || !once || !death || !reconnect) errors.add("Quest book lifecycle mismatch");

        var recipe = server.getRecipeManager().byKey(new ResourceLocation("ntnhcore", "quest_book")).orElseThrow();
        var ingredients = recipe.getIngredients();
        boolean original = ingredients.size() == 2
            && ingredients.stream().anyMatch(i -> i.test(new ItemStack(Items.BOOK)))
            && ingredients.stream().anyMatch(i -> i.test(new ItemStack(Items.STICK)));
        report.addProperty("quest_book_original_book_and_wooden_stick_recipe", original);
        if (!original) errors.add("Quest book recipe differs from the original book + stickWood");
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        return new ServerPlayer(server, server.overworld(),
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
    }
    private static int books(ServerPlayer player) {
        return player.getInventory().countItem(PackContent.QUEST_BOOK.get());
    }
}
