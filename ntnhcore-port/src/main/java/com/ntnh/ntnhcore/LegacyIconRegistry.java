package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import funwayguy.betterquesting.objects.StackLibrary;
import funwayguy.betterquesting.objects.StackType;
import funwayguy.betterquesting.objects.StackTypeRegistry;
import funwayguy.betterquesting.objects.StackedObj;
import funwayguy.betterquesting.registry.ResourcePath;
import funwayguy.betterquesting.client.gui.resources.textures.IGuiTexture;
import funwayguy.betterquesting.client.gui.resources.textures.GuiTextureSimple;
import funwayguy.betterquesting.client.misc.GuiRectangle;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/** Presentation-only original icons; this registry does not create fake obtainable game items. */
final class LegacyIconRegistry extends StackTypeRegistry<LegacyIconRegistry.Icon> {
    static final ResourcePath ID=new ResourcePath("ntnhcore:legacy_icon");
    private final HashMap<ResourcePath,Icon> icons=new HashMap<>();
    LegacyIconRegistry(){
        try(var stream=LegacyIconRegistry.class.getResourceAsStream("/ntnhcore/legacy-icons.json")){
            if(stream!=null){
                var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                data.entrySet().forEach(entry->{var icon=new Icon(new ResourcePath(entry.getKey()),entry.getValue().getAsJsonObject());icons.put(icon.getID(),icon);});
            }
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
    static void install(){StackLibrary.get().addRegistry(new LegacyIconRegistry());}
    @Override public ResourcePath getRegistryID(){return ID;}
    @Override public String getTypeName(){return QuestText.text("ntnhcore.ui.icons");}
    @Override public boolean addRef(Icon icon){return icons.putIfAbsent(icon.getID(),icon)==null;}
    @Override public Icon getRef(ResourcePath id){return icons.get(id);}
    @Override public Icon getEmpty(){return null;}
    @Override public StackedObj getDefault(){return StackLibrary.EMPTY_STACK;}
    static final class Icon extends StackType {
        final JsonObject data;
        Icon(ResourcePath id,JsonObject data){super(id,ID);this.data=data;}
        @Override public boolean isEmpty(){return false;}
        @Override public String getDisplayName(StackedObj stack){return data.get("name").getAsString();}
        @Override public IGuiTexture getTexture(StackedObj stack,boolean count,boolean tooltip){
            return new GuiTextureSimple(new ResourcePath(data.get("texture").getAsString()),
                new GuiRectangle(0,0,data.get("width").getAsInt(),data.get("height").getAsInt())).maintainAspect(true);
        }
    }
}
