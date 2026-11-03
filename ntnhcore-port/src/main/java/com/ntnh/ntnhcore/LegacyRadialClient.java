package com.ntnh.ntnhcore;

import com.hbm.inventory.fluid.Fluids;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.util.List;

@Mod.EventBusSubscriber(modid="ntnhcore",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class LegacyRadialClient {
    private static ResourceLocation model(String part){return new ResourceLocation("ntnhcore","block/selenium_"+part);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{
        MenuScreens.register(LegacyRadialEngine.MENU.get(),Screen::new);
        if(Boolean.getBoolean("ntnhcore.validation"))net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(LegacyRadialClient::verify);
    });}
    private static boolean verified;
    private static void verify(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        var mc=Minecraft.getInstance();if(verified||mc.level==null||e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;verified=true;
        var result=new com.google.gson.JsonObject();var errors=new com.google.gson.JsonArray();int quads=0;
        try{
            for(String p:List.of("body","piston","rotor")){
                var baked=mc.getModelManager().getModel(model(p));if(baked==mc.getModelManager().getMissingModel())throw new IllegalStateException("Missing radial part "+p);
                var faces=baked.getQuads(null,null,net.minecraft.util.RandomSource.create(0),ModelData.EMPTY,RenderType.cutout());
                if(faces.isEmpty())throw new IllegalStateException("Empty radial geometry "+p);
                for(var face:faces)if(face.getSprite().contents().name().toString().contains("missingno"))throw new IllegalStateException("Missing radial texture "+p);
                quads+=faces.size();
            }
            var stack=new net.minecraft.world.item.ItemStack(LegacyRadialEngine.ITEM.get());
            var itemModel=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
            if(itemModel==mc.getModelManager().getMissingModel()||itemModel.getQuads(null,null,net.minecraft.util.RandomSource.create(0),ModelData.EMPTY,RenderType.cutout()).isEmpty())throw new IllegalStateException("Missing or empty radial item model");
            if(!stack.getHoverName().getString().equals("Радиальный двигатель"))throw new IllegalStateException("Missing radial Russian name");
            try(var png=mc.getResourceManager().open(TEXTURE_LOCATION)){if(png.read()<0)throw new IllegalStateException("Empty original GUI texture");}
        }catch(Exception|LinkageError ex){errors.add(ex.toString());}
        result.addProperty("passed",errors.isEmpty());result.add("errors",errors);result.addProperty("radial_original_model_quads_checked",quads);result.addProperty("human_visual_review",false);
        try{var dir=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ntnhcore");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve("radial-client-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    }
    private static final ResourceLocation TEXTURE_LOCATION=new ResourceLocation("ntnhcore","textures/gui/gui_selenium.png");
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){for(String p:List.of("body","piston","rotor"))e.register(model(p));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(LegacyRadialEngine.TYPE.get(),Renderer::new);}
    private static final class Renderer implements BlockEntityRenderer<LegacyRadialEngine.Engine> {
        Renderer(BlockEntityRendererProvider.Context c){}
        private void part(String name,LegacyRadialEngine.Engine e,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            var mc=Minecraft.getInstance();var baked=mc.getModelManager().getModel(model(name));
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)),e.getBlockState(),baked,1,1,1,light,overlay,ModelData.EMPTY,RenderType.cutout());
        }
        @Override public void render(LegacyRadialEngine.Engine e,float partial,PoseStack pose,MultiBufferSource b,int light,int overlay){
            pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-e.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot()));
            part("body",e,pose,b,light,overlay);pose.translate(0,1,0);
            int count=e.pistonCount;for(int i=0;i<count;i++){pose.pushPose();pose.mulPose(Axis.ZP.rotationDegrees(360F*i/count));part("piston",e,pose,b,light,overlay);pose.popPose();}
            part("rotor",e,pose,b,light,overlay);pose.popPose();
        }
    }
    private static final class Screen extends AbstractContainerScreen<LegacyRadialEngine.Menu> {
        static final ResourceLocation TEXTURE=new ResourceLocation("ntnhcore","textures/gui/gui_selenium.png");
        Screen(LegacyRadialEngine.Menu m,Inventory i,Component t){super(m,i,t);imageWidth=176;imageHeight=222;inventoryLabelY=128;}
        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            g.blit(TEXTURE,leftPos,topPos,0,0,176,222);
            int energy=(int)(160D*menu.power()/Math.max(1,menu.capacity()));if(energy>0)g.blit(TEXTURE,leftPos+8,topPos+108,0,222,energy,16);
            int count=menu.data.get(6);if(count>0)g.blit(TEXTURE,leftPos+26,topPos+81,176,52+16*(count-1),16,16);
            var fluid=Fluids.byNetworkId(menu.data.get(7));int height=(int)(52D*menu.fuel()/Math.max(1,menu.fuelCapacity()));
            if(height>0)g.fill(leftPos+116,topPos+70-height,leftPos+132,topPos+70,0xff000000|fluid.getColor());
            if(count>=3&&menu.fuel()>0&&LegacyRadialEngine.energyPerMb(fluid)>0)g.blit(TEXTURE,leftPos+115,topPos+71,192,0,18,18);
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){
            g.drawString(font,title,(imageWidth-font.width(title))/2,6,0x404040,false);g.drawString(font,playerInventoryTitle,8,inventoryLabelY,0x404040,false);
            if(menu.data.get(6)<3)g.drawString(font,Component.translatable("ntnhcore.radial.minimum"),8,92,0x9f2626,false);
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
            var fluid=Fluids.byNetworkId(menu.data.get(7));
            if(isHovering(116,18,16,52,mx,my))g.renderTooltip(font,List.of(fluid.getDisplayName(),Component.translatable("ntnhcore.radial.fuel",menu.fuel(),menu.fuelCapacity()),Component.translatable("ntnhcore.radial.output",LegacyRadialEngine.energyPerMb(fluid)*menu.data.get(6))),java.util.Optional.empty(),mx,my);
            if(isHovering(8,108,160,16,mx,my))g.renderTooltip(font,Component.literal(menu.power()+" / "+menu.capacity()+" HE"),mx,my);
            if(isHovering(8,18,54,54,mx,my))g.renderTooltip(font,List.of(Component.translatable("ntnhcore.radial.pistons",menu.data.get(6)),Component.translatable("ntnhcore.radial.consumption",menu.data.get(6))),java.util.Optional.empty(),mx,my);
        }
    }
}
