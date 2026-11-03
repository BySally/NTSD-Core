package com.ntnh.ntnhcore.mixin;

import com.ntnh.ntnhcore.QuestText;
import funwayguy.betterquesting.questing.quests.Quest;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The original Russian book reuses some titles for different English quests. */
@Mixin(value=Quest.class,remap=false)
public abstract class BQQuestLocaleMixin {
    @Inject(method="getNameKey",at=@At("RETURN"),cancellable=true,remap=false)
    private void ntnh$name(CallbackInfoReturnable<String> callback){ntnh$field("name",callback);}
    @Inject(method="getDescKey",at=@At("RETURN"),cancellable=true,remap=false)
    private void ntnh$description(CallbackInfoReturnable<String> callback){ntnh$field("desc",callback);}
    private void ntnh$field(String field,CallbackInfoReturnable<String> callback){
        String key=QuestText.questKey(QuestDatabase.INSTANCE.getID((Quest)(Object)this),field,callback.getReturnValue());
        if(key!=null)callback.setReturnValue(key);
    }
}
