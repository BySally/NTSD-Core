package com.ntnh.ntnhcore;

import net.minecraftforge.common.ForgeConfigSpec;

/** NTNH's original AE2 Stuff wireless power law; the transport is native ExtendedAE. */
public final class LegacyWireless {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.DoubleValue BASE, DISTANCE_MULTIPLIER, MAX_RANGE;
    static {
        var b = new ForgeConfigSpec.Builder();
        BASE = b.comment("Original AE2 Stuff PowerBase, in AE/t at each end of a link.")
            .defineInRange("powerBase", 1D, 0D, 1_000_000D);
        DISTANCE_MULTIPLIER = b.comment("Power = base + multiplier * distance * ln(distance^2 + 3). Energy cards do not discount legacy wireless links.")
            .defineInRange("powerDistanceMultiplier", .1D, 0D, 1_000_000D);
        MAX_RANGE = b.comment("0 = no fixed distance limit, as in NTNH 1.7.10. Both chunks must be loaded and in the same dimension. Overrides ExtendedAE's range setting.")
            .defineInRange("maxRange", 0D, 0D, 100_000_000D);
        CONFIG = b.build();
    }
    public static double power(double distance) {
        return BASE.get() + DISTANCE_MULTIPLIER.get() * distance * Math.log(distance * distance + 3D);
    }
    public static double range() {return MAX_RANGE.get() == 0D ? Double.POSITIVE_INFINITY : MAX_RANGE.get();}
    private LegacyWireless() {}
}
