package com.ntnh.ntnhcore;

import com.hbm.api.energymk2.*;
import com.hbm.interfaces.ILookOverlay;
import com.hbm.lib.ForgeDirection;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.tileentity.TilePortShapes;
import dev.golem.core.data.*;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.List;

/** Supplied NTNH X5806 RF -> HE converter, with its original 2 RF -> 5 HE arithmetic. */
final class LegacyRfEnergyConverter {
    static final long HE_CAPACITY=5_000_000L;
    static final int FE_CAPACITY=1_000_000;
    static final ForgeConfigSpec CONFIG;
    static final ForgeConfigSpec.LongValue RF_USED,HE_CREATED;
    static final ForgeConfigSpec.DoubleValue INPUT_DECAY;
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"ntnhcore");
    static RegistryObject<Block> BLOCK;
    static RegistryObject<Item> ITEM;
    static RegistryObject<BlockEntityType<Converter>> TYPE;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.comment("Original NTNH hbmMachines.json / RFToHEConverter. RF and FE use the same units.",
            "Defaults: 2 RF/FE -> 5 HE; zero input decay. The native HBM FE bridge is independent.",
            "The original integer rounding is retained: one RF alone yields two HE.",
            "Changes require restarting the game/server.").push("RFToHEConverter");
        RF_USED=b.defineInRange("RF_Used2",2L,1L,(long)FE_CAPACITY);
        HE_CREATED=b.defineInRange("HE_Created2",5L,1L,HE_CAPACITY);
        INPUT_DECAY=b.defineInRange("inputDecay2",0D,0D,1D);
        b.pop();CONFIG=b.build();
    }
    static void register(DeferredRegister<Block> blocks,DeferredRegister<Item> items,IEventBus bus){
        BLOCK=blocks.register("machine_converter_rf_he",ConverterBlock::new);
        ITEM=items.register("machine_converter_rf_he",()->new BlockItem(BLOCK.get(),new Item.Properties()){
            @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
                lines.add(Component.translatable("ntnhcore.rf_converter.ratio",RF_USED.get(),HE_CREATED.get()));
                lines.add(Component.translatable("ntnhcore.rf_converter.buffers",FE_CAPACITY,HE_CAPACITY));
                lines.add(Component.translatable("ntnhcore.rf_converter.direction"));
            }
        });
        TYPE=TILES.register("machine_converter_rf_he",()->BlockEntityType.Builder.of(Converter::new,BLOCK.get()).build(null));
        TILES.register(bus);
    }
    private static final class ConverterBlock extends Block implements EntityBlock,IEnergyConnectorBlock,ILookOverlay {
        ConverterBlock(){super(Properties.of().strength(5F,10F).sound(SoundType.METAL).requiresCorrectToolForDrops());}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new Converter(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide||type!=TYPE.get()?null:(l,p,s,t)->((Converter)t).tick();}
        @Override public boolean canConnect(BlockGetter level,BlockPos pos,ForgeDirection side){return side!=ForgeDirection.UNKNOWN;}
        @Override public ILookOverlay.Data getLookOverlay(Level level,BlockPos pos,Player player){
            if(!(level.getBlockEntity(pos) instanceof Converter tile))return null;
            return new ILookOverlay.Data(getName().getString(),0xffff00,0x404000,List.of(
                "§a-> §r"+tile.energy.getEnergyStored()+" / "+FE_CAPACITY+" RF / FE",
                "§c<- §r"+tile.getPower()+" / "+HE_CAPACITY+" HE"));
        }
    }
    static final class Converter extends TileEntityLoadedBase implements IEnergyProviderMK2 {
        private long power;
        private int fe;
        final IEnergyStorage energy=new IEnergyStorage(){
            @Override public int receiveEnergy(int amount,boolean simulate){
                int take=Math.min(Math.max(0,amount),FE_CAPACITY-fe);
                if(!simulate&&take>0){fe+=take;setChanged();}return take;
            }
            @Override public int extractEnergy(int amount,boolean simulate){return 0;}
            @Override public int getEnergyStored(){return fe;}
            @Override public int getMaxEnergyStored(){return FE_CAPACITY;}
            @Override public boolean canExtract(){return false;}
            @Override public boolean canReceive(){return true;}
        };
        private LazyOptional<IEnergyStorage> energyCap=LazyOptional.of(()->energy);
        Converter(BlockPos pos,BlockState state){super(TYPE.get(),pos,state);}
        @Override public long getPower(){return power;}
        @Override public long getMaxPower(){return HE_CAPACITY;}
        @Override public void setPower(long value){long next=Math.max(0,Math.min(HE_CAPACITY,value));if(next!=power){power=next;setChanged();}}
        @Override public long transferPower(long amount,boolean simulate){return amount;} // Provider only; never accepts HE.
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,Direction side){return capability==ForgeCapabilities.ENERGY?energyCap.cast():super.getCapability(capability,side);}
        @Override public void invalidateCaps(){super.invalidateCaps();energyCap.invalidate();}
        @Override public void reviveCaps(){super.reviveCaps();energyCap=LazyOptional.of(()->energy);}
        void tick(){
            if(level==null||level.isClientSide)return;
            setupPowerPorts(TilePortShapes.around(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ()));
            long used=Math.min(fe,(HE_CAPACITY-power)*RF_USED.get()/HE_CREATED.get());
            fe-=(int)used;power+=used*HE_CREATED.get()/RF_USED.get();
            if(fe>0)fe-=(int)Math.ceil(fe*INPUT_DECAY.get());
            if(used>0||INPUT_DECAY.get()>0)setChanged();
            // The original tryProvide also fed a touching receiver with no cable. A restored
            // plain BlockEntity may not own a native port node, so preserve that direct contract.
            if(level instanceof net.minecraft.server.level.ServerLevel server)for(var side:Direction.values()){
                if(power==0)break;var next=worldPosition.relative(side);if(!level.hasChunkAt(next))continue;
                var endpoint=PowerNetwork.handlerAtFace(server,next,side.getOpposite());
                if(endpoint instanceof IEnergyHandlerMK2 receiver&&receiver.isLoaded()&&receiver.getReceiverSpeed()>0
                    &&receiver.canConnect(ForgeDirection.getOrientation(side.getOpposite()))){
                    long offered=Math.min(power,receiver.getReceiverSpeed());
                    long remainder=receiver.transferPower(offered,false);
                    setPower(power-(offered-Math.max(0,Math.min(offered,remainder))));
                }
            }
            updateAllPorts();providePower();networkPackNT();
        }
        @Override protected void saveData(DataOut out){super.saveData(out);out.putLong("power",power);out.putInt("Energy",fe);}
        @Override protected void loadData(DataIn in){super.loadData(in);power=Math.max(0,Math.min(HE_CAPACITY,in.getLongOr("power",0)));fe=Math.max(0,Math.min(FE_CAPACITY,in.getIntOr("Energy",0)));}
        @Override public void serialize(FriendlyByteBuf b){super.serialize(b);b.writeLong(power);b.writeInt(fe);}
        @Override public void deserialize(FriendlyByteBuf b){super.deserialize(b);power=b.readLong();fe=b.readInt();}
    }
}
