package com.ntnh.ntnhcore;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/** GLFW consumes RGBA frames; the original multi-resolution ICO is retained beside them. */
@Mod.EventBusSubscriber(modid="ntnhcore",value=Dist.CLIENT)
public final class SecondDawnBranding {
    private static boolean attempted;
    static boolean installed;
    static final int[] SIZES={16,24,32,48,64,128,256};
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||attempted)return;
        var mc=net.minecraft.client.Minecraft.getInstance();if(mc.getWindow()==null)return;attempted=true;
        var pixels=new ArrayList<ByteBuffer>();
        try(var frames=GLFWImage.malloc(SIZES.length)){
            for(int i=0;i<SIZES.length;i++){
                int size=SIZES[i];
                try(var input=SecondDawnBranding.class.getResourceAsStream("/assets/ntnhcore/branding/ntsd-"+size+".png")){
                    if(input==null)throw new IllegalStateException("Missing NTSD icon frame "+size);
                    try(var image=NativeImage.read(input)){
                        if(image.getWidth()!=size||image.getHeight()!=size)throw new IllegalStateException("Bad NTSD icon dimensions");
                        var buffer=MemoryUtil.memAlloc(size*size*4);pixels.add(buffer);
                        for(int y=0;y<size;y++)for(int x=0;x<size;x++)buffer.putInt(image.getPixelRGBA(x,y));
                        buffer.flip();frames.position(i).width(size).height(size).pixels(buffer);
                    }
                }
            }
            frames.position(0);GLFW.glfwSetWindowIcon(mc.getWindow().getWindow(),frames);installed=true;
            org.slf4j.LoggerFactory.getLogger("ntnhcore").info("NTSD window icon installed: seven original ICO frames, 16..256 px");
        }catch(Exception error){org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Cannot install NTSD window icon",error);}
        finally{pixels.forEach(MemoryUtil::memFree);}
    }
}
