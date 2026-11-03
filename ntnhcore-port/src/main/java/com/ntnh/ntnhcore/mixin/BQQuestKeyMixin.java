package com.ntnh.ntnhcore.mixin;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Change the real registered default, leaving Minecraft's drop key untouched. */
@Mixin(targets="funwayguy.betterquesting.bootstrap.forge.client.MCKeyBindings",remap=false)
public abstract class BQQuestKeyMixin {
    @Shadow @Final @Mutable public static KeyMapping OPEN_QUESTS;
    @Inject(method="<clinit>",at=@At("RETURN"),remap=false)
    private static void ntsd$defaultKey(CallbackInfo ci){OPEN_QUESTS=new KeyMapping("betterquesting.key.open_quests",GLFW.GLFW_KEY_GRAVE_ACCENT,"BetterQuesting");}
}
