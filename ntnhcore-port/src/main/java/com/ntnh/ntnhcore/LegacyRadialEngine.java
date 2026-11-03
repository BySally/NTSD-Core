package com.ntnh.ntnhcore;

import com.hbm.api.energymk2.IBatteryItem;
import com.hbm.api.energymk2.IEnergyConnectorBlock;
import com.hbm.api.energymk2.IEnergyProviderMK2;
import com.hbm.api.fluidmk2.IFluidConnectorBlockMK2;
import com.hbm.api.fluidmk2.IFluidStandardReceiverMK2;
import com.hbm.inventory.FluidContainerRegistry;
import com.hbm.inventory.NTMItemHandler;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTankNTM;
import com.hbm.inventory.fluid.trait.FT_Combustible;
import com.hbm.items.machine.IItemFluidId;
import com.hbm.lib.ForgeDirection;
import com.hbm.lib.Library;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.tileentity.TilePort;
import com.hbm.tileentity.TilePortShapes;
import com.hbm.tileentity.machine.MachineFluidContainerIO;
import dev.golem.core.data.DataIn;
import dev.golem.core.data.DataOut;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.network.*;
import net.minecraftforge.registries.*;

/** Supplied X5806_NTNH radial engine, using Continuum's real HE/fluid graphs and containers.
 * The obsolete automation indices (0/1/2 instead of 9/10/13) are deliberately corrected.
 * Fuel burn, including burning against a full buffer and the final partial tick, follows the original.
 */
