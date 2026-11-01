# Create: Trains Scheduling

[![License: MPL-2.0](https://img.shields.io/badge/License-MPL--2.0-brightgreen.svg)](LICENSE)

一个 NeoForge 1.21.1 的 [Create](https://github.com/Creators-of-Create/Create) 附属模组，提供列车调度工具，
并集成到 [Xaero 的世界地图](https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map) 与
[JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap) 中。

当前版本：**0.0.6-alpha-1.21.1Neo-C6**（Minecraft 1.21.1 / NeoForge / Create 6）。

[English](README.md)

## 功能

- **地图叠加层**（Xaero 的世界地图与 JourneyMap）—— 车站标记，以及按车厢高亮列车；
  叠加层跟随 Create 自带的列车地图叠加开关
- **在地图上点击列车**打开调度菜单：
  - **设置目的地** —— 在地图上选择车站；目的地按车站 UUID 下发，
    因此重名车站绝不会把列车送到错误的车站
  - **规划进路** —— 依次点击多个车站并确认；安装 Steam 'n' Rails 时中间站为*通过不停车*
    （未安装时回退为正常停靠）
  - **允许越过前方红灯** —— 事件驱动：列车真正通过信号机后授权立即失效
  - **显示状态** —— 服务端权威状态（能否自动运行 / 是否有时刻表 / 是否暂停 /
    越红灯状态）以及 CRN 交路数据（线路 / 终点 / 种类）
  - **停用时刻表** —— 手动放置的时刻表只做*中断*（暂停并保留）；
    本模组创建的自动时刻表会被清除
  - **恢复时刻表** —— 继续被中断的时刻表
- **时刻表编辑器** —— 通过剪贴板导入/导出时刻表与编辑器目录（`/cts getcrn`、
  `/cts getsch`、`/cts writesch`），并附带单文件浏览器编辑器
- **权限系统** —— 普通玩家默认无调度权限；OP 可按功能授予权限
- **命令** —— `/cts trains`、`/cts stations`、`/cts route`、`/cts override`、`/cts clear`、`/cts perm`、`/cts selftest`
- **ComputerCraft 桥接**（可选）—— 在 Create 车站方块上提供只读的 `cts_station` 外设，
  把调度状态和 CRN 交路数据暴露给 Lua（车站/时刻表数据来自 Create 自带的 `Create_Station` 外设）
- **语言** —— 英文与简体中文

## 环境要求

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219+ |
| Create | 6.0.10 |
| Create Railways Navigator | 0.10.0-C6+（**硬依赖**） |
| DragonLib | 3.0.28+（CRN 的依赖） |
| Xaero 的世界地图 **或** JourneyMap | 1.21.1，仅客户端，可选（地图 UI 需要） |
| Steam 'n' Rails | 可选，通过不停车的途经点需要 |
| ComputerCraft / CC:Tweaked | 可选，启用 `cts_station` 外设 |
| Java | 21 |

## 安装

**客户端**（地图 UI 需要）：Create、Create Railways Navigator、DragonLib、NeoForge、本模组，
以及 Xaero 的世界地图或 JourneyMap 二选一。

**服务端**：Create、Create Railways Navigator、DragonLib、NeoForge、本模组。
地图模组属于客户端模组，服务端无需安装。

## 使用

### 地图操作流程

1. 打开全屏地图（Xaero 的世界地图或 JourneyMap）。
2. 左键点击列车（先悬停 —— 所有车厢会黄色高亮）。
3. 使用菜单：
   - **设置目的地…** → 所有车站显示黄色边框；点击一个车站把列车派往该站
   - **允许越过前方红灯** → 授予一次性越红灯许可
   - **显示状态** → 展开服务端权威的列车状态
   - **停用 / 恢复时刻表** → 暂停并保留手动时刻表；清除自动时刻表

按 `Esc` 或右键关闭菜单 / 取消选站。

### 命令

| 命令 | 说明 |
| --- | --- |
| `/cts trains` | 列出列车及调度状态 |
| `/cts stations` | 列出车站名 |
| `/cts route <train> <station1,station2,...>` | 下发一次性进路 |
| `/cts override <train> [ticks]` | 允许越过红灯（不带 ticks = 下一个信号机；带 ticks = 限时） |
| `/cts clear <train>` | 停用列车时刻表（手动时刻表会保留） |
| `/cts perm grant <player> <dispatch\|override\|schedule>` | 仅 OP：授予权限 |
| `/cts perm revoke <player> <permission>` | 仅 OP：收回权限 |
| `/cts perm list [player]` | 仅 OP：列出已授予的权限 |
| `/cts getcrn` | 把时刻表编辑器目录（CRN 线路 / 种类 / 车站）复制到剪贴板 |
| `/cts getsch` | 把手持时刻表物品的 JSON 复制到剪贴板 |
| `/cts writesch` | 从剪贴板读取时刻表 JSON 并写入手持物品（需要 `schedule` 权限） |
| `/cts selftest` | 开发自检 |

### 时刻表编辑器（剪贴板工作流）

外部时刻表编辑器通过剪贴板工作，不新增物品或 GUI：

1. `/cts getcrn` —— 把 CRN 线路 / 种类与全部车站名的 JSON 目录复制到剪贴板
   （作为 HTML 编辑器的下拉数据源）
2. `/cts getsch` —— 把手持时刻表物品复制为 JSON 到剪贴板
3. 用附带的 [`schedule-editor.html`](schedule-editor.html)（或手动）编辑 JSON，再放回剪贴板
4. `/cts writesch` —— 读取剪贴板并写入手持时刻表物品

客户端与服务端都需要 0.0.6 或更新版本，剪贴板通道才存在；版本混用的说明见
[COMPATIBILITY.md](COMPATIBILITY.md)。

**安全：** 剪贴板内容始终按不可信 JSON 数据处理 —— 绝不会被当作命令执行。写入前，服务端会强制校验
`schedule` 权限、每玩家冷却、15 秒一次性 token（只有显式执行 `/cts writesch` 后才能回传剪贴板内容）、
64 KB 大小上限、条目/条件数量上限、NBT 深度/节点上限、只接受已注册的指令/条件 id，
以及字符串清洗（禁止 `§` 与控制字符）。

### 权限

普通玩家默认**没有任何**调度权限。OP 始终拥有全部权限。OP 可按玩家授予以下权限
（保存在世界存档中，重启后仍然有效）：

- `dispatch` —— 设置目的地 / 下发进路
- `override` —— 允许越过红灯
- `schedule` —— 停用 / 恢复 / 清除时刻表

旧的 `forceReleasePlayers` 配置项仍然等同于 `override`。

### ComputerCraft 桥接

安装 CC:Tweaked 后，每个 Create 车站方块会暴露一个只读外设，类型为 `cts_station`
（不新增方块或物品 —— 它挂接到 Create 自己的车站方块实体上）。

这个外设只补充 **Create 没有暴露的调度专属状态**。车站名、列车是否在场、时刻表以及
`train_imminent` / `train_arrival` / `train_departure` 事件都由 Create 本身提供 ——
这些请使用 Create 的 `Create_Station` 外设。

```lua
local station = peripheral.find("cts_station")

station.getTrains()                 -- 本站轨道网络上的列车（调度状态）
station.getTrains(true)             -- 全部轨道网络
station.getTrain("车名或UUID")       -- 单车：暂停 / 完成 / 自动时刻表 / 越红灯 / 速度
station.getCircuit("车名或UUID")     -- CRN 交路：线路 / 终点 / 种类
station.getVersion()
```

该外设刻意设计为**只读**：调度仍然必须通过地图 UI / 命令，并经过权限检查。

## 工具

- [`schedule-editor.html`](schedule-editor.html) —— 独立的单文件时刻表 JSON 浏览器编辑器（中英双语）；
  配合 `/cts getcrn`、`/cts getsch`、`/cts writesch` 使用。不访问服务器，不会执行任何粘贴内容。
- [`COMPATIBILITY.md`](COMPATIBILITY.md) —— 客户端 × 服务端版本兼容性矩阵。

## 构建

```bash
./gradlew build
```

jar 输出到 `build/libs/create_trains_scheduling-0.0.6-alpha-1.21.1Neo-C6.jar`。

开发自检（`./gradlew runData`）会验证 JSON ↔ Schedule 往返以及所有 mixin 是否应用。

## 已知限制（alpha）

- 当某列车轨道网络上存在重名车站时，单目的地会回退为精确导航（Create/CRN 的时刻表显示不会展示该趟行程），
  而多站进路会拒绝下发，直到受影响的车站改名。
- 通过不停车（途经点）需要 Steam 'n' Rails；没有它时中间站为正常停靠。
- Alpha 软件：请备份存档。

## 许可证

本项目使用 **Mozilla Public License 2.0** 许可证 —— 见 [LICENSE](LICENSE)。

第三方组件及其许可证见 [NOTICE](NOTICE)。
