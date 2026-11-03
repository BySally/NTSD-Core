package com.ntnh.ntnhcore;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

/** Isolated client model/localisation checks; does not automate the user's interface. */
final class LegacyWirelessClient {
    private static boolean done;
    static void install(){if(Boolean.getBoolean("ntnhcore.validation"))MinecraftForge.EVENT_BUS.addListener(LegacyWirelessClient::tick);}
    private static void tick(TickEvent.ClientTickEvent event){
        var mc=Minecraft.getInstance();if(done||event.phase!=TickEvent.Phase.END||mc.level==null)return;done=true;
        var result=new JsonObject();var errors=new JsonArray();int states=0,items=0,pages=0,scenes=0;
        try{
            for(String id:new String[]{"wireless_connect","wireless_hub"}){
                var block=BuiltInRegistries.BLOCK.get(new ResourceLocation("expatternprovider",id));
                for(var state:block.getStateDefinition().getPossibleStates()){
                    var model=mc.getBlockRenderer().getBlockModel(state);boolean visible=false,missing=model==mc.getModelManager().getMissingModel();
                    for(Direction side:new Direction[]{null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})
                        for(var q:model.getQuads(state,side,RandomSource.create(1),ModelData.EMPTY,null)){
                            visible=true;missing|=q.getSprite().contents().name().toString().equals("minecraft:missingno");
                        }
                    if(!visible||missing)errors.add(id+" "+state+" visible="+visible+" missing="+missing);states++;
                }
            }
            for(String id:new String[]{"wireless_connect","wireless_hub","wireless_tool","wireless_advanced_tool"}){
                var stack=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("expatternprovider",id)));
                var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
                if(model==mc.getModelManager().getMissingModel())errors.add("Missing item model: "+id);
                if(!stack.getHoverName().getString().matches(".*[А-Яа-яЁё].*"))errors.add("Non-Russian name: "+id+" "+stack.getHoverName().getString());items++;
            }
            var guideApi=Class.forName("guideme.PageCollection");
            var guides=(java.util.Collection<?>)Class.forName("guideme.Guides").getMethod("getAll").invoke(null);
            Object wirelessGuide=null;
            for(String name:new String[]{"epp_intro-index","wireless_connector","wireless_hub","wireless_advanced_setup"}){
                var id=new ResourceLocation("expatternprovider","epp_intro/"+name+".md");boolean found=false;
                for(var guide:guides){
                    var parsed=guideApi.getMethod("getParsedPage",ResourceLocation.class).invoke(guide,id);if(parsed==null)continue;
                    found=true;wirelessGuide=guide;
                    if(!"ru_ru".equals(parsed.getClass().getMethod("getLanguage").invoke(parsed)))errors.add("Guide page is not Russian: "+name);
                    if(guideApi.getMethod("getPage",ResourceLocation.class).invoke(guide,id)==null)errors.add("Guide page cannot compile: "+name);
                    pages++;break;
                }
                if(!found)errors.add("Guide page not loaded: "+name);
            }
            if(wirelessGuide!=null)for(String name:new String[]{"wireless_connector_off","wireless_connector_on","wireless_connector_setup"}){
                var data=(byte[])guideApi.getMethod("loadAsset",ResourceLocation.class).invoke(wirelessGuide,new ResourceLocation("expatternprovider","structure/"+name+".snbt"));
                if(data==null||data.length==0)errors.add("Guide scene structure missing: "+name);
                else {net.minecraft.nbt.TagParser.parseTag(new String(data,java.nio.charset.StandardCharsets.UTF_8));scenes++;}
            }
        }catch(Exception|LinkageError e){errors.add(e.toString());}
        result.addProperty("passed",errors.isEmpty());result.addProperty("wireless_block_states_checked",states);result.addProperty("wireless_item_models_and_ru_names_checked",items);result.add("errors",errors);
        result.addProperty("russian_guide_pages_loaded_and_compiled",pages);result.addProperty("native_wireless_scene_structures_loaded",scenes);
        try{var dir=FMLPaths.CONFIGDIR.get().resolve("ntnhcore");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve("wireless-client-verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(java.io.IOException e){org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Cannot export wireless client check",e);}
    }
}
