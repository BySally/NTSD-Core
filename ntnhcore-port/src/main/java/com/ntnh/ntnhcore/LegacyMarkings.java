package com.ntnh.ntnhcore;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Missing original drawings on the same native floor/wall implementation. */
final class LegacyMarkings {
    static void register(DeferredRegister<Block> blocks,DeferredRegister<Item> items){
        try(var input=LegacyMarkings.class.getResourceAsStream("/ntnhcore/legacy-markings.json")){
            if(input==null)throw new IllegalStateException("Missing original marking list");
            for(var element:JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonArray()){
                var row=element.getAsJsonObject();if(!row.get("restored").getAsBoolean())continue;
                String name=row.get("name").getAsString(),kind=row.get("kind").getAsString();
                var block=blocks.register(name,()->create(kind));
                items.register(name,()->new BlockItem(block.get(),new Item.Properties()));
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static Block create(String kind){
        var id=new ResourceLocation("ags_modernmarkings",kind+"_marking_exit");
        var template=ForgeRegistries.BLOCKS.getValue(id);
        if(template==null||!id.equals(ForgeRegistries.BLOCKS.getKey(template)))throw new IllegalStateException("Missing marking dependency "+id);
        try{
            return (Block)Class.forName("net.aminoglycoside.modernmarkings.block.custom.Marking"+(kind.equals("floor")?"Floor":"Wall"))
                .getConstructor(BlockBehaviour.Properties.class).newInstance(BlockBehaviour.Properties.copy(template));
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported ModernMarkings API",e);}
    }
}
