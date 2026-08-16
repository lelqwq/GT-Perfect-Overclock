<h1 align="center">GT-Perfect-Overclock</h1>
<p align="center"><strong><em>GTNH 全机器无损超频</em></strong></p>

一个**仅适配 GTNH 2.8.4** 的小型 mod：把 GTNH 中所有格雷机器（单块机与多方块）的超频改为 **EBF 式无损超频**——每级超频处理时间 ÷4、峰值功率 ×4、单配方总耗能不变。

> \[!NOTE]
> 个人自用 mod。本 mod 会大幅改变游戏节奏（机器速度与峰值功耗同时上升），请自行斟酌使用。

## 原理

GTNH 2.8.4 的 GT5U（5.09.51.476）已将全部超频计算收敛到 `gregtech.api.util.OverclockCalculator`：

- 有损超频默认：每级 时间÷2、功率×4 → 单配方总耗能翻倍
- 无损超频（EBF 式）：每级 时间÷4、功率×4 → 单配方总耗能不变

本 mod 通过 Mixin 在 `calculate()` 与 `calculateMultiplierUnderOneTick()` 两个计算入口强制时长因子为 4.0，覆盖：

- 全部单块机（`MTEBasicMachine` 系及其全部子类）
- ProcessingLogic 多方块（热解炉等）
- 直接构造计算器的多方块（PCB 厂、纳米锻造炉、等离子锻造炉、聚变等）
- 同样调用该计算器的第三方机器（GT++ 等）

## 特性

- **纯计算修改**：无持久化数据，存档完全兼容；卸载 mod 即恢复原版行为
- **配置开关**：`gtpoc.cfg` 中的 `enablePerfectOverclock`（默认 true）
- **NEI 一致**：NEI 配方显示与机器实际计算同源，自动显示无损超频后的数值
- **服务器/客户端**：服务端负责机器计算，客户端负责 NEI 显示，两侧均需安装

## 版本需求

| GTNH  | 版本 |
| ----- | ---- |
| 2.8.4 | 0.1.0+ |
| 其他  | 未适配，不保证兼容 |

## 构建

`./gradlew build`，产物位于 `build/libs/`。

## 许可证

详见 LICENSE 文件。
