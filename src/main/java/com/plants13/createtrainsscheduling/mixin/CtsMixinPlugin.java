// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.neoforged.fml.loading.LoadingModList;

/**
 * 条件化 mixin：Xaero 相关注入只在装了 Xaero 世界地图时应用；临时诊断 mixin 只在显式开启调试时应用。
 *
 * <p>⚠️ 必须用 {@link LoadingModList} 而不是 {@code ModList}：mixin 在类加载早期执行，
 * 那时 {@code ModList} 尚未就绪（会返回 null），但 {@code LoadingModList} 已经可用。
 * Create 本体的 {@code CreateMixinPlugin} 用的就是这个（见其 {@code Mods} 枚举）。
 *
 * <p>这里的开关读的是 JVM 系统属性而不是配置对象：mixin 应用阶段配置还没加载完，
 * 读配置会得到 {@code IllegalStateException}。
 */
public class CtsMixinPlugin implements IMixinConfigPlugin {

    private static final String XAERO_PACKAGE = "com.plants13.createtrainsscheduling.mixin.xaero";
    private static final String XAERO_MOD_ID = "xaeroworldmap";

    private static final String DEBUG_SEPARATION_MIXIN =
            "com.plants13.createtrainsscheduling.mixin.TrainSeparationHoldDebugMixin";
    /** 加 {@code -Dcts.debugSeparation=true} 才启用扣车诊断日志 mixin（还要打开 debugLogging 配置）。 */
    private static final String DEBUG_SEPARATION_PROPERTY = "cts.debugSeparation";

    private static Boolean xaeroLoaded;

    private static boolean isXaeroLoaded() {
        if (xaeroLoaded == null) {
            LoadingModList modList = LoadingModList.get();
            xaeroLoaded = modList != null && modList.getModFileById(XAERO_MOD_ID) != null;
            CreateTrainsScheduling.LOGGER.info("[CTS] xaero mixin gate: {}={}", XAERO_MOD_ID, xaeroLoaded);
        }
        return xaeroLoaded;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(XAERO_PACKAGE)) {
            return isXaeroLoaded();
        }
        if (DEBUG_SEPARATION_MIXIN.equals(mixinClassName)) {
            return Boolean.getBoolean(DEBUG_SEPARATION_PROPERTY);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
