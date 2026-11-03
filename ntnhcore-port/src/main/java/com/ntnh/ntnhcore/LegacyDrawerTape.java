package com.ntnh.ntnhcore;

import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerAttributesModifiable;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.item.ItemKey;
import com.jaquadro.minecraft.storagedrawers.security.SecurityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;

/** Original SD packaging rules; the seal is a persistent, client-synchronised block property. */
public final class LegacyDrawerTape {
    /** Native suspension is independent of the temporary seal and must remain independent in NBT. */
    public interface NativeSuspension {boolean ntnh$rawSuspended();}
    public static final BooleanProperty SEALED=BooleanProperty.create("ntnh_sealed");
    public static RegistryObject<Item> TAPE;
    static void register(DeferredRegister<Item> items){TAPE=items.register("drawer_tape",Tape::new);}
    public static boolean sealed(BlockState state){return state.hasProperty(SEALED)&&state.getValue(SEALED);}
    public static boolean sealed(Level level,BlockPos pos){return sealed(level.getBlockState(pos));}

    // Called by the native mutable attribute adapter. Only changing this property keeps the same BE.
    public static boolean change(BlockEntityDrawers tile,boolean value){
        var level=tile.getLevel();if(level==null)return false;
        var state=level.getBlockState(tile.getBlockPos());
        if(!state.hasProperty(SEALED)||sealed(state)==value)return false;
        if(!level.setBlock(tile.getBlockPos(),state.setValue(SEALED,value),3))return false;
        tile.setChanged();return true;
    }
    public static void rightClick(PlayerInteractEvent.RightClickBlock event){
        var level=event.getLevel();var pos=event.getPos();if(!sealed(level,pos))return;
        var player=event.getEntity();var held=event.getItemStack();
        // Native keys still control locks and concealment, as in the source mod.
        if(held.getItem() instanceof ItemKey)return;
        if(held.isEmpty()&&player.isShiftKeyDown()&&level.getBlockEntity(pos) instanceof BlockEntityDrawers tile
            &&SecurityManager.hasAccess(player,tile)
            &&SecurityManager.canInteract(player,event.getHand(),pos)
            &&level.mayInteract(player,pos)&&player.mayUseItemAt(pos,event.getFace(),held)){
            if(!level.isClientSide)((IDrawerAttributesModifiable)tile.getDrawerAttributes()).setIsSealed(false);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        }else event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event){
        // Suppress the native click-to-extract action; normal block mining must remain possible.
        if(sealed(event.getLevel(),event.getPos()))event.setUseBlock(Event.Result.DENY);
    }
    private static final class Tape extends Item {
        // 1.7.10 broke the roll when damage > 8. Nine operations reproduce that behaviour in 1.20.1.
        Tape(){super(new Properties().durability(9));}
        @Override public boolean isEnchantable(ItemStack stack){return false;}
        @Override public boolean isValidRepairItem(ItemStack tool,ItemStack repair){return false;}
        @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
        @Override public InteractionResult useOn(UseOnContext context){
            var level=context.getLevel();var pos=context.getClickedPos();var player=context.getPlayer();
            if(!(level.getBlockEntity(pos) instanceof BlockEntityDrawers tile))return InteractionResult.PASS;
            if(player==null||sealed(level,pos)||!level.mayInteract(player,pos)
                ||!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand())
                ||!SecurityManager.hasOwnership(player.getGameProfile(),tile)
                ||!SecurityManager.canInteract(player,context.getHand(),pos))return InteractionResult.FAIL;
            if(!level.isClientSide){
                if(!((IDrawerAttributesModifiable)tile.getDrawerAttributes()).setIsSealed(true))return InteractionResult.FAIL;
                context.getItemInHand().hurtAndBreak(1,player,p->p.broadcastBreakEvent(context.getHand()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("ntnhcore.drawer_tape.hint"));
            lines.add(Component.translatable("ntnhcore.drawer_tape.unseal"));
        }
    }
}
