# 移植计划：1.20.1（Forge 为主）与旧版 CRN / SnR

状态：**计划稿**（2026-11-01）。本文记录已验证的版本矩阵、代码改动点、分支策略与分阶段任务。

## 1. 目标与范围

| 目标 | 加载器 | Create | CRN | SnR | 优先级 |
| --- | --- | --- | --- | --- | --- |
| 现状 | **NeoForge 21.1** | 6.0.10 | 0.10.0-C6 | 1.21.1 版 0.3.0-beta.2 | 维护中 |
| 目标 A | **Forge 47.x（1.20.1）** | 6.0.8 | 1.20.1-beta-0.10.0-C6 | 1.7.3+forge-mc1.20.1 | 高 |
| 目标 B（legacy） | Forge 47.x（1.20.1） | 0.5.1.j | 0.5.3-beta-1.20.1-forge（旧 API） | 旧版 0.5.x | 低，可选 |

**1.20.1 以 Forge 为主加载器**。理论上 NeoForge 47 兼容，但不保证、不专门适配。

## 2. 版本矩阵（已用真实 jar 核实）

### 目标 A：1.20.1 + Create 6 + CRN 0.10-C6

| 组件 | 版本 | 核验结果 |
| --- | --- | --- |
| Minecraft | 1.20.1 | — |
| Forge | 47.1.3+（建议用 47.x 最新） | Create 6.0.8 要求 `[47.1.3,)` |
| Create | 1.20.1-6.0.8 | 依赖 Forge `[47.1.3,)`、Flywheel `[1.0.0,2.0)`、Ponder `[0.8,)`、JEI 15.19+ |
| CRN | 1.20.1-beta-0.10.0-C6 | 依赖 Forge 47+、Create `[6.0.8,)`、**DragonLib `[1.20.1-beta-3.0.28,)`** |
| DragonLib | 1.20.1-beta-3.0.28+ | 与 1.21.1 同版本号线，坐标待确认（Forge 变体） |
| SnR | 1.7.3+forge-mc1.20.1 | 依赖 Forge 47+、Create `[6.0.7,)`；**带 `WaypointDestinationInstruction`** |
| Xaero's World Map | 1.36.0_Forge_1.20（客户端） | `xaero.map.gui.GuiMap` 存在；字段名待确认 |
| JourneyMap | 1.20.1-5.9.18（客户端） | **没有 `journeymap.api.v2`**，需按旧 API v1 单独实现 |
| Java | 17 | 1.20.1 运行时 |

### 目标 B：1.20.1 + Create 0.5.1（legacy，分支 `1.20.1Forge-C0.5`）

已用实际测试实例（`D:\.minecraft\versions\新岛铁道 v1.5.1`）的 jar 核实：

| 组件 | 版本 | 核验结果 |
| --- | --- | --- |
| Forge | 1.20.1-47.4.6 | 实例 `fmlloader` 版本 |
| Create | 1.20.1-0.5.1.j | 实例 mods |
| CRN | 1.20.1-beta-0.8.4 | **旧 API 极小**：只有 `IPredictableWaitCondition` 与 `api.client.Screens`，**没有 `RailwayBackendApi`** |
| SnR | 1.6.7+forge-mc1.20.1 | 含 `WaypointDestinationInstruction` → 途经不停车可用 |
| Xaero / JourneyMap | 未安装 | 该整合包没有全屏地图 mod；地图 UI 需另行安装才能测 |
| CC:Tweaked | 未安装 | CC 桥在该包不可测，除非临时加装 |

结论：

- **Create 0.5 legacy 线上拿不到 CRN 交路数据**（0.8.4 没有公开列车/旅程 API）。可选：
  1. 1.20.1 + Create 0.5 版本**砍掉 CRN 交路显示**；
  2. 反射 CRN 内部类，升级即碎，不建议；
  3. 若坚持要交路数据，走目标 A（1.20.1 + Create 6 + CRN 0.10-C6），而不是 Create 0.5。
- 其余功能（单站/多站进路、越红灯、权限、手动时刻表打断、SnR 途经不停车）与 Create 6 版本逻辑一致，差异集中在 Create 0.5 的调度/信号类。

## 3. 关键结论

1. **A 的目标栈是真实存在的**：1.20.1 上 Create 6.0.8 + CRN 0.10.0-C6 官方同时支持，CRN 仍是 API v2（jar 内 `de/mrjulsen/crn/api/core/RailwayBackendApi.class` 存在）。
   - 意味着 `compat/crn/CrnBridge` 的调用**大概率原样可用**（只需换依赖坐标）。
