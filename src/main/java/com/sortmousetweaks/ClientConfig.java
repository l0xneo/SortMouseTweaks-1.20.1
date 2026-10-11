package com.sortmousetweaks;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<Sorter.Mode> MODE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        MODE = builder.comment("How the sort buttons order items.").defineEnum("sortMode", Sorter.Mode.GROUPS);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
