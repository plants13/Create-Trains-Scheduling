// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.plants13.createtrainsscheduling.schedule.ScheduleSelfTest;
import com.simibubi.create.Create;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Main entry point of Create: Trains Scheduling.
 *
 * <p>This mod builds on top of Create's train system. The hooks we will mostly work with are
 * {@link Create#RAILWAYS} (the global railway manager) and the schedule/station APIs under
 * {@code com.simibubi.create.content.trains}.
 */
@Mod(CreateTrainsScheduling.MODID)
public class CreateTrainsScheduling {
    public static final String MODID = "create_trains_scheduling";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateTrainsScheduling(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::gatherData);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        runStartupSelfChecks();
    }

    /**
     * 开发期自检钩子（通过 JVM 系统属性开启，正常游戏不会执行）：
     *
     * <ul>
     *   <li>{@code -Dcts.selftest=true}：跑一次 JSON &lt;-&gt; Schedule 往返校验</li>
     *   <li>{@code -Dcts.verifyMixin=true}：强制加载 Navigation 类，验证信号 mixin 注入点是否匹配</li>
     * </ul>
     */
    private void runStartupSelfChecks() {
        if (Boolean.getBoolean("cts.verifyMixin")) {
            for (String className : new String[] {
                    "com.simibubi.create.content.trains.entity.Train",
                    "com.simibubi.create.content.trains.entity.Navigation",
                    "com.simibubi.create.content.trains.signal.SignalBoundary",
                    "com.simibubi.create.content.trains.signal.SignalEdgeGroup"}) {
                try {
                    Class<?> loaded = Class.forName(className);
                    LOGGER.info("[CTS] forced load of {} succeeded (mixin pipeline ran)", loaded.getName());
                } catch (Throwable t) {
                    LOGGER.error("[CTS] forced load of {} failed", className, t);
                }
            }
        }
        if (Boolean.getBoolean("cts.selftest")) {
            ScheduleSelfTest.run(null);
        }
    }

    private void gatherData(final GatherDataEvent event) {
        // 开发期自检：只在 runData 里执行，验证 JSON <-> Schedule 往返无损
        event.getLookupProvider().thenAccept(ScheduleSelfTest::run);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("{} loaded alongside Create {}", MODID, Create.ID);
        LOGGER.info("Railway manager: {}", Create.RAILWAYS);
    }
}