2. **SnR 途经不停车在 1.20.1 可用**：SnR 1.7.3 含 `WaypointDestinationInstruction`，我们按指令 id `railways:waypoint_destination` 构造的逻辑不变。
3. **JourneyMap 需要两套实现**：1.20.1 的 JM 5.9.18 是旧 API（`journeymap.client.api.*`，v1），没有 v2 包。
   - 方案：`client/journeymap/` 下分 `v1` / `v2` 两个实现，编译期按分支选择。
4. **Create 6.0.8 与 6.0.10 的内部方法名基本同源**（同 6 代），信号/调度 mixin 目标预期一致，但仍需在 1.20.1 jar 上逐个验证（尤其 `Navigation#tick` 内部结构与 lambda 名）。
5. **构建系统必须换**：1.20.1 Forge 用 ForgeGradle 6（或 NeoGradle），不是 MDG 2；Java 17；Mixin 需要 refmap（MC 方法需要重映射，mod 方法名不变）。

## 4. 平台差异与改动清单

| 层 | 1.21.1 NeoForge（现状） | 1.20.1 Forge（目标） |
| --- | --- | --- |
| Mod 元数据 | `META-INF/neoforge.mods.toml` | `META-INF/mods.toml`（Forge 格式，dependencies 用 `mandatory=true`） |
| 构建插件 | ModDevGradle 2.x | ForgeGradle 6.x + MixinGradle 或 Forge 内置 mixin refmap |
| Java | 21 | 17 |
| 网络 | `RegisterPayloadHandlersEvent` + `CustomPacketPayload` + `StreamCodec` + `PacketDistributor` | `SimpleChannel` / `NetworkRegistry.newSimpleChannel` + `IMessage` + `channel.sendToServer/send` |
| 通道是否存在 | `NetworkRegistry.hasChannel` | `SimpleChannel#isRemotePresent(connection)` |
| optional 通道 | `registrar.optional()` | `NetworkRegistry.newSimpleChannel(..., ACCEPTVANILLA/ABSENT 版本谓词, ...)` + `isRemotePresent` |
| 事件包名 | `net.neoforged.neoforge.event.*` | `net.minecraftforge.event.*` / `net.minecraftforge.fml.event.*` |
| Mod 注解 | `@Mod` + `@EventBusSubscriber`（neoforge 包） | `net.minecraftforge.fml.common.Mod` + `@Mod.EventBusSubscriber` |
| 注册事件 | `RegisterPayloadHandlersEvent` 在 mod bus | `FMLCommonSetupEvent` 里建 channel |
| 配置 | `ModConfigSpec`（NeoForge 包） | `ModConfigSpec`（Forge 包），事件 `ModConfigEvent` |
| SavedData | `SavedData.Factory<>(supplier, loader, DataFixTypes)`（3 参） | `SavedData.Factory<>(supplier, loader)`（2 参） |
| ResourceLocation | `ResourceLocation.fromNamespaceAndPath` | `new ResourceLocation(ns, path)` |
| 时刻表物品写入 | `AllDataComponents.TRAIN_SCHEDULE`（1.20.5+ 数据组件） | `stack.getOrCreateTag().put("Schedule", tag)`（1.20.1 无数据组件） |
| `PlayerLoggedInEvent` | `net.neoforged.neoforge.event.entity.player.PlayerEvent` | `net.minecraftforge.event.entity.player.PlayerEvent` |
| Mixin 插件 / 环境判断 | `LoadingModList`（NeoForge） | `LoadingModList`（Forge 同名）但包名不同 |
| Xaero GUI | mixin `@Mixin(targets="xaero.map.gui.GuiMap", remap=false)`，`@Shadow cameraX/cameraZ/scale` | 版本 1.36 结构相近，字段需实测 |
| JourneyMap | API v2（`journeymap.api.v2.*`） | API v1（`journeymap.client.api.*`） |
| CC 外设（未来） | `RegisterCapabilitiesEvent`（neoforge） | `RegisterCapabilitiesEvent`（forge） |
| 混合映射 | NeoForge 1.21 官方名运行时 | Forge 1.20.1 SRG 运行时，MC 成员需 refmap 重映射 |

## 5. 分支与目录策略

推荐 **分支隔离 + 共享代码复制**，先不引入 Stonecutter（多版本统一树），原因：目标 B 需要不同的 Create 大版本 API，统一树会被条件编译污染。

```
main                    # 1.21.1 NeoForge 0.0.3-alpha-*
└── 1.20.1-forge        # 目标 A：Forge 47 + Create 6.0.8 + CRN 0.10-C6
└── 1.20.1-forge-create5# 目标 B（可选）：Create 0.5.1 + CRN 0.5.x
```

共享部分（`DispatchService`、`SignalOverrideManager`、`PermissionSavedData`、`RouteBuilder`、`NbtJson`/`ScheduleJson`）在分支间保持一致；差异集中在：

