package com.ntnh.ntnhcore;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** GrassIsAnnoying: keep attacks within vanilla reach and check solid block occlusion. */
@Mod.EventBusSubscriber(modid="ntnhcore", value=Dist.CLIENT)
public final class ClientModules {
    private static boolean legacyFontInstalled;
    @SubscribeEvent
    public static void font(net.minecraftforge.client.event.ScreenEvent.Opening event){
        if(!legacyFontInstalled&&event.getNewScreen() instanceof funwayguy.betterquesting.bootstrap.forge.client.MCScreenProxy){
            LegacyBookFont.install();legacyFontInstalled=true;
        }
    }
    /** FastEquip for the player's inventory, using vanilla validated container transactions. */
    @SubscribeEvent
    public static void equip(net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
        if(event.getButton()!=1 || !(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen screen))return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.gameMode==null || !mc.player.containerMenu.getCarried().isEmpty())return;
        var slot=screen.getSlotUnderMouse();
        if(slot==null || slot.index<9 || slot.index>44 || !slot.hasItem() || !slot.mayPickup(mc.player))return;
        var stack=slot.getItem();
        var equipment=net.minecraft.world.entity.Mob.getEquipmentSlotForItem(stack);
        if(equipment.getType()!=net.minecraft.world.entity.EquipmentSlot.Type.ARMOR)return;
        int target=8-equipment.getIndex();var menu=mc.player.inventoryMenu;
        if(!menu.getSlot(target).mayPlace(stack) || !menu.getSlot(target).mayPickup(mc.player))return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId,slot.index,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);
        mc.gameMode.handleInventoryMouseClick(menu.containerId,target,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);
        mc.gameMode.handleInventoryMouseClick(menu.containerId,slot.index,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!(mc.hitResult instanceof BlockHitResult block)) return;
        if (!mc.level.getBlockState(block.getBlockPos()).getCollisionShape(mc.level, block.getBlockPos()).isEmpty()) return;
        var camera = mc.getCameraEntity();
        if (camera == null) return;
        double reach = mc.gameMode.hasFarPickRange() ? 6.0 : 3.0;
        var eye = camera.getEyePosition();
        var direction = camera.getViewVector(1);
        var end = eye.add(direction.scale(reach));
        var solid = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
        double limit = solid.getType() == HitResult.Type.MISS ? reach*reach : eye.distanceToSqr(solid.getLocation());
        var hit = ProjectileUtil.getEntityHitResult(camera, eye, end,
            camera.getBoundingBox().expandTowards(direction.scale(reach)).inflate(1),
            entity -> !entity.isSpectator() && entity.isPickable(), limit);
        if (hit != null) {
            mc.hitResult = hit;
            mc.crosshairPickEntity = hit.getEntity();
        }
    }
}
