# GT-Perfect-Overclock — 项目备忘录

GTNH 2.9.0 专用小 mod：把全部格雷机器改为 EBF 式无损超频。作者 lelqwq，个人自用，独立于 gtswn 无线电网项目。

## 一句话原理

本项目编译和适配目标为 GTNH 2.9.0 实机使用的 GT5U **5.09.54.265**（2.8.4 / 5.09.51.482 的适配保留在 v1.1.0 标签）。常规配方超频集中在 `gregtech.api.util.OverclockCalculator`，工业蜂箱等特殊机器另行适配。
本 mod 用 Mixin 在该类的两个计算入口把**小于 4 的时长因子提升为 4.0**：
每级超频 时间÷4、峰值功率×4、单配方总耗能不变（EBF 式无损语义）。

## 核心设计决策（已与用户确认，勿再询问）

- 策略（2026-11-06 修订，取代旧的 A1「一刀切」）：**只提升、不压低** —— `durationDecreasePerOC < 4.0` 时设为 4.0；`≥ 4.0` 保持原值。原因是 2.9.0 的工业电弧炉电极存在 6 / 8 的因子，无条件覆盖会把它们压到 4、反而比原版慢
- 无损语义 = EBF 式（时间÷4、峰值×4、总耗能不变），**不是**"峰值功率也不变"
- 无配方机器策略（v1.1.0）：WA 与 Miner 各自单独改写速度表（每台一个配置开关，默认开）；泵/钻塔因「无速度曲线只有档位税」**不做**；炽焱高炉等自带速度曲线的修复**暂缓**
- 仅适配 GTNH 2.9.0 / GT5U 5.09.54.265，其他版本不保证兼容
- 工业蜂箱（v1.2.0 起）：速度升级由 2^等级改为 4^等级，同等级 EU/t 保持原版；繁殖最低 1 tick；**第 5 级起追加产量补偿**（见下）；独立开关 `enableLosslessApiary` 默认开
- 配置开关 `enablePerfectOverclock`（gtpoc.cfg，默认 true），Mixin 每次计算时读取；纯计算注入、无持久化，存档兼容，卸载还原
- 预期副作用（非 bug）：NEI 显示值同步变化；机器峰值功率每级 ×4，供电线路须扛得住；蜂箱总耗能随等级按 (1/2)^n 下降（比严格无损更强，因为只改了时长因子、保留原版 2^n 耗电）

## 注入点

`MixinIndustrialApiary`（265 核对）：

- 生产：`@ModifyVariable(STORE ordinal=0, name="actualDivider")` 把局部变量 `actualDivider` 初值平方（2^n → 4^n），同时驱动耗时和产物倍率；耗电仍用原版 `usedDivider = 2^n`
- 产量补偿：`@ModifyVariable(STORE ordinal=1, name="actualDivider")` 在第二次写入（此时已是原版"产物倍率"）时，从第 5 级起保证倍率至少 `4^(等级-4)`，只抬不压
- 繁殖：`@Redirect` 第四次 `mMaxProgresstime` 写入（ordinal=3），改为 `max(1, 100/4^等级)`；回调里先校验收到的原值与 `100/min(100,2^等级)` 相符，不符则记录日志并放弃改写（防 GT5U 改方法体后静默打错位置）
- 显示：2.9.0 把 UI 迁到 `MTEIndustrialApiaryGui`（旧 `lambda$addUIWidgets$26/$19` 已消失），速度按钮与悬浮提示统一读 `getAcceleration()`（265 起是 public）→ 注入该方法返回 `4^等级`；同时 `@Redirect` `getAdditionalEnergyUsage()` 内对 `getAcceleration()` 的调用回 `2^等级`，否则耗电提示会虚高
- 全部注入 `require = 1`，升级 GT5U 后必须重新核对字节码

`src/main/java/com/lelqwq/gtpoc/mixins/MixinOverclockCalculator.java`：