final class LegacyRadialEngine {
    static final ForgeConfigSpec CONFIG;
    static final ForgeConfigSpec.IntValue FUEL_CAP;
    static final ForgeConfigSpec.LongValue POWER_CAP;
    static final ForgeConfigSpec.DoubleValue EXPONENT,LOW,MEDIUM,HIGH,AERO,GAS;
    static final ForgeConfigSpec.BooleanValue QUIET;
    static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"ntnhcore");
    static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,"ntnhcore");
    static RegistryObject<Block> BLOCK;
    static RegistryObject<Item> ITEM;
    static RegistryObject<BlockEntityType<Engine>> TYPE;
    static RegistryObject<MenuType<Menu>> MENU;
    static {
        var b=new ForgeConfigSpec.Builder();b.comment("From the supplied NTNH hbmConfig/hbmMachines.json: radialengine.",
            "Native HBM combustion energy is used. Restart required after changing these values.").push("radialengine");
        POWER_CAP=b.defineInRange("powerCap",250000L,1L,200000000L);
        FUEL_CAP=b.defineInRange("fuelCap",16000,1,100000000);
        EXPONENT=b.defineInRange("pistonGenExponent",1D,0D,4D);
        LOW=b.defineInRange("efficiencyLow",.75D,0D,10D);MEDIUM=b.defineInRange("efficiencyMedium",.5D,0D,10D);
        HIGH=b.defineInRange("efficiencyHigh",.25D,0D,10D);AERO=b.defineInRange("efficiencyAero",0D,0D,10D);
        GAS=b.defineInRange("efficiencyGas",0D,0D,10D);QUIET=b.define("shutUp",false);b.pop();CONFIG=b.build();
    }
    static void register(DeferredRegister<Block> blocks,DeferredRegister<Item> items,IEventBus bus){
        BLOCK=blocks.register("machine_selenium",EngineBlock::new);
        ITEM=items.register("machine_selenium",()->new BlockItem(BLOCK.get(),new Item.Properties()));
        TYPE=TILES.register("machine_selenium",()->BlockEntityType.Builder.of(Engine::new,BLOCK.get()).build(null));
        MENU=MENUS.register("machine_selenium",()->net.minecraftforge.common.extensions.IForgeMenuType.create(Menu::new));
        TILES.register(bus);MENUS.register(bus);
    }
    static boolean isPiston(ItemStack s){return BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("hbm:piston_selenium");}
    static long energyPerMb(FluidType type){
        var fuel=type.getTrait(FT_Combustible.class);if(fuel==null)return 0;
        double multiplier=switch(fuel.getGrade().name()){
            case "LOW"->LOW.get();case "MEDIUM"->MEDIUM.get();case "HIGH"->HIGH.get();case "AERO"->AERO.get();case "GAS"->GAS.get();default->0D;};
        return (long)((fuel.getCombustionEnergy()/1000L)*multiplier);
    }
    static final class EngineBlock extends HorizontalDirectionalBlock implements EntityBlock,IEnergyConnectorBlock,IFluidConnectorBlockMK2 {
        EngineBlock(){super(Properties.of().strength(5F,10F).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
        @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
        @Override public BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.ENTITYBLOCK_ANIMATED;}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Engine(p,s);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide||t!=TYPE.get()?null:(ll,p,ss,tile)->((Engine)tile).tick();}
        @Override public boolean canConnect(BlockGetter l,BlockPos p,ForgeDirection side){return side==ForgeDirection.DOWN;}
        @Override public boolean canConnect(FluidType f,BlockGetter l,int x,int y,int z,ForgeDirection side){return side!=ForgeDirection.UNKNOWN&&l.getBlockEntity(new BlockPos(x,y,z)) instanceof Engine e&&e.tank.accepts(f);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
            if(player.isShiftKeyDown())return InteractionResult.PASS;
            if(player instanceof ServerPlayer sp&&l.getBlockEntity(p) instanceof Engine e)NetworkHooks.openScreen(sp,e,p);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
            if(!s.is(next.getBlock())&&l.getBlockEntity(p) instanceof Engine e){Containers.dropContents(l,p,e);l.updateNeighbourForOutputSignal(p,this);}
            super.onRemove(s,l,p,next,moving);
        }
    }
    static final class Engine extends TileEntityLoadedBase implements WorldlyContainer,MenuProvider,IEnergyProviderMK2,IFluidStandardReceiverMK2 {
        long power;
        int pistonCount,soundCycle;
        final FluidTankNTM tank=new FluidTankNTM(Fluids.DIESEL,FUEL_CAP.get());
        final NTMItemHandler inventory=new NTMItemHandler(14){
            @Override public int getSlotLimit(int slot){return slot<9||slot==13?1:64;}
            @Override public boolean isItemValid(int slot,ItemStack stack){return Engine.this.canPlaceItem(slot,stack);}
            @Override protected void onContentsChanged(int slot){setChanged();pistonCount=countPistons();}
        };
        private LazyOptional<IItemHandlerModifiable>[] itemCaps=SidedInvWrapper.create(this,Direction.values());
        Engine(BlockPos pos,BlockState state){super(TYPE.get(),pos,state);tank.bindOwner(this);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(1);}
        int countPistons(){int n=0;for(int i=0;i<9;i++)if(isPiston(inventory.getStackInSlot(i)))n++;return n;}
        void tick(){
            if(level==null||level.isClientSide)return;
            setupPowerPorts(new TilePort.PortDef[]{TilePort.PortDef.make(worldPosition,ForgeDirection.DOWN)});
            setupFluidPorts(TilePortShapes.around(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ()));
            updatePortPOFIFO();
            tank.setType(inventory,11,12);MachineFluidContainerIO.load(tank,inventory,9,10);
            pistonCount=countPistons();power=Math.min(power,getMaxPower());
            power=Library.chargeItemsFromTE(inventory,13,power,getMaxPower());
            long he=energyPerMb(tank.getFluid());
            if(pistonCount>=3&&tank.getFill()>0&&he>0){
                tank.setFill(Math.max(0,tank.getFill()-pistonCount));
                power=Math.min(getMaxPower(),power+(long)(he*Math.pow(pistonCount,EXPONENT.get())));
                if(!QUIET.get()&&soundCycle++%3==0)level.playSound(null,worldPosition,SoundEvents.FIREWORK_ROCKET_BLAST,SoundSource.BLOCKS,getVolume(1F),.5F);
            }
            setChanged();networkPackNT();
        }
        @Override public long getPower(){return power;}
        @Override public long getMaxPower(){return POWER_CAP.get()*(tank.getFluid()==Fluids.NITAN?10:1);}
        @Override public void setPower(long v){power=Math.max(0,Math.min(getMaxPower(),v));setChanged();}
        @Override public boolean canConnect(ForgeDirection side){return side==ForgeDirection.DOWN;}
        @Override public boolean canConnect(FluidType type,ForgeDirection side){return side!=ForgeDirection.UNKNOWN&&tank.accepts(type);}
        @Override public FluidTankNTM[] getAllTanks(){return new FluidTankNTM[]{tank};}
        @Override public FluidTankNTM[] getReceivingTanks(){return getAllTanks();}
        @Override public Component getDisplayName(){return Component.translatable("block.ntnhcore.machine_selenium");}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new Menu(id,inv,this);}
        @Override protected void saveData(DataOut out){super.saveData(out);inventory.save(out);out.putLong("powerTime",power);tank.writeToNBT(out,"fuel");}
        @Override protected void loadData(DataIn in){super.loadData(in);inventory.load(in);tank.readFromNBT(in,"fuel");tank.changeTankSize(FUEL_CAP.get());power=Math.max(0,Math.min(getMaxPower(),in.getLongOr("powerTime",0)));pistonCount=countPistons();}
        @Override public void serialize(FriendlyByteBuf b){super.serialize(b);b.writeLong(power);b.writeInt(pistonCount);tank.serialize(b);}
        @Override public void deserialize(FriendlyByteBuf b){super.deserialize(b);power=b.readLong();pistonCount=b.readInt();tank.deserialize(b);}
        @Override public int getContainerSize(){return 14;}
        @Override public boolean isEmpty(){return inventory.getStacks().stream().allMatch(ItemStack::isEmpty);}
        @Override public ItemStack getItem(int slot){return inventory.getStackInSlot(slot);}
        @Override public ItemStack removeItem(int slot,int count){return inventory.extractItem(slot,count,false);}
        @Override public ItemStack removeItemNoUpdate(int slot){var s=getItem(slot);inventory.setStackInSlot(slot,ItemStack.EMPTY);return s;}
        @Override public void setItem(int slot,ItemStack s){inventory.setStackInSlot(slot,s);setChanged();}
        @Override public boolean stillValid(Player p){return level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
        @Override public void clearContent(){for(int i=0;i<14;i++)inventory.setStackInSlot(i,ItemStack.EMPTY);}
        @Override public boolean canPlaceItem(int slot,ItemStack s){
            if(slot<9)return isPiston(s);
            return switch(slot){case 9->FluidContainerRegistry.INSTANCE.contentOf(s,tank.getFluid())>0||MachineFluidContainerIO.acceptsInfiniteSource(tank,s);
                case 11->s.getItem() instanceof IItemFluidId;case 13->s.getItem() instanceof IBatteryItem;default->false;};
        }
        @Override public int[] getSlotsForFace(Direction d){return d==Direction.UP?new int[]{9}:d==Direction.DOWN?new int[]{10,12,13}:new int[]{9,13};}
        @Override public boolean canPlaceItemThroughFace(int slot,ItemStack s,Direction d){return slot==9||slot==13?canPlaceItem(slot,s):false;}
        @Override public boolean canTakeItemThroughFace(int slot,ItemStack s,Direction d){return slot==10||slot==12||slot==13&&s.getItem() instanceof IBatteryItem b&&b.getCharge(s)==b.getMaxCharge(s);}
        @Override public <T> LazyOptional<T> getCapability(Capability<T> c,Direction side){if(c==ForgeCapabilities.ITEM_HANDLER&&side!=null)return itemCaps[side.ordinal()].cast();return super.getCapability(c,side);}
        @Override public void invalidateCaps(){super.invalidateCaps();for(var cap:itemCaps)cap.invalidate();}
        @Override public void reviveCaps(){super.reviveCaps();itemCaps=SidedInvWrapper.create(this,Direction.values());}
    }
    static final class Menu extends AbstractContainerMenu {
        final Container machine;
        final ContainerData data;
        Menu(int id,Inventory inv,FriendlyByteBuf b){this(id,inv,new SimpleContainer(14),new SimpleContainerData(10));b.readBlockPos();}
        Menu(int id,Inventory inv,Engine e){this(id,inv,e,new ContainerData(){
            @Override public int get(int i){return switch(i){case 0->(int)e.power&65535;case 1->(int)(e.power>>>16);case 2->(int)e.getMaxPower()&65535;case 3->(int)(e.getMaxPower()>>>16);case 4->e.tank.getFill()&65535;case 5->e.tank.getFill()>>>16;case 6->e.pistonCount;case 7->Fluids.networkId(e.tank.getFluid());case 8->e.tank.getMaxFill()&65535;case 9->e.tank.getMaxFill()>>>16;default->0;};}
            @Override public void set(int i,int v){}@Override public int getCount(){return 10;}
        });}
        Menu(int id,Inventory inv,Container machine,ContainerData data){
            super(MENU.get(),id);this.machine=machine;this.data=data;addDataSlots(data);
            for(int i=0;i<9;i++)addSlot(new Slot(machine,i,8+(i%3)*18,18+(i/3)*18){@Override public boolean mayPlace(ItemStack s){return isPiston(s);}@Override public int getMaxStackSize(){return 1;}});
            addSlot(new Slot(machine,9,80,18){@Override public boolean mayPlace(ItemStack s){return machine instanceof Engine e?e.canPlaceItem(9,s):true;}});
            addSlot(output(machine,10,80,54));
            addSlot(new Slot(machine,11,152,18){@Override public boolean mayPlace(ItemStack s){return s.getItem() instanceof IItemFluidId;}});
            addSlot(output(machine,12,152,54));addSlot(new Slot(machine,13,116,90){@Override public boolean mayPlace(ItemStack s){return s.getItem() instanceof IBatteryItem;}});
            for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inv,x+y*9+9,8+x*18,140+y*18));
            for(int x=0;x<9;x++)addSlot(new Slot(inv,x,8+x*18,198));
        }
        private static Slot output(Container c,int i,int x,int y){return new Slot(c,i,x,y){@Override public boolean mayPlace(ItemStack s){return false;}};}
        long power(){return (data.get(0)&65535L)|((data.get(1)&65535L)<<16);}
        long capacity(){return (data.get(2)&65535L)|((data.get(3)&65535L)<<16);}
        int fuel(){return (data.get(4)&65535)|((data.get(5)&65535)<<16);}
        int fuelCapacity(){return (data.get(8)&65535)|((data.get(9)&65535)<<16);}
        @Override public boolean stillValid(Player p){return machine.stillValid(p);}
        @Override public ItemStack quickMoveStack(Player p,int i){
            var slot=slots.get(i);if(!slot.hasItem())return ItemStack.EMPTY;var live=slot.getItem();var copy=live.copy();
            if(i<14){if(!moveItemStackTo(live,14,slots.size(),true))return ItemStack.EMPTY;}
            else {boolean moved=false;if(isPiston(live)){
                    int before;do{before=live.getCount();moved|=moveItemStackTo(live,0,9,false);}while(!live.isEmpty()&&live.getCount()<before);
                }
                else if(live.getItem() instanceof IBatteryItem)moved=moveItemStackTo(live,13,14,false);
                else if(live.getItem() instanceof IItemFluidId)moved=moveItemStackTo(live,11,12,false);
                else moved=moveItemStackTo(live,9,10,false);
                if(!moved)return ItemStack.EMPTY;}
            if(live.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,live);return copy;
        }
    }
}
