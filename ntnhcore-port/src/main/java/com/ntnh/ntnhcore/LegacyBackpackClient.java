package com.ntnh.ntnhcore;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Model/resource validation in the separate test client, without controlling the user's desktop. */
final class LegacyBackpackClient {
    private static boolean done;
    static void install(){if(Boolean.getBoolean("ntnhcore.validation"))MinecraftForge.EVENT_BUS.addListener(LegacyBackpackClient::tick);}
    private static void tick(TickEvent.ClientTickEvent event){
        var mc=Minecraft.getInstance();if(done||event.phase!=TickEvent.Phase.END||mc.level==null)return;done=true;
        var result=new JsonObject();var errors=new JsonArray();int items=0,keys=0;
        try(var input=LegacyBackpacks.class.getResourceAsStream("/ntnhcore/legacy-backpack-source.json")){
            var source=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            var ids=new java.util.LinkedHashSet<String>();source.getAsJsonArray("native_bag_variants").forEach(v->ids.add(v.getAsString()));
            ids.addAll(java.util.List.of("travelersbackpack:backpack_tank","travelersbackpack:red_sleeping_bag","travelersbackpack:hose_nozzle","travelersbackpack:hose"));
            for(String id:ids){
                var stack=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
                if(stack.isEmpty()||model==mc.getModelManager().getMissingModel())errors.add("Missing backpack item/model: "+id);
                if(!stack.getHoverName().getString().matches(".*[А-Яа-яЁё].*"))errors.add("Missing Russian item name: "+id);items++;
            }
            try(var resource=mc.getResourceManager().open(new ResourceLocation("travelersbackpack","lang/en_us.json"))){
                var english=JsonParser.parseReader(new InputStreamReader(resource,StandardCharsets.UTF_8)).getAsJsonObject();
                for(String key:english.keySet()){if(!net.minecraft.client.resources.language.I18n.exists(key))errors.add("Missing translation key: "+key);keys++;}
            }
            // BQ sends the quest database on book access, not necessarily on the first world tick.
            // Server verification checks the loaded quest; this hook validates client assets only.
        }catch(Exception|LinkageError e){errors.add(e.toString());}
        result.addProperty("passed",errors.isEmpty());result.add("errors",errors);result.addProperty("backpack_native_item_models_and_ru_names_checked",items);result.addProperty("backpack_translation_keys_loaded",keys);
        result.addProperty("human_visual_review",false);
        try{var dir=FMLPaths.CONFIGDIR.get().resolve("ntnhcore");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve("backpack-client-verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(java.io.IOException e){org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Cannot export backpack client verification",e);}
    }
}