- `OverclockCalculator.calculate()` — 常规配方检查路径（单块机 `MTEBasicMachine.calculateCustomOverclock`、ProcessingLogic 多方块、直接构造计算器的多方块）
- `OverclockCalculator.calculateMultiplierUnderOneTick()` — 子 tick 并行倍率路径（ParallelHelper、装配线 MTEAssemblyLine、集成矿场、多方块炉），必须同步处理以保持数学一致
- 用 `@Shadow protected double durationDecreasePerOC` 读原值，`< 4.0` 才覆盖
- **坑（已踩）**：两个目标方法都有返回值（分别返回 OverclockCalculator / double），注入回调必须用 `CallbackInfoReturnable<...>`；误用 `CallbackInfo` 编译期查不出来，运行时 APPLY 阶段抛 InvalidInjectionException，导致目标类整体 transform 失败 → 游戏内 NoClassDefFoundError/崩溃
- **已知例外（2.9.0 新内容）**：工业电弧炉 `kubatech/.../MTEIndustrialArcFurnace` + `ArcFurnaceElectrode` 电极表，`OCSpeedFactor` 为 1（钨/钨钢/无尽/海珀根）、1.5（下界合金）、2、3、4（钽）、6（YBCO）、8（宇宙素纳米）。按新规则：1/1.5/2/3 → 4，4/6/8 保持。聚变 `FusionOverclockDescriber`（EU 倍率 2、时长 2）会被提升到 4，结果变成"时长÷4、功率×2"，总耗能减半（用户知情接受）
- **WA（非 Mixin，主类 preInit 改写速度表）**：`MTEWorldAccelerator.mAccelerateStatic`（public static final 数组，引用 final 但元素可写）原版为 `{1,2,4,8,16,32,64,128,256,512,512...}`（16 项，2^档、512 封顶），替换为 4^档表 `{1,4,16,64,256,512,...}`；耗电 `getEnergyDemand` 只依赖档位电压（每档 ×4）→ 严格无损；档位 5+ 速度封顶 512 无收益
- **Miner（非 Mixin，同上方式）**：`MTEMiner.SPEED`（package-private `static final int[]`）经反射取引用后改写元素为 `{160,160,40,10,5}`（原版 `{160,160,80,40,20}`，`ENERGY = {8,8,32,128,512}`），耗电保持原版 —— 每方块耗能 T0-T3 恒 1280 EU、T4 为 2560（**SPEED&lt;5 会触发 DrillingLogicDelegate 的 `aTick % (getMachineSpeed()/5)` 除零崩溃**，故顶档取 5t 非严格 ×4）
- 两者均非 Mixin：数组元素可变（仅引用 final），普通赋值即可，Java 25 无反射写 final 问题（Miner 仅反射读引用）
- 编译依赖 `compileOnly GTNHLib 0.11.54`（GT5U API 引用 gtnhlib 类，编译期必需，运行时 2.9.0 自带）

## GT5U 超频架构速查（265 源码核实）

- 超频核心 `OverclockCalculator.calculateOverclock()`：`consumption = recipePower × eutIncreasePerOC^OC`（默认 4）；`duration /= durationDecreasePerOC^OC`（默认 2 ← 有损根源）
- `enablePerfectOC()` = 官方完美超频 API（把 durationDecreasePerOC 设为 4）
- `setDurationDecreasePerOC` 的调用方（265）：`ProcessingLogic`（`overClockTimeReduction`，默认 2.0）、`FusionOverclockDescriber`（2.0/2.0）、`MTEExoFoundry`（`2.0 + additive`）、`MTENanoForge`（2.0 或 4.0）、`MTEPCBFactory`（2.0 或 4.0）、`MTEIndustrialArcFurnace`（电极因子）、TecTech 天炉模块（`getOverclockTimeFactor()`）
- 单块机：`MTEBasicMachine.calculateCustomOverclock()` → `EUOverclockDescriber` 只设 EUt、不碰时长因子
- ProcessingLogic 默认 `overClockTimeReduction = 2.0`；LCR 显式 `enablePerfectOverclock()`；EBF 走热量超频（final 4.0/1800K），不受本 mod 影响
- 源码参考：本机可解压 `~/.gradle/caches/modules-2/files-2.1/com.github.GTNewHorizons/GT5-Unofficial/5.09.54.265/*-sources.jar`

## 构建

- `./gradlew build` → 产物 `build/libs/gt-perfect-overclock-1.2.0.jar`（jar 名由 build.gradle.kts 的 `base.archivesName` 控制，modid 仍为 gtpoc）
- 改完代码先 `./gradlew spotlessApply`（import 顺序/行尾符/折行自动修复），否则 build 会被 spotlessJavaCheck 卡住
- 构建坑：`gradle.properties` 的 `mixinsPackage` 是**相对 modGroup 的路径**（= `mixins`），不是完整包名；写全路径会导致插件报 "Could not resolve mixinsPackage"
- GT5U 编译依赖 5.09.54.265（dependencies.gradle，transitive=false）；UniMixins 由 GTNH 构建脚本因 `usesMixins = true` 自动引入，2.9.0 客户端自带
- 版本号：`RELEASE_VERSION` 为准，`modVersion` 需手动保持一致

## 工作流程（用户习惯，务必遵守）

- 用户自己进游戏实测每个版本，**实测通过前不要提交**
- 提交信息：中文、Conventional Commits 格式（如 `feat(mixin): ...`）
- 改动前先出影响分析报告、经用户批准再动手（comprehensive-analysis 技能）
- 沟通用中文

## 当前状态（2026-11-06）

- 主分支：适配 GTNH 2.9.0 / GT5U 5.09.54.265，v1.2.0
- 2026-11-06：超频规则改为「只提升 <4 的因子」；蜂箱补 UI 显示注入（`getAcceleration`）、第 5 级起产量补偿、繁殖注入自校验；构建通过，**待实机实测**
- 实机验证清单：蜂箱 GUI 显示 4^等级而耗电提示不变；电弧炉 YBCO 仍为 6、钨电极（因子 1）会被提升到 4；聚变因子 2 → 4；普通机器无损超频
- **v1.1.0 已实测通过**（WA 加速 ×4/档、采矿场挖速 ×4/档，耗电均保持原版；开关还原正常）
- **v1.0.0 已发布**：GitHub Release（`lelqwq/GT-Perfect-Overclock`），附主 jar / dev / sources 三个产物
- 实机环境：客户端 UniMixins 0.1.23 / Java 25
- 远程仓库：`git@github.com:lelqwq/GT-Perfect-Overclock.git`（`main`）
