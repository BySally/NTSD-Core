package com.ntnh.ntnhcore.mixin;

import com.ntnh.ntnhcore.QuestText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="funwayguy.betterquesting.client.text.BQTranslate",remap=false)
public abstract class BQQuestTextMixin {
    @Inject(method="translateText",at=@At("HEAD"),cancellable=true,remap=false)
    private void ntnh$questLanguage(String source,Object[] args,CallbackInfoReturnable<String> callback){
        String text=QuestText.resolve(source);
        if(text!=null)callback.setReturnValue(text);
    }
}
