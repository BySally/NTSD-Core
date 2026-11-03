package com.ntnh.ntnhcore.mixin;

import com.glodblock.github.extendedae.common.tileentities.TileWirelessConnector;
import com.glodblock.github.extendedae.util.WirelessHelpers;
import com.ntnh.ntnhcore.LegacyWireless;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Consumer;

@Mixin(value=WirelessHelpers.class,remap=false)
public abstract class WirelessLinkMixin {
    @Redirect(method="connectWireless",at=@At(value="FIELD",target="Lcom/glodblock/github/extendedae/config/EPPConfig;wirelessMaxRange:D"),remap=false)
    private static double ntnh$legacyRange(){return LegacyWireless.range();}

    @Inject(method="connectWireless",at=@At("HEAD"),cancellable=true,remap=false)
    private static void ntnh$keepExistingPair(Consumer<Long> setter,CompoundTag tag,Level level,BlockPos pos,Player player,Runnable success,CallbackInfoReturnable<InteractionResult> ci) {
        if(!tag.contains("bind",Tag.TAG_COMPOUND))return;
        var bind=GlobalPos.CODEC.parse(NbtOps.INSTANCE,tag.get("bind")).result().orElse(null);
        if(bind==null||!bind.dimension().equals(level.dimension()))return;
        // Match the old pair exclusivity. Do not silently turn an established pair
        // into three devices sharing one frequency when reusing the setup tool.
        if(occupied(level,pos,bind.pos())||occupied(level,bind.pos(),pos)) {
            player.displayClientMessage(Component.translatable("ntnhcore.wireless.occupied"),true);
            ci.setReturnValue(InteractionResult.FAIL);
        }
    }
    private static boolean occupied(Level level,BlockPos pos,BlockPos expected) {
        return level.getBlockEntity(pos) instanceof TileWirelessConnector tile && tile.getOtherSide()!=null && !tile.getOtherSide().equals(expected);
    }
}
