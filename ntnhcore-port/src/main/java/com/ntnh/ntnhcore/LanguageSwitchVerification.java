package com.ntnh.ntnhcore;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import funwayguy.betterquesting.client.text.BQTranslate;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.minecraft.locale.Language;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in regression test in an isolated client, including non-UTF-8 Windows JVMs. */
@Mod.EventBusSubscriber(modid="ntnhcore",value=Dist.CLIENT)
public final class LanguageSwitchVerification {
    private static final String[] LOCALES={"ru_ru","en_us","en_gb","ru_ru","ru_ru","en_us","en_gb","ru_ru"};
    private static final List<JsonObject> RESULTS=new ArrayList<>();
    private static int stage,settled;
    private static boolean waiting,done;
    private static Throwable failure;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("ntnhcore.languageVerification")||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(waiting||mc.getOverlay()!=null||mc.screen==null)return;
        if(failure!=null){finish(mc,failure);return;}
        if(++settled<30)return;
        settled=0;
        try{
            if(stage>0)check(mc,LOCALES[stage-1]);
            if(stage==LOCALES.length){finish(mc,null);return;}
            String locale=LOCALES[stage++];
            if(stage==5){
                var packs=new ArrayList<>(mc.getResourcePackRepository().getSelectedIds());
                if(!mc.getResourcePackRepository().getAvailableIds().contains("file/NTNH-Russian-1.20.1.zip"))throw new IllegalStateException("Missing language pack for enabled-pack test");
                packs.add("file/NTNH-Russian-1.20.1.zip");mc.getResourcePackRepository().setSelected(packs);
            }
            mc.getLanguageManager().setSelected(locale);mc.options.languageCode=locale;
            waiting=true;
            mc.reloadResourcePacks().whenComplete((unused,error)->mc.execute(()->{failure=error;waiting=false;}));
        }catch(Throwable error){finish(mc,error);}
    }

    private static JsonObject json(String path)throws Exception{
        try(var input=LanguageSwitchVerification.class.getResourceAsStream(path)){
            if(input==null)throw new IllegalStateException("Missing verification resource: "+path);
            return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static void check(Minecraft mc,String locale)throws Exception{
        var expected=json("/assets/ntnhcore/lang/"+(locale.equals("ru_ru")?"ru_ru":"en_us")+".json");
        var language=Language.getInstance();int checked=0;
        for(var entry:expected.entrySet()){
            String value=entry.getValue().getAsString();
            if(!value.equals(language.getOrDefault(entry.getKey())))throw new IllegalStateException("Incorrect "+locale+" translation: "+entry.getKey());
            checked++;
        }
        int literals=0;
        for(var entry:json("/ntnhcore/quest-text-keys.json").entrySet()){
            String key=entry.getValue().getAsString();
            if(!expected.get(key).getAsString().equals(BQTranslate.translate(entry.getKey())))throw new IllegalStateException("Incorrect BQ literal: "+key);
            literals++;
        }
        var sources=new java.util.HashMap<String,String>();
        json("/ntnhcore/quest-text-keys.json").entrySet().forEach(e->sources.put(e.getValue().getAsString(),e.getKey()));
        int questFields=0;
        // The isolated test stays at the title screen. Exercise native Quest
        // methods in memory; no player, world, task or progress data is created.
        if(mc.level!=null)throw new IllegalStateException("Language regression must not target a world");
        for(var entry:json("/ntnhcore/quest-text-contexts.json").entrySet()){
            String key=entry.getKey(),source=sources.get(entry.getValue().getAsString());
            String[] parts=key.split("\\.");int id=Integer.parseInt(parts[2]);boolean name=parts[3].equals("name");
            var database=funwayguy.betterquesting.questing.quests.QuestDatabase.INSTANCE;
            var quest=database.getValue(id);if(quest==null)quest=database.createNew(id);
            quest.getInfo().setProp(name?funwayguy.betterquesting.properties.CommonProps.NAME:funwayguy.betterquesting.properties.CommonProps.DESC,source);
            String actual=name?quest.getNameKey():quest.getDescKey();
            if(!key.equals(actual)||!expected.get(key).getAsString().equals(BQTranslate.translate(actual)))throw new IllegalStateException("Native quest localization failed: "+key);
            questFields++;
        }
        String title=BQTranslate.translate("betterquesting.title.quest_lines");
        if(locale.equals("ru_ru")&&!title.equals("Цепочки квестов"))throw new IllegalStateException("BQ UTF-8 translation corrupted: "+title);
        if(!locale.equals("ru_ru")&&(title.equals("betterquesting.title.quest_lines")||title.matches(".*[А-Яа-яЁё].*")))throw new IllegalStateException("BQ English fallback failed: "+title);
        for(String font:new String[]{"default","uniform"}){
            var provider=json("/assets/minecraft/font/"+font+".json").getAsJsonArray("providers").get(0);
            var result=UnihexProvider.Definition.CODEC.codec().parse(JsonOps.INSTANCE,provider);
            if(result.error().isPresent())throw new IllegalStateException("Invalid "+font+" font: "+result.error().get().message());
        }
        LegacyBookFont.install();
        if(mc.font.width("Привет, друг! Quest book 123")<=0)throw new IllegalStateException("Font failed to produce text width");
        var row=new JsonObject();row.addProperty("locale",locale);row.addProperty("core_translations_checked",checked);
        row.addProperty("quest_literals_checked",literals);row.addProperty("bq_title",title);
        row.addProperty("bq_locale",BQTranslate.get().getCurLocale().getCode());
        row.addProperty("native_quest_fields_checked",questFields);
        row.addProperty("font_definitions_decoded",2);row.addProperty("minecraft_text_width",mc.font.width("Привет, друг! Quest book 123"));
        row.addProperty("russian_resource_pack_enabled",mc.getResourcePackRepository().getSelectedIds().contains("file/NTNH-Russian-1.20.1.zip"));
        row.addProperty("passed",true);RESULTS.add(row);
        org.slf4j.LoggerFactory.getLogger("ntnhcore").info("Language regression passed: {}",row);
    }
    private static void finish(Minecraft mc,Throwable error){
        done=true;
        try{
            var root=new JsonObject();root.addProperty("passed",error==null);root.addProperty("default_charset",Charset.defaultCharset().name());
            root.addProperty("game_language",mc.getLanguageManager().getSelected());
            root.add("steps",new GsonBuilder().create().toJsonTree(RESULTS));
            if(error!=null)root.addProperty("error",error.toString());
            var path=mc.gameDirectory.toPath().resolve("config/ntnhcore/language-switch-verification.json");
            Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(root),StandardCharsets.UTF_8);
            if(error!=null)org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Language regression failed",error);
        }catch(Exception writeError){org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Cannot save language regression",writeError);}
        mc.stop();
    }
}
