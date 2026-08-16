# GT-Perfect-Overclock — 项目备忘录

GTNH 2.8.4 专用小 mod：把全部格雷机器改为 EBF 式无损超频。作者 lelqwq，个人自用，独立于 gtswn 无线电网项目。

## 一句话原理

GTNH 2.8.4 的 GT5U（5.09.51.476）已把所有超频计算收敛到 `gregtech.api.util.OverclockCalculator`。
本 mod 用 Mixin 在该类的两个计算入口强制时长因子 `durationDecreasePerOC = 4.0`：
每级超频 时间÷4、峰值功率×4、单配方总耗能不变（EBF 式无损语义）。

## 核心设计决策（已与用户确认，勿再询问）

- 策略 **A1「一刀切」**：在 `calculate()` 入口无条件强制 4.0，覆盖所有机器（含 GT++ 等第三方调用方、含自定义超频的机器如 PCB 厂/纳米锻造炉）
- 无损语义 = EBF 式（时间÷4、峰值×4、总耗能不变），**不是**"峰值功率也不变"
- 仅适配 GTNH 2.8.4 / GT5U 5.09.51.476，其他版本不保证兼容
- 配置开关 `enablePerfectOverclock`（gtpoc.cfg，默认 true），Mixin 每次计算时读取；纯计算注入、无持久化，存档兼容，卸载还原
- 预期副作用（非 bug）：NEI 显示值同步变化；机器峰值功率每级 ×4，供电线路须扛得住

## 注入点

`src/main/java/com/lelqwq/gtpoc/mixins/MixinOverclockCalculator.java`：

- `OverclockCalculator.calculate()` — 常规配方检查路径（单块机 `MTEBasicMachine.calculateCustomOverclock`、ProcessingLogic 多方块、直接构造计算器的多方块）
- `OverclockCalculator.calculateMultiplierUnderOneTick()` — 子 tick 并行倍率路径（ParallelHelper、装配线 MTEAssemblyLine、集成矿场、多方块炉），必须同步强制以保持数学一致
- **坑（已踩）**：两个目标方法都有返回值（分别返回 OverclockCalculator / double），注入回调必须用 `CallbackInfoReturnable<...>`；误用 `CallbackInfo` 编译期查不出来，运行时 APPLY 阶段抛 InvalidInjectionException，导致目标类整体 transform 失败 → 游戏内 NoClassDefFoundError/崩溃

## GT5U 5.09.51.476 超频架构速查（已源码核实）

- 超频核心 `OverclockCalculator.calculateOverclock()`：`consumption = recipePower × eutIncreasePerOC^OC`（默认 4）；`duration /= durationDecreasePerOC^OC`（默认 2 ← 有损根源）
- `enablePerfectOC()` = 官方完美超频 API（把 durationDecreasePerOC 设为 4）
- 单块机：`MTEBasicMachine.calculateCustomOverclock()`（~line 694）→ `EUOverclockDescriber` 只设 EUt、不碰时长因子
- ProcessingLogic（36 个多方块文件）默认 `overClockTimeReduction = 2.0`；LCR 显式 `enablePerfectOverclock()`；EBF 走热量超频（final 4.0/1800K），不受本 mod 影响
- 遗留 `mEUt *= 4` 式超频循环：0 处（已全部迁移）
- 源码参考：本机曾解压到 /tmp/gt5src（若不在，从 GT5-Unofficial 5.09.51.476 源码 jar 重新解压）

## 构建

- `./gradlew build` → 产物 `build/libs/gt-perfect-overclock-0.1.0.jar`（jar 名由 build.gradle.kts 的 `base.archivesName` 控制，modid 仍为 gtpoc）
- 改完代码先 `./gradlew spotlessApply`（import 顺序/行尾符/折行自动修复），否则 build 会被 spotlessJavaCheck 卡住
- 构建坑：`gradle.properties` 的 `mixinsPackage` 是**相对 modGroup 的路径**（= `mixins`），不是完整包名；写全路径会导致插件报 "Could not resolve mixinsPackage"
- 依赖仅 GT5U 5.09.51.476（dependencies.gradle，transitive=false）；UniMixins 由 GTNH 构建脚本因 `usesMixins = true` 自动引入，2.8.4 客户端自带

## 工作流程（用户习惯，务必遵守）

- 用户自己进游戏实测每个版本，**实测通过前不要提交**
- 提交信息：中文、Conventional Commits 格式（如 `feat(mixin): ...`）
- 改动前先出影响分析报告、经用户批准再动手（comprehensive-analysis 技能）
- 沟通用中文

## 当前状态（2026-08-16）

- v0.1.0 首次实机测试暴露 mixin 描述符 bug（`CallbackInfo` → `CallbackInfoReturnable<...>`），已修复并重建 jar（16:20），服务端/客户端崩溃同源
- 实测发现：用户 2.8.4 实例的 GT5U 实际为 **5.09.51.482**（`gregtech-5.09.51.482.jar`），比编译目标 476 新；注入目标方法在 482 中均存在（Mixin 方法解析成功后才报描述符错误）。是否把编译依赖对齐到 482 待定
- 仓库已 git init（main 分支），**无任何提交**
- 等待用户游戏内复测，验证点：单块机 NEI 时间÷4/总耗能不变、多方块（热解炉）、装配线不崩、EBF 行为不变、gtpoc.cfg 正常生成
