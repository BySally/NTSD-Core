package com.ntnh.ntnhcore;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="ntnhcore",value=Dist.CLIENT)
public final class LegacyRfEnergyClient {
    private static boolean checked;
    @SubscribeEvent public static void verify(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("ntnhcore.validation")||checked||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;checked=true;
        var report=new JsonObject();var errors=new JsonArray();int faces=0;
        try{
            var state=LegacyRfEnergyConverter.BLOCK.get().defaultBlockState();var model=mc.getBlockRenderer().getBlockModel(state);
            if(model==mc.getModelManager().getMissingModel())throw new IllegalStateException("RF converter block model missing");
            for(var side:Direction.values()){
                var quads=model.getQuads(state,side,net.minecraft.util.RandomSource.create(0));
                if(quads.isEmpty())throw new IllegalStateException("RF converter face missing "+side);
                for(var q:quads)if(!q.getSprite().contents().name().toString().equals("ntnhcore:block/machine_converter_rf_he"))throw new IllegalStateException("RF converter original texture missing");
                faces+=quads.size();
            }
            var stack=new ItemStack(LegacyRfEnergyConverter.ITEM.get());
            if(mc.getItemRenderer().getModel(stack,mc.level,mc.player,0)==mc.getModelManager().getMissingModel())throw new IllegalStateException("RF converter item model missing");
            if(!stack.getHoverName().getString().equals("Конвертер RF в HE"))throw new IllegalStateException("RF converter Russian name missing");
        }catch(Exception|LinkageError e){errors.add(e.toString());}
        report.addProperty("passed",errors.isEmpty());report.add("errors",errors);report.addProperty("original_textured_block_faces_checked",faces);report.addProperty("human_visual_review",false);
        try{var dir=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ntnhcore");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve("rf-converter-client-verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
