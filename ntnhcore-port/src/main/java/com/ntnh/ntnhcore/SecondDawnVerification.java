package com.ntnh.ntnhcore;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import funwayguy.betterquesting.client.gui.*;
import funwayguy.betterquesting.client.layouts.args.GArgBasic;
import funwayguy.betterquesting.client.layouts.presets.PresetGui;
import funwayguy.betterquesting.client.text.BQTranslate;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Opt-in integration check; only run in a disposable loopback client profile. */
@Mod.EventBusSubscriber(modid="ntnhcore",value=Dist.CLIENT)
public final class SecondDawnVerification {
    private static final JsonArray results=new JsonArray();
    private static int step,settle;private static boolean done,waiting;
    private static Throwable failure;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("ntnhcore.ntsdVerification")||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null||waiting)return;
        if(++settle<45)return;settle=0;
        try{
            if(failure!=null)throw new IllegalStateException("Language reload",failure);
            if(step==0){
                var key=java.util.Arrays.stream(mc.options.keyMappings).filter(k->k.getName().equals("betterquesting.key.open_quests")).findFirst().orElseThrow();
                require(key.getDefaultKey().getValue()==96,"Native quest key default must be grave");
                require(key.matches(GLFW.GLFW_KEY_GRAVE_ACCENT,0)&&!key.matches(GLFW.GLFW_KEY_Q,0),"Quest key conflicts with Q");
                require(mc.options.keyDrop.matches(GLFW.GLFW_KEY_Q,0),"Drop key was changed");
                require(SecondDawnBranding.installed,"Window icon not installed");
                require(QuestDatabase.INSTANCE.getEntries().size()==418,"Quest database not loaded");
                row("key-icon-database",true);setLanguage(mc,"ru_ru");
            }else if(step==5){capture(mc,"ru-issues");setLanguage(mc,"en_us");
            }else if(step<=9){
                int index=step<5?step-1:step-6;
                if(index>0)capture(mc,(step<5?"ru-":"en-")+new String[]{"home","intro","credits"}[index-1]);
                if(index==0){
                    PresetGui.HOME.openWindow(GArgBasic.NONE);
                    require(WindowManager.get().getActive().getRootCanvas() instanceof SecondDawnHome,"Native home factory missing");
                    var quest=QuestDatabase.INSTANCE.getValue(25089);
                    require(BQTranslate.translate(quest.getNameKey()).contains("Second Dawn"),"Quest contextual name not rebranded");
                }else WindowManager.get().openWindowCanvas(new SecondDawnInfo(new SecondDawnHome(),new String[]{"intro","credits","issues"}[index-1]));
            }else if(step==10){capture(mc,"en-issues");GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1440,620);PresetGui.HOME.openWindow(GArgBasic.NONE);
            }else if(step==11){capture(mc,"wide-home");checkSize();GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),800,700);PresetGui.HOME.openWindow(GArgBasic.NONE);
            }else if(step==12){capture(mc,"narrow-home");checkSize();
                PresetGui.PARTY.openWindow(new funwayguy.betterquesting.client.layouts.args.GArgValue<Integer>(new SecondDawnHome(),-1));
            }else if(step==13){capture(mc,"native-party");require(WindowManager.get().getActive()!=null,"Native party UI missing");row("native-party-screen",true);finish(mc,null);return;}
            step++;
        }catch(Throwable error){finish(mc,error);}
    }
    private static void setLanguage(Minecraft mc,String locale){
        mc.getLanguageManager().setSelected(locale);mc.options.languageCode=locale;waiting=true;
        mc.reloadResourcePacks().whenComplete((v,e)->mc.execute(()->{failure=e;waiting=false;}));
    }
    private static void require(boolean condition,String error){if(!condition)throw new IllegalStateException(error);}
    private static void row(String name,boolean passed){var r=new JsonObject();r.addProperty("check",name);r.addProperty("passed",passed);results.add(r);}
    private static void checkSize(){
        var wm=WindowManager.get();var rect=wm.getActive().getRootCanvas().getTransform();
        require(rect.getWidth()==wm.getWidth()&&rect.getHeight()==wm.getHeight(),"Home does not fill resized window");
        row("responsive-home-"+rect.getWidth()+"x"+rect.getHeight(),true);
    }
    private static void capture(Minecraft mc,String name){
        Screenshot.grab(mc.gameDirectory,"ntsd-"+name+".png",mc.getMainRenderTarget(),message->{});
        row(name,true);
    }
    private static void finish(Minecraft mc,Throwable error){
        done=true;
        try{
            var root=new JsonObject();root.addProperty("passed",error==null);root.add("checks",results);
            if(error!=null)root.addProperty("error",error.toString());
            var path=mc.gameDirectory.toPath().resolve("config/ntnhcore/ntsd-verification.json");Files.createDirectories(path.getParent());
            Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(root),StandardCharsets.UTF_8);
            if(error!=null)org.slf4j.LoggerFactory.getLogger("ntnhcore").error("NTSD verification failed",error);
        }catch(Exception e){org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Cannot write NTSD verification",e);}
        mc.stop();
    }
}
