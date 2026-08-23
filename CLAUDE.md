# GT-Perfect-Overclock — 项目备忘录

GTNH 2.8.4 专用小 mod：把全部格雷机器改为 EBF 式无损超频。作者 lelqwq，个人自用，独立于 gtswn 无线电网项目。

## 一句话原理

GTNH 2.8.4 的 GT5U（5.09.51.476）已把所有超频计算收敛到 `gregtech.api.util.OverclockCalculator`。
本 mod 用 Mixin 在该类的两个计算入口强制时长因子 `durationDecreasePerOC = 4.0`：
每级超频 时间÷4、峰值功率×4、单配方总耗能不变（EBF 式无损语义）。

## 核心设计决策（已与用户确认，勿再询问）

- 策略 **A1「一刀切」**：在 `calculate()` 入口无条件强制 4.0，覆盖所有机器（含 GT++ 等第三方调用方、含自定义超频的机器如 PCB 厂/纳米锻造炉）
- 无损语义 = EBF 式（时间÷4、峰值×4、总耗能不变），**不是**"峰值功率也不变"
- 无配方机器策略（v1.1.0）：WA 与 Miner 各自单独 Mixin 无损化（每台一个配置开关，默认开）；泵/钻塔因「无速度曲线只有档位税」**不做**；炽焱高炉等自带速度曲线的修复**暂缓**（方案已出过但用户退回再考虑）
- 仅适配 GTNH 2.8.4 / GT5U 5.09.51.476，其他版本不保证兼容
- 配置开关 `enablePerfectOverclock`（gtpoc.cfg，默认 true），Mixin 每次计算时读取；纯计算注入、无持久化，存档兼容，卸载还原
- 预期副作用（非 bug）：NEI 显示值同步变化；机器峰值功率每级 ×4，供电线路须扛得住

## 注入点

`src/main/java/com/lelqwq/gtpoc/mixins/MixinOverclockCalculator.java`：

- `OverclockCalculator.calculate()` — 常规配方检查路径（单块机 `MTEBasicMachine.calculateCustomOverclock`、ProcessingLogic 多方块、直接构造计算器的多方块）
- `OverclockCalculator.calculateMultiplierUnderOneTick()` — 子 tick 并行倍率路径（ParallelHelper、装配线 MTEAssemblyLine、集成矿场、多方块炉），必须同步强制以保持数学一致
- **坑（已踩）**：两个目标方法都有返回值（分别返回 OverclockCalculator / double），注入回调必须用 `CallbackInfoReturnable<...>`；误用 `CallbackInfo` 编译期查不出来，运行时 APPLY 阶段抛 InvalidInjectionException，导致目标类整体 transform 失败 → 游戏内 NoClassDefFoundError/崩溃
- **WA（非 Mixin，主类 preInit 改写速度表）**：`MTEWorldAccelerator.mAccelerateStatic`（public static final 数组，引用 final 但元素可写）在 GT5U preInit 后替换为 4^档表 `{1,4,16,64,256,512,...}`，耗电保持原版 —— 一分电一分速度（档位 0-4 严格无损，档位 5+ 速度封顶 512 无收益）；preInit 时快照原版表，开关关闭时还原
- **Miner（非 Mixin，同上方式）**：`MTEMiner.SPEED`（private static final）经反射取引用后改写元素为 `{160,160,40,10,5}`，耗电保持原版 —— 每方块耗能 T0-T3 恒 1280 EU、T4 为 2560（**SPEED&lt;5 会触发 DrillingLogicDelegate 的 `aTick % (speed/5)` 除零崩溃**，故顶档取 5t 非严格 ×4）；开关关闭时按快照还原
- 两者均非 Mixin：数组元素可变（仅引用 final），普通赋值即可，Java 25 无反射写 final 问题（Miner 仅反射读引用）
- 编译依赖新增 `compileOnly GTNHLib 0.6.37`（GT5U API 引用 gtnhlib 类，编译期必需，运行时 2.8.4 自带）
- 已知副作用（接受）：WA/Miner 的机器描述文本仍显示原版数字（构造期静态字符串），游戏行为已按新表执行

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

## 当前状态（2026-08-23）

- **v1.1.0 已实测通过**（WA 加速 ×4/档、采矿场挖速 ×4/档，耗电均保持原版；开关还原正常）
- **v1.0.0 已发布**：GitHub Release（`lelqwq/GT-Perfect-Overclock`），附主 jar / dev / sources 三个产物，实机测试通过（mod 正常生效，mixin 描述符崩溃已修复）
- 实机环境事实：用户 2.8.4 实例的 GT5U 实际为 **5.09.51.482**（`gregtech-5.09.51.482.jar`），比编译目标 476 新；注入目标方法在 482 中均存在，API 兼容已实测。是否把编译依赖对齐到 482 待定；客户端 UniMixins 0.1.23 / Java 25
- 远程仓库：`git@github.com:lelqwq/GT-Perfect-Overclock.git`（main 分支）