- `build.gradle` / `gradle.properties`（加载器、映射、依赖坐标）
- `META-INF/mods.toml`
- `network/` 传输层（SimpleChannel 版）
- `mixin/` 中针对 MC 类的目标/refmap
- `client/journeymap/`（v1 版）
- 少量 API 差异（SavedData、ResourceLocation、DataComponent）

后续若要维护三条线，再评估 Stonecutter 统一。

## 6. 分阶段任务（目标 A）

### P0 调研与骨架（0.5~1 天）
- [ ] 确认 Forge 47.x 最新稳定版本；确认 DragonLib Forge 变体坐标
- [ ] 在 1.20.1 jar 上验证 Create 6.0.8 内部方法：`Navigation#tick`、`SignalEdgeGroup#isOccupiedUnless`、`SignalBoundary#isForcedRed`、`ScheduleInstruction.fromTag/write/setData`、`Schedule.INSTRUCTION_TYPES`、`GlobalStation#id`
- [ ] 确认 Xaero 1.36 `GuiMap` 的 `cameraX/cameraZ/scale` 字段名
- [ ] 确认 JourneyMap 5.9.18 的 v1 API 渲染事件与全屏状态读取方式
- 验收：调研结论写回本文档

### P1 构建骨架（1 天）
- [ ] 新建 `1.20.1-forge` 分支；ForgeGradle 6 + Java 17 + mixin refmap
- [ ] 依赖：Create 1.20.1-6.0.8（slim/开发产物）、CRN 0.10.0-C6、DragonLib、Flywheel、Ponder
- [ ] `mods.toml`；能启动专用服务器且无崩溃（不含业务代码）

### P2 服务端核心（1~2 天）
- [ ] 移植 `DispatchService` / `SignalOverrideManager` / `OneOffScheduleTracker` / `DirectDispatchTracker`
- [ ] 移植 `PermissionSavedData` / `PermissionManager`（SavedData 2 参构造）
- [ ] 移植 `RouteBuilder` / `NbtJson` / `ScheduleJson`（`setData` 签名差异）
- [ ] 移植四个信号/导航 mixin，逐个验证注入点
- [ ] `/cts` 命令
- 验收：命令可下发单站/多站进路；越红灯生效；权限持久化

### P3 网络层（1 天）
- [ ] `SimpleChannel` 实现：SetDestination / SetRoute / GrantOverride / SetScheduleEnabled / RequestStatus / TrainStatus / PlayerPermissions / CircuitInfo
- [ ] `isRemotePresent` 作为 optional 通道检查（客户端没装 mod 也能进服）
- 验收：无 mod 客户端可进服；功能客户端正常收发

### P4 客户端地图（1~2 天）
- [ ] Xaero：1.36 的 GuiMap mixin + 投影/点击
- [ ] JourneyMap：API v1 的全屏渲染事件 + 投影 + 输入
- [ ] 菜单/选站/进路规划 UI（共享逻辑基本可直接复用）
- 验收：两张地图都能点列车、选站、规划进路、拖动地图

### P5 CRN 交路 / SnR 途经（0.5~1 天）
- [ ] `CrnBridge` 直接复用（API v2 同源），验证返回字段
- [ ] 途经不停车：`railways:waypoint_destination` 运行时构造（与 1.21 同）
- 验收：状态面板显示线路/终点/种类；多站进路中途不停车

### P6 发布
- [ ] 版本命名：`0.0.4-alpha-1.20.1Forge-C6`
- [ ] CI 增加 1.20.1 分支构建
- [ ] 双语 changelog、README 分支说明

## 7. 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| Create 6.0.8（1.20.1）内部方法与 6.0.10 有差异 | mixin 静默失效 | `require=0` + 启动自检；P0 逐个验证 |
| Forge 47 SRG + refmap 配置错误 | mixin 不生效/编译失败 | 先用最小 mixin 验证 refmap |
| JourneyMap v1 API 与 v2 差异大 | 需要第二套实现 | 已确认为独立文件，隔离在 `journeymap/v1` |
| Xaero 1.36 字段名不同 | 投影错误 | P0 javap / 实测确认 |
| DragonLib Forge 坐标/仓库缺失 | 构建失败 | P0 先解析依赖 |
| 目标 B（Create 0.5）API 差异大 | 工作量翻倍 | 后置，独立分支，先不做 |

## 8. 待确认清单

- [ ] DragonLib 1.20.1 Forge 的 Maven 坐标与仓库
- [ ] Create 1.20.1-6.0.8 的开发产物坐标（`com.simibubi.create:create-1.20.1:6.0.8-xxx`）
- [ ] ForgeGradle 6 + official mappings 下 Create mod 类方法名是否保持（预期保持）
- [ ] Xaero 1.36 `GuiMap` 私有字段名
- [ ] JourneyMap 5.9.18 v1 API 的全屏渲染事件名
