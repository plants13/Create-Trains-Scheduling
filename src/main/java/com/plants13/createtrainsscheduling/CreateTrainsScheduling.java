// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Main entry point of Create: Trains Scheduling (1.20.1 Forge / Create 0.5.1 port).
 *
 * <p>P1 skeleton: only config + startup logging. Dispatch features are ported in later phases
 * (see docs/PORTING.md).
 */
@Mod(CreateTrainsScheduling.MODID)
public class CreateTrainsScheduling {
    public static final String MODID = "create_trains_scheduling";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateTrainsScheduling() {
        var modBus = FMLJavaModLoadingContext.get()
                .getModEventBus();
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get()
                .registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("{} loaded (1.20.1 Forge, Create 0.5.1 line) - dispatch port skeleton", MODID);
    }
}
