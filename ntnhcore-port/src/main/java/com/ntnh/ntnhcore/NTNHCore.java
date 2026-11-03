package com.ntnh.ntnhcore;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The required common NTNHCore module and reproducible content audit for this pack. */
@Mod("ntnhcore")
public final class NTNHCore {
    public NTNHCore() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
            LegacyWireless.CONFIG,"ntnhcore-wireless.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
            LegacyEnergyConverter.CONFIG,"ntnhcore-energy.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
            LegacyRadialEngine.CONFIG,"ntnhcore-radialengine.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
            LegacyRfEnergyConverter.CONFIG,"ntnhcore-rf-energy.toml");
        net.minecraftforge.fml.DistExecutor.safeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
            () -> LegacyQuestLineSetup::install);
        PackContent.bootstrap(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        PackOreFeature.bootstrap(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(
            (net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(BQQuestTasks::install));
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,BQShutdownState::starting);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,BQShutdownState::stopping);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,BQShutdownVerification::stopping);
        MinecraftForge.EVENT_BUS.addListener(QuestBookGrant::login);
        MinecraftForge.EVENT_BUS.addListener(QuestBookGrant::clone);
        MinecraftForge.EVENT_BUS.addListener(BQQuestTasks::tick);
        MinecraftForge.EVENT_BUS.addListener(BQChapterOrder::tick);
        MinecraftForge.EVENT_BUS.addListener(LegacyWoodHarvest::speed);
        MinecraftForge.EVENT_BUS.addListener(LegacyWoodHarvest::breaking);
        MinecraftForge.EVENT_BUS.addListener(LegacyDrawerTape::rightClick);
        MinecraftForge.EVENT_BUS.addListener(LegacyDrawerTape::leftClick);
    }

    private void commands(RegisterCommandsEvent event) {
        // Retain the legacy command; changing health is an administrator operation.
        event.getDispatcher().register(Commands.literal("coloredhearts")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("amount", FloatArgumentType.floatArg(2, 1024))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    float amount = FloatArgumentType.getFloat(context, "amount");
                    var attribute = player.getAttribute(Attributes.MAX_HEALTH);
                    if (attribute == null) return 0;
                    attribute.setBaseValue(amount);
                    player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
                    context.getSource().sendSuccess(() -> Component.translatable("ntnhcore.message.max_health",amount), false);
                    return 1;
                })));
        event.getDispatcher().register(Commands.literal("ntnh_audit")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                export(context.getSource().getServer());
                context.getSource().sendSuccess(() -> Component.translatable("ntnhcore.message.audit_exported"), false);
                return 1;
            }));
        event.getDispatcher().register(Commands.literal("ntnh_verify")
            .requires(source -> source.hasPermission(2))
            .executes(context -> PackVerification.run(context.getSource().getServer())));
        event.getDispatcher().register(Commands.literal("ntnh_quest_status")
            .requires(source -> source.hasPermission(2))
            .executes(context -> BQProgressAudit.write(context.getSource().getServer())));
    }

    private void started(ServerStartedEvent event) { export(event.getServer()); }

    private static void export(net.minecraft.server.MinecraftServer server) {
        try {
            var gson = new GsonBuilder().setPrettyPrinting().create();
            Path dir = FMLPaths.CONFIGDIR.get().resolve("ntnhcore");
            Files.createDirectories(dir);
            JsonObject data = new JsonObject();
            JsonObject items = new JsonObject();
            for (var id : BuiltInRegistries.ITEM.keySet()) {
                var item = BuiltInRegistries.ITEM.get(id);
                JsonObject row = new JsonObject();
                row.addProperty("translation", item.getDescriptionId());
                row.addProperty("name", item.getDescription().getString());
                items.add(id.toString(), row);
            }
            data.add("items", items);
            JsonArray blocks = new JsonArray();
            for (var id : BuiltInRegistries.BLOCK.keySet()) blocks.add(id.toString());
            data.add("blocks", blocks);
            JsonObject recipes = new JsonObject();
            JsonObject results = new JsonObject();
            for (var recipe : server.getRecipeManager().getRecipes()) {
                recipes.addProperty(recipe.getId().toString(), BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString());
                var result = recipe.getResultItem(server.registryAccess());
                if (!result.isEmpty()) results.addProperty(recipe.getId().toString(), BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
            }
            data.add("recipes", recipes);
            data.add("recipe_results", results);
            JsonObject tags = new JsonObject();
            BuiltInRegistries.ITEM.getTags().forEach(pair -> {
                JsonArray values = new JsonArray();
                pair.getSecond().stream().forEach(holder -> values.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString()));
                tags.add(pair.getFirst().location().toString(), values);
            });
            data.add("item_tags", tags);
            JsonArray entities = new JsonArray();
            BuiltInRegistries.ENTITY_TYPE.keySet().forEach(id -> entities.add(id.toString()));
            data.add("entities", entities);
            JsonArray dimensions = new JsonArray();
            server.levelKeys().forEach(key -> dimensions.add(key.location().toString()));
            data.add("dimensions", dimensions);
            // HBM's own flattening table is the authority for legacy metadata families.
            Path requests = dir.resolve("legacy-families.json");
            JsonObject legacy = new JsonObject();
            if (Files.exists(requests)) {
                var families = gson.fromJson(Files.readString(requests), String[].class);
                var cls = Class.forName("com.hbm.items.LegacyMetaIds");
                var lookup = cls.getMethod("lookup", String.class, int.class);
                for (String family : families) {
                    JsonObject variants = new JsonObject();
                    for (int metadata=0; metadata<512; metadata++) {
                        Object id=lookup.invoke(null, family, metadata);
                        if (id != null) variants.addProperty(Integer.toString(metadata), id.toString());
                    }
                    if (variants.size() > 0) legacy.add(family, variants);
                }
            }
            data.add("legacy_hbm", legacy);
            Path temp = dir.resolve("registry-audit.json.tmp");
            Files.writeString(temp, gson.toJson(data), StandardCharsets.UTF_8);
            Files.move(temp, dir.resolve("registry-audit.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Cannot export NTNH migration audit", error);
        }
    }
}
