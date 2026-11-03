package com.ntnh.ntnhcore;

import com.google.gson.*;
import com.jaquadro.minecraft.storagedrawers.block.BlockDrawers;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

/** Model-baking validation only; no UI control or automatic actions in ordinary client sessions. */
final class LegacyDrawerTapeClient {
    private static boolean done;
    static void install(){if(Boolean.getBoolean("ntnhcore.validation"))MinecraftForge.EVENT_BUS.addListener(LegacyDrawerTapeClient::tick);}
    private static void tick(TickEvent.ClientTickEvent event){
        var mc=Minecraft.getInstance();if(done||event.phase!=TickEvent.Phase.END||mc.level==null)return;done=true;
        var result=new JsonObject();var errors=new JsonArray();int states=0;
        try{
            for(var block:BuiltInRegistries.BLOCK)if(block instanceof BlockDrawers){
                for(var direction:Direction.Plane.HORIZONTAL){
                    var state=block.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,direction).setValue(LegacyDrawerTape.SEALED,true);
                    var model=mc.getBlockRenderer().getBlockModel(state);boolean tape=false,missing=false;
                    for(Direction face:new Direction[]{null,Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.UP,Direction.DOWN})
                        for(var quad:model.getQuads(state,face,RandomSource.create(1),ModelData.EMPTY,null)){
                            var texture=quad.getSprite().contents().name().toString();tape|=texture.equals("ntnhcore:block/drawer_tape");missing|=texture.equals("minecraft:missingno");
                        }
                    if(!tape||missing)errors.add(BuiltInRegistries.BLOCK.getKey(block)+" facing="+direction+" tape="+tape+" missing="+missing);states++;
                }
            }
            var itemModel=mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(LegacyDrawerTape.TAPE.get()),mc.level,mc.player,0);
            if(itemModel==mc.getModelManager().getMissingModel())errors.add("Missing tape item model");
        }catch(Exception|LinkageError e){errors.add(e.toString());}
        result.addProperty("passed",errors.isEmpty());result.addProperty("native_sealed_directional_models",states);result.add("errors",errors);
        try{var dir=FMLPaths.CONFIGDIR.get().resolve("ntnhcore");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve("drawer-tape-client-verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(java.io.IOException e){org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Cannot export drawer tape model check",e);}
    }
}
