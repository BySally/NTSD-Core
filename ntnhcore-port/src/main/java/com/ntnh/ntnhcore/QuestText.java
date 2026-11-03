package com.ntnh.ntnhcore;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.locale.Language;

/** Localize legacy literal quest text on the client without changing server progress or IDs. */
public final class QuestText {
    private static final Map<String,String> KEYS=load("quest-text-keys.json");
    private static final Map<String,String> CONTEXTS=load("quest-text-contexts.json");
    private QuestText(){}
    private static Map<String,String> load(String file){
        try(var input=QuestText.class.getResourceAsStream("/ntnhcore/"+file)){
            if(input==null)throw new IllegalStateException("Missing bilingual quest text map");
            var result=new HashMap<String,String>();
            JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject()
                .entrySet().forEach(entry->result.put(entry.getKey(),entry.getValue().getAsString()));
            return Map.copyOf(result);
        }catch(java.io.IOException error){throw new IllegalStateException("Cannot load bilingual quest text",error);}
    }
    public static String text(String key){return Language.getInstance().getOrDefault(key);}
    public static String questKey(int id,String field,String source){
        String key="ntnhcore.quest."+id+"."+field;
        String literal=KEYS.get(source);
        return literal!=null&&literal.equals(CONTEXTS.get(key))?key:null;
    }
    /** Null means that native BQ translation/fallback should handle this string. */
    public static String resolve(String source){
        String key=KEYS.get(source);
        if(key==null&&source.startsWith("ntnhcore.")&&Language.getInstance().has(source))key=source;
        return key==null?null:Language.getInstance().getOrDefault(key,source);
    }
}
