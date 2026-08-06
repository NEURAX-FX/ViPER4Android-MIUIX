# LSP C++ 绘制引擎、ViPER Driver 与 Android 编辑器完整审计报告

## 1. 审计范围与方法

本报告直接审计 LSP 的 C++ 图形/DSP 数据管线，不以 XML 布局和配色作为技术依据。交叉比对对象包括：

- LSP 图形容器、坐标轴、mesh、控制点、标记线、旋钮和推子实现；
- LSP Graphic EQ、Parametric EQ、Multiband Compressor 的 DSP mesh 生成代码；
- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP` 中的 ViPER 实际 EQ、Dynamic EQ、MBC、Biquad 和参数换算代码；
- ViPER 当前 Compose 曲线模型、Canvas 绘制、触控、状态更新、持久化和功能入口；
- 2026-08-05 提供的两张实机截图及此前问题结论。

本报告不修改应用实现。结论按功能正确性、数据真实性、交互可靠性、视觉可读性和测试完备性排序。

ViPER driver 源码位于同级项目 `ViPERFX_RE/ViPERDSP`，本次已初始化并纳入审计。因此 FIR 固定频率、滤波器递推式、Dynamic EQ biquad、MBC crossover/FET 参数语义和采样率依赖现在都可以直接验证。Driver 当前没有提供 response mesh 或每段实时 gain telemetry，动态效果的“当前瞬时响应”仍无法只靠 Android state 完整还原。

## 2. 结论摘要

当前问题不是单纯的“曲线不够平滑”，而是四层问题叠加：

1. **功能迁移不完整**：主页面通过无条件 `return` 移除了旧控件，但独立编辑器没有补齐全部参数，已经造成真实音频功能不可访问。
2. **曲线数据不是 ViPER DSP 传递函数**：FIR driver 是并联 IIR band-pass bank，Dynamic EQ 是串联 RBJ biquad，MBC 是 LR4 类 crossover + stateful FET；UI 却使用 `smoothStep`、Gaussian 和参数平均值。
3. **绘制器与 LSP 原理相反**：LSP 先计算高密度真实响应，再用直线 mesh 绘制；当前实现先生成近似响应，再叠加三次样条，产生额外过冲和波形失真。
4. **状态与交互没有闭环**：部分列表编辑不下发 DSP，旋钮不进入 undo 历史、undo 不持久化、精确输入无法解析带单位文本、频率旋钮使用线性映射、畸形持久化列表仍可能触发崩溃。

因此，继续调整颜色、圆角、填充或样条张力不会解决根因。首先必须修复参数下发/单位和功能完整性，再用 driver 同源公式建立可信响应，最后才是图形细节。

## 3. LSP C++ 引擎的真实工作方式

### 3.1 曲线数据由 DSP 生成，不由 UI 插值生成

Graphic EQ：

- 每个频段调用 `sEqualizer.freq_chart()` 计算 640 个频率点上的复数响应；
- 总响应从复数单位响应开始，逐频段调用 `complex_mul2()` 合成；
- 最后通过 `complex_mod()` 得到幅度 mesh。

证据：

- `/tmp/opencode/lsp-plugins-graph-equalizer/include/private/meta/graph_equalizer.h:44`
- `/tmp/opencode/lsp-plugins-graph-equalizer/src/main/plug/graph_equalizer.cpp:887`
- `/tmp/opencode/lsp-plugins-graph-equalizer/src/main/plug/graph_equalizer.cpp:899`
- `/tmp/opencode/lsp-plugins-graph-equalizer/src/main/plug/graph_equalizer.cpp:918`

Parametric EQ：

- 滤波器类型、斜率、频率、第二频率、增益和 Q 先进入真实 equalizer 参数；
- 每个滤波器和总响应都通过 `freq_chart()` 计算；
- 总响应同样以复数乘法合成，而不是将若干 dB 钟形函数直接相加。

证据：

- `/tmp/opencode/lsp-plugins-para-equalizer/src/main/plug/para_equalizer.cpp:1067`
- `/tmp/opencode/lsp-plugins-para-equalizer/src/main/plug/para_equalizer.cpp:1079`
- `/tmp/opencode/lsp-plugins-para-equalizer/src/main/plug/para_equalizer.cpp:1595`
- `/tmp/opencode/lsp-plugins-para-equalizer/src/main/plug/para_equalizer.cpp:1644`

Multiband Compressor：

- 分频滤波器按 Classic、Modern、Linear Phase 三种模式构建；
- 频率图使用各频段真实滤波器响应和最后一个 VCA 增益值合成；
- 该 mesh 以 20 Hz 刷新率更新；
- 每个频段另有独立的 256 点输入/输出压缩曲线，和频率图不是同一张图。

证据：

- `/tmp/opencode/lsp-plugins-mb-compressor/include/private/meta/mb_compressor.h:100`
- `/tmp/opencode/lsp-plugins-mb-compressor/include/private/meta/mb_compressor.h:133`
- `/tmp/opencode/lsp-plugins-mb-compressor/include/private/meta/mb_compressor.h:143`
- `/tmp/opencode/lsp-plugins-mb-compressor/src/main/plug/mb_compressor.cpp:1033`
- `/tmp/opencode/lsp-plugins-mb-compressor/src/main/plug/mb_compressor.cpp:1112`
- `/tmp/opencode/lsp-plugins-mb-compressor/src/main/plug/mb_compressor.cpp:1671`
- `/tmp/opencode/lsp-plugins-mb-compressor/src/main/plug/mb_compressor.cpp:1847`
- `/tmp/opencode/lsp-plugins-mb-compressor/src/main/plug/mb_compressor.cpp:1953`

### 3.2 GraphMesh 不做样条拟合

`GraphMesh::render()` 的流程是：

1. 读取原始 X/Y 数组；
2. 分别调用 X/Y `GraphAxis::apply()` 投影；
3. 可选地通过 strobe 切分多个片段；
4. 直接调用 `wire_poly()` 或 `draw_poly()` 绘制折线/填充多边形。

引擎中没有 Catmull-Rom、Bezier、PCHIP 或其他曲线拟合步骤。

证据：

- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphMesh.cpp:231`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphMesh.cpp:269`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphMesh.cpp:288`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphMesh.cpp:307`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphMesh.cpp:330`

这意味着 LSP 的“平滑”来自高密度、物理正确的采样点和抗锯齿，不来自 UI 对稀疏点的自由插值。

### 3.3 坐标轴负责统一投影和反投影

`GraphAxis` 对线性轴和对数轴分别处理：

- `apply()` 将原始参数映射到画布坐标；
- `project()` 将鼠标坐标反投影回原始参数；
- X/Y 最终会经过 `saturate()` 限制到画布；
- GraphDot、GraphMarker 和 GraphMesh 共用同一组轴，因此控制点与曲线不会使用两套映射。

证据：

- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphAxis.cpp:153`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphAxis.cpp:190`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphAxis.cpp:225`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphAxis.cpp:232`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/Graph.cpp:579`

### 3.4 Graph 容器处理内部画布、缓存和文字碰撞

LSP 的 `Graph` 不是一个直接铺满控件的裸画布：

- 外框、内部画布和内部 padding 分离；
- 图形项目绘制到缓存 surface，再合成到控件；
- 带有同组优先级的项目会先计算 bounding box，发生碰撞时丢弃低优先级项目；
- GraphText 使用轴投影和真实文字尺寸确定区域。

证据：

- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/Graph.cpp:203`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/Graph.cpp:249`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/Graph.cpp:328`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/Graph.cpp:339`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphText.cpp:195`

### 3.5 控制点和旋钮有完整编辑事务

GraphDot/GraphMarker：

- 各轴独立声明是否可编辑；
- 命中使用实际像素几何；
- 拖动通过轴反投影回参数；
- 发出 begin-edit、change、end-edit；
- 支持步进和精细调节。

Knob/Fader：

- 显式提供 balance/neutral 值；
- 参数值可以通过属性转换实现对数映射和量化；
- 支持普通、加速和减速步进；
- Knob 可显示独立 meter 区间。

证据：

- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphDot.cpp:357`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphDot.cpp:409`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphDot.cpp:512`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphMarker.cpp:355`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/graph/GraphMarker.cpp:482`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/simple/Knob.cpp:378`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/simple/Knob.cpp:496`
- `/tmp/opencode/lsp-tk-lib/src/main/widgets/simple/Fader.cpp:387`

### 3.6 ViPER FIR 是并联 IIR filter bank，不是 LSP cascade EQ

ViPER 的 10/15/25/31 段中心频率由 driver 明确定义：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/utils/MinPhaseIIRCoeffs.cpp:4-37`

每个 band 的递推在 `IIRFilter::Process()` 中执行，随后所有 band 以线性振幅相加：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/IIRFilter.cpp:33-72`

令 driver coefficient 为 `c1 = coeff[1]`、`c2 = coeff[2]`、`c3 = coeff[3]`，单 band 传递函数可以从递推式直接写成：

```text
H_i(z) = c2 * (1 - z^-2) / (1 - c3*z^-1 + c1*z^-2)
```

总响应不是 LSP Graphic EQ 的复数 cascade product，而是：

```text
H_total(z) = sum_i(0.636 * 10^(gain_i/20) * H_i(z))
```

driver coefficient 还依赖当前 sample rate：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/utils/MinPhaseIIRCoeffs.cpp:66-128`
- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/IIRFilter.cpp:101-108`

因此，LSP 的绘制管线可以参考，但不能直接复制 LSP Graphic EQ 的滤波器模型。

### 3.7 ViPER Dynamic EQ 是串联、状态相关的 RBJ biquad

每个 Dynamic EQ band 使用 `MultiBiquad` 的 Peak/Low Shelf/High Shelf coefficient：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/utils/MultiBiquad.cpp:47-154`
- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/DynamicEQ.cpp:180-228`

各 band 在音频链中串联处理：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/DynamicEQ.cpp:231-269`

UI 中的 gain 是最大目标 gain。实际瞬时 gain 由 envelope 决定：

```text
desired_gain_db = target_gain_db * clamp((envelope_db - threshold_db) / 12, 0, 1)
```

然后再经过 attack/release 平滑。证据：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/DynamicEQ.cpp:238-261`

所以 Android 可以用同源 biquad 计算“最大/目标响应”，但没有实时 `smoothed_gain_db` telemetry 时不能声称它是当前瞬时响应。

### 3.8 ViPER MBC 是 4 阶 crossover 与 stateful FET compressor

每个 crossover 由两个相同的二阶 Butterworth LP/HP 串联，Q 固定为 `0.70710678`；中间 band 先 high-pass 再 low-pass：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/MultibandCompressor.cpp:223-305`
- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/MultibandCompressor.cpp:307-350`

每段压缩器是带 auto knee/gain/attack/release、crest 和 adapt 状态的 FET compressor：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/FETCompressor.cpp:160-247`

因此可离线精确绘制的是 crossover 各 band 的静态 transfer 和 unity sum；实际压缩后的瞬时频率响应需要 driver 暴露每 band 当前 gain。Threshold/Ratio/Knee 则应进入独立的 compressor input/output 图，而不是作为频率图 Y 值。

### 3.9 Driver sample rate 已可查询，但编辑器没有接入

Driver 通过 parameter 4 返回当前 sample rate：

- `/root/AndroidIDEProjects/ViPERFX_RE/src/ViperContext.cpp:266-273`

Android 主页面状态查询已经读取该参数：

- `MainViewModel.kt:1323-1344`

但 `EffectState`/`EffectEditorViewModel` 没有把 sample rate 提供给 curve model。当前三种响应 helper 也都不接收 sample rate。

## 4. 完整问题清单

## Critical

### C-01 独立编辑器迁移导致大量音频功能不可访问

主页面的 FIR、Dynamic EQ 和 MBC 模块都在预览后直接执行无条件 `return`，旧控件全部成为不可达代码：

- FIR：`EffectSections.kt:1128`
- MBC：`EffectSections.kt:695`
- Dynamic EQ：`EffectSections.kt:1233`

但独立编辑器没有恢复等价功能：

- FIR 只剩选中频段 Gain，缺少段数选择、预设选择、预设保存/删除；
- Dynamic EQ 缺少频段新增、删除和 Filter Type；
- MBC 缺少 Band Enable、Knee、Auto Knee、Knee Multi、Auto Gain、Auto Attack、Max Attack、Auto Release、Max Release、Crest、Adapt、No Clip 等参数。

相关状态和驱动参数仍然存在：

- `EffectStates.kt:46`
- `EffectStates.kt:88`
- `EffectGroups.kt:370`
- `EffectGroups.kt:628`
- `ViperDispatcher.kt:508`
- `ViperDispatcher.kt:616`

**影响：** 这是功能回归，不是视觉差异；违反项目“不得在 UI 迁移中丢失音频功能”的硬性规则。

**修复优先级：** 阻断后续 UI 对齐。先建立参数功能清单并恢复全部显式入口。

### C-02 MBC crossover 和 Dynamic EQ/MBC reset 不会实时下发 DSP

`EffectStateStore.updatePref()` 对 `IntListPref` 和 `BoolListPref` 明确返回 `null` command，只更新内存和持久化：

- `EffectStateStore.kt:168-186`

但 MBC crossover 更新使用的是整列表 `updatePref()`，不是带 band index 的 `updateBandPref()`：

- `EffectEditorViewModel.kt:98-101`

Dynamic EQ 和 MBC reset 也连续调用整列表 `updatePref()`：

- `EffectEditorViewModel.kt:129-145`

这些路径没有在最后调用 `dispatchFullState()`。

**影响：** crossover 拖动/旋钮会更新界面和 preference，却不会改变当前正在运行的 DSP；Dynamic EQ/MBC reset 也不会立即改变声音。只有服务重连、手动 full-state 或进程重启后的重新下发才可能使声音追上界面。

**修复方向：** crossover 用 `updateBandPref()` 下发变更 band；多列表 reset 使用一个原子 state transaction，并在结尾执行一次 full-state dispatch 和一次批量持久化。增加“UI state、dispatch command、persisted value 三者同步”的回归测试。

### C-03 Dynamic EQ reset 会留下互相矛盾的 bandCount 和列表长度

Dynamic EQ reset 把七个 band list 重置为 3 项，但没有把 `bandCount` 重置为 3：

- `EffectEditorViewModel.kt:129-137`

Screen 又用 `min(bandCount, freqs.size, gains.size)` 决定可见频段：`EffectEditorScreen.kt:248`；Dispatcher 则按原始 `bandCount` 循环，并对缺失项填默认值：`ViperDispatcher.kt:619-629`。

**影响：** 例如从 10 段执行 reset 后，UI 只显示 3 段，状态仍声明 10 段；下一次 full-state 会向 DSP 下发另外 7 个 UI 不可见的默认频段。多项 preference 还是独立异步写入，进程中断时可能留下混合快照。

**修复方向：** reset 必须一次性生成完整、normalized 的 `DynamicEqState(bandCount = 3, ...)`，通过单一 transaction 原子更新内存、DSP 和持久化。

### C-04 [已撤回] FET/MBC 时间参数换算

本条在初版报告中被判定为缺陷，经复核为**错误结论，予以撤回**。

初版声称 Android 按 `milliseconds / 500` 编码 attack/release/max time。复核当前 `HEAD` 代码后确认并非如此：

- `ParamRaw.fetCompressorAttackMsF()` 实际实现为 `(ln(ms/1000) + 9.21034) / 7.600903`；
- `ParamRaw.fetCompressorReleaseMsF()` 实际实现为 `(ln(ms/1000) + 5.298317) / 5.991465`。

这两者正是 driver 解码公式的精确反函数：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/FETCompressor.cpp:99-113`

初版还声称 Max Attack/Release 应改用 `milliseconds / 1000`。这同样错误。Driver 的 `SetMaxAttack()`、`SetMaxRelease()` 和 `SetCrest()` 使用与 attack/release **相同**的 exp 解码，不是把 value 当秒数：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/FETCompressor.cpp:123-135`

因此序列化器对这些参数复用同一反函数是正确的：

- `ViperParamsSerializer.kt:188-194`
- `ViperParamsSerializer.kt:459-466`

**当前状态：** 无缺陷，无需修改。初版结论源于未核对 `HEAD` 实际实现，属审计错误。

### C-05 Dynamic EQ raw band index 未校验，可写越界

`ViPER::DispatchRawParam()` 直接把客户端提供的 `val1` 传给 Dynamic EQ setter：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/ViPER.cpp:928-960`

而 `SetBandFrequency/Q/Gain/Threshold/Attack/Release/FilterType()` 都直接索引固定 10 项数组，没有 `band < kMaxBands` 检查：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/DynamicEQ.cpp:136-178`

MBC setter 已有 `band >= kMaxBands` 防护，Dynamic EQ 没有。

**影响：** 畸形或恶意 AudioEffect parameter 可在 audioserver/effect 进程内造成越界写，属于 driver memory-safety 问题。Android 当前 UI 限制不能替代 driver 端边界检查。

**修复方向：** 所有 raw setter 在最外层和模块内部双重检查 index；无效参数返回错误而不是静默继续。增加 0、9、10、`UINT32_MAX` 边界测试并在 ASan/UBSan 下运行。

## High

### H-01 FIR 15/25 段频率表已被 driver 源码证实错误

独立编辑器在 `EffectEditorScreen.kt:466` 维护了一套硬编码频率表；主页面/现有 Dispatcher 标签在 `ViperDispatcher.kt:345` 维护另一套表。

15 段示例：

- 编辑器：31, 39, 49, 62, 78, 98, 125, 157, 196, 250, 315, 500, 1k, 4k, 16k；
- Dispatcher 标签：25, 40, 63, 100, 160, 250, 400, 630, 1k, 1.6k, 2.5k, 4k, 6.3k, 10k, 16k。

Driver 的真实表在 `MinPhaseIIRCoeffs.cpp:4-37`。Dispatcher 除了把 `31.5` 显示为 `31` 外与 driver 表一致；独立编辑器的 15/25 段表不一致。31 段和 10 段基本一致。

**影响：** 同一 band index 在主预览和独立编辑器中对应不同频率；15/25 段独立编辑器的标签、handle X 和响应计算已经被 driver 源码证实为错误。

**修复方向：** 删除重复表，建立一个由驱动规格、序列化、主预览和编辑器共同使用的 `EqBandSpec` 数据源，并增加逐项一致性测试。

### H-02 FIR 曲线模型不符合 ViPER driver 的并联 IIR 响应

当前 `EqResponse.kt:26` 声称“Mirrors the band layout used by LSP”，但实现只是在 log2 频率上构造固定 0.5 octave `smoothStep` 权重，然后把各频段 dB 线性相加：

- `EqResponse.kt:33`
- `EqResponse.kt:50`
- `EqResponse.kt:59`

LSP Graphic EQ 使用 Low Shelf、High Shelf、Ladder Pass filter cascade，逐频率计算复数响应后相乘：

- `graph_equalizer.cpp:554-575`
- `graph_equalizer.cpp:887-940`

ViPER driver 则使用另一种结构：每个固定 band 是二阶 IIR band-pass，按线性振幅加权后并联求和：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/IIRFilter.cpp:33-72`
- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/utils/MinPhaseIIRCoeffs.cpp:66-128`

当前测试只证明自定义模型内部自洽，甚至明确固定了“相邻 +6 dB 在中间应大于 +6 dB”的假设：`EqResponseTest.kt:49`。它没有任何 driver coefficient golden vector。

**影响：** 曲线形状、过渡带宽、相邻段叠加和边缘 shelf 都可能与实际声音不符；多个增益叠加后被 `dbToY()` 截断，产生截图中的贴顶/贴底平台。

**修复方向：** 按 `MinPhaseIIRCoeffs` 和 `IIRFilter` 的精确递推式实现只读 response evaluator，输入 band count、band gains、sample rate 后对各并联 band 的复数响应求和。不要复制 LSP Graphic EQ 的 cascade filter 模型；LSP 只作为 mesh/axis/rendering 架构参考。

### H-03 Dynamic EQ 曲线忽略 Filter Type，并用高斯函数冒充滤波器

当前模型把所有频段都视为对称 Gaussian bell：

- `EqResponse.kt:76`
- `EqResponse.kt:86`
- `EqResponse.kt:104`

但状态和驱动明确支持 `filterTypes`：

- `EffectStates.kt:97`
- `EffectGroups.kt:699`
- `ViperDispatcher.kt:627`

旧 UI 支持 Peak、Low Shelf、High Shelf，独立编辑器既没有类型控件，也没有在曲线计算中读取类型。

ViPER driver 的实际 Peak/Low Shelf/High Shelf coefficient 已在 `MultiBiquad::ComputeCoefficients()` 中给出：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/utils/MultiBiquad.cpp:47-154`
- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/DynamicEQ.cpp:180-228`

各 band 串联，所以给定某一时刻的 `smoothed_gain_db` 后，总静态响应以复数乘法合成。LSP 参数 EQ 也采用真实滤波器和复数合成，而不是 Gaussian：

- `para_equalizer.cpp:1067-1109`
- `para_equalizer.cpp:1595-1685`

**影响：** Low Shelf 和 High Shelf 在图中仍显示成 bell；Peak 的带宽和 Q 关系也不符合 driver。当前 UI 使用 target gain，但 driver 实际 gain 还取决于 envelope、threshold、attack 和 release，因此图形没有说明它表示“最大目标”而不是当前响应。

**修复方向：** 恢复 Filter Type 控件；移植 `MultiBiquad` coefficient 公式，以当前 sample rate 计算 target/max response。若要显示实时响应，driver 必须暴露每 band 的 `smoothed_gain_db`。

### H-04 MBC 把阈值点放在频率图上，图形语义仍然错误

独立编辑器虽然移除了旧的 threshold stair curve，但仍然把每段 threshold 作为 `(频段中心频率, threshold dB)` 控制点绘制：

- `EffectEditorScreen.kt:332`
- `EffectEditorScreen.kt:335`
- `EffectEditorScreen.kt:358`

这仍然把“压缩器输入阈值”伪装成“该频率的幅度响应”。LSP 的 MBC 明确分成两类数据：

- 频率图：分频滤波器响应与实时 VCA 增益合成的 640 点 mesh；
- 压缩图：每个频段独立的 256 点输入/输出 compressor curve。

ViPER driver 已明确 crossover 是两级 Butterworth LP/HP 构成的 4 阶分频：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/MultibandCompressor.cpp:223-305`

当前应用没有实时 per-band compressor gain telemetry，因此不能复刻 LSP 的动态频率曲线；但可以用 driver 同源 biquad 精确绘制静态 crossover band response 和 unity sum。

**影响：** 用户会把阈值的 Y 坐标误认为该频段的频响；拖动点同时混合了“频段位置”和“压缩参数”两种语义。

**修复方向：** 频率图绘制 driver 同源 crossover band response/总和，并保留 crossover marker；Threshold/Ratio/Knee/Makeup 进入独立 compressor input/output 图。Auto 参数开启时该曲线依赖运行状态，必须明确标记静态假设或由 driver 提供 telemetry。

### H-05 主页面 Dynamic EQ/MBC 预览仍是伪造的平均值直线

`previewCurve()` 只求参数平均值，再生成带固定轻微斜率的 32 点直线：

- `EffectSections.kt:115`
- `EffectSections.kt:117`
- `EffectSections.kt:120`

Dynamic EQ 将 Gain 列表交给该函数：`EffectSections.kt:1227`。

MBC 将 Threshold/4 交给该函数：`EffectSections.kt:689`。

**影响：** 主页面和独立编辑器显示两套不同且都不完全可信的曲线；主页面预览不包含频率、Q、Filter Type 或 crossover 的真实影响。

**修复方向：** 主页面与编辑器必须共用同一个只读 graph model。MBC 在无真实 mesh 时只显示 crossover 分区，不显示伪曲线。

### H-06 三次样条会对已经采样的响应再次造形

`VstResponseGraph` 将响应采样点交给 `buildSplinePath()`：

- `VstResponseGraph.kt:259`
- `EqGraphView.kt:251`
- `EqGraphView.kt:281`

该函数用相邻点计算 cubic Bezier 控制点；只有局部极值降低张力，不能保证单调、不过冲或保持采样点之间的物理边界。

LSP `GraphMesh` 直接画 sample-to-sample polyline，不做任何样条拟合。

**影响：** 近似模型的误差又被样条扩大；窄 Q、截断到图边界的点和相邻频段突变处可能产生非物理隆起/下凹。实机截图中的波浪感与该路径一致。

**修复方向：** 对真实/可信的高密度响应点直接 `lineTo`。不要用 PCHIP 代替当前样条来“修正”错误数据；PCHIP 仍然不是 DSP。采样数应按画布像素宽度或固定上限生成，例如 `min(canvasWidthPx, 640)`。

### H-07 Undo 只覆盖 graph drag，且 undo 结果不持久化

只有图形拖动显式调用：

- `beginGesture()`：`EffectEditorScreen.kt:206`, `:283`, `:375`
- `settleGesture()`：`EffectEditorScreen.kt:211`, `:289`, `:388`

所有 `VstKnob`、精确输入、reset、enable/bypass 都不建立 history operation。

更严重的是，undo/redo 调用 `store.restoreState()`：

- `EffectEditorViewModel.kt:41`
- `EffectStateStore.kt:163`

`restoreState()` 只替换内存状态并 dispatch full state，不写回 preferences。

**影响：** 顶栏 Undo 对大部分编辑无效；对 graph drag 的 undo 在进程内有效，但应用重启后恢复到 undo 前已写入的值。

**修复方向：** 所有编辑控件统一 begin/change/end 事务；history 记录 effect-scoped patch；undo/redo 必须以批量、原子方式更新状态、驱动和持久化。

### H-08 畸形持久化列表仍存在崩溃路径

MBC UI 会用默认值补齐短 `crossovers` 列表：`EffectEditorScreen.kt:327`。但拖动时 `updateMultibandCrossover()` 又读取原始状态列表，并调用带强制索引检查的 `constrainCrossovers()`：

- `EffectEditorViewModel.kt:98`
- `GraphMapping.kt:184`
- `GraphMapping.kt:191`

如果原始列表少于 4 项，UI 看起来正常，但拖动缺失索引会抛 `IllegalArgumentException`。

Dynamic EQ 将最小 count 强制为 1，却直接索引 `freqs[0]` 和 `gains[0]`：

- `EffectEditorScreen.kt:248`
- `EffectEditorScreen.kt:252`

空列表会直接崩溃。主页面预览有同类问题：`EffectSections.kt:1208-1213`。

**修复方向：** 在状态加载边界统一 normalize，而不是只在 Composable 局部补默认值；ViewModel 更新函数必须对 normalized state 操作，并增加空/短/超长/乱序列表测试。

### H-09 响应模型缺少 sample rate，无法匹配 driver coefficient

FIR、Dynamic EQ 和 MBC 的 coefficient 都依赖 sample rate：

- `MinPhaseIIRCoeffs.cpp:105-107`
- `MultiBiquad.cpp:49-53`
- `MultibandCompressor.cpp:223-305`

Android 已能通过 `PARAM_GET_SAMPLING_RATE` 查询真实值，但 Editor state 和 `EqResponse.kt` 的 API 都不接收 sample rate。

**影响：** 即使把滤波器公式移植正确，固定按 44.1/48 kHz 推导的曲线在其他输出配置上仍会和声音不一致。

**修复方向：** 把 driver status/sample rate 提升为共享只读状态；所有 response model 显式接收 sample rate，并在配置变化时失效重算。

### H-10 Driver 未统一校验 frequency、Q、time 和 Nyquist 边界

Dynamic EQ setter 接受任意 frequency、Q、attack、release，`MultiBiquad::ComputeCoefficients()` 会直接执行除法、`sin/cos` 和 `pow`。MBC crossover 也只检查 index，不检查顺序、正值或 Nyquist：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/DynamicEQ.cpp:136-178`
- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/utils/MultiBiquad.cpp:47-154`
- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/MultibandCompressor.cpp:127-135`

FIR 固定表最高到 20 kHz；`ViperContext::HandleSetConfig()` 只检查输入/输出 sample rate 是否相等，不限制最低 sample rate，也不验证 EQ 最高 band 是否低于 Nyquist：

- `/root/AndroidIDEProjects/ViPERFX_RE/src/ViperContext.cpp:93-104`
- `/root/AndroidIDEProjects/ViPERFX_RE/src/ViperContext.cpp:162-171`

`MinPhaseIIRCoeffs` 也没有跳过高于 Nyquist 的 band。

**影响：** 非法 raw 参数可触发除零、NaN、不稳定 biquad 或无意义的 Nyquist 以上滤波器。Android UI clamp 不是 driver 安全边界；低采样率即使使用合法固定表也存在问题。

**修复方向：** Driver 根据当前 sample rate clamp/拒绝 frequency，要求 `Q > 0`、time > 0、crossover 单调，并为 Nyquist 以上 FIR band 定义禁用或重设计策略。

### H-11 Driver 在实时音频循环中执行昂贵 coefficient/exp 计算

Dynamic EQ 在 `smoothed_gain_db` 变化超过 0.1 dB 时，从 `Process()` 内调用 `SetParams()`，其中包含 `pow/sin/cos`：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/DynamicEQ.cpp:257-263`
- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/utils/MultiBiquad.cpp:47-154`

FET Compressor 在 Auto Attack/Release 开启时，每个 sample 重新调用指数函数计算 time coefficient；MBC 最多有 5 个 compressor：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/FETCompressor.cpp:178-200`
- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/MultibandCompressor.cpp:307-350`

**影响：** 默认 MBC auto 参数开启时会在实时线程产生大量 transcendentals；Dynamic EQ 快速变化时也会抖动 coefficient 计算，增加 underrun/CPU spike 风险。

**修复方向：** 在 control-rate 更新 coefficient，使用预计算/查表或每 block 更新；音频 sample loop 只执行稳定的递推和低成本平滑。增加实时性能 benchmark。

## Medium

### M-01 频率旋钮使用线性映射，低频区域几乎不可控

`VstKnob` 始终用 `(value-min)/(max-min)` 和固定拖动距离进行线性映射：

- `VstKnob.kt:57`
- `VstKnob.kt:92`

Dynamic FREQ 和 MBC crossover 都直接传入 `20..20000`：

- `EffectEditorScreen.kt:306`
- `EffectEditorScreen.kt:418`

LSP 参数 EQ 对频率旋钮使用 log-frequency transform：`para_equalizer.cpp:615` 和 `:839`。

**影响：** 20-500 Hz 只占整个旋钮行程约 2.4%，普通拖动难以精确调整；图上的 X 轴却是对数的，旋钮与图形手感不一致。

**修复方向：** `VstKnob` 增加显式 value transform（linear/log/custom），频率和 crossover 使用 log 映射。

### M-02 Bipolar 参数没有 neutral/balance 表达

LSP Knob 从 `balance` 到当前值绘制有效区间：`Knob.cpp:500-581`。当前 `VstKnob` 总是从最小值开始画 progress：`VstKnob.kt:124-131`。

**影响：** Gain、Threshold 等双极/有语义基准的参数不能直观看到相对 0 dB 的方向；0 dB 在视觉上只是 50% 进度，而不是 neutral。

**修复方向：** 增加 `neutralValue`，按 neutral 到 current 绘制；可选增加 0 dB snap，但应将 snap 视为移动端增强，不应错误地声称 LSP 引擎默认具有 snap。

### M-03 精确输入预填带单位文本，确认时无法解析

点击值后，输入框预填 `formatValue(value)`：`VstKnob.kt:160`。确认时直接调用 `input.text.toDoubleOrNull()`：`VstKnob.kt:175`。

因此 `"0.0 dB"`、`"1000 Hz"`、`"10 ms"` 都不是合法 Double。用户不删除单位就确认时，值不会改变，但对话框会关闭。

**修复方向：** 输入框保存纯数值，单位作为独立 suffix/label；无效输入必须保留对话框并显示错误。

### M-04 标签没有碰撞管理，且占用真实绘图区

当前频率轴会标记每个 decade 中的 1/2/3/5，窄屏可达到十余个标签：

- `EffectEditorScreen.kt:474`
- `EffectEditorScreen.kt:488`

`VstResponseGraph` 只把文字坐标 clamp 到画布内，没有检测相邻文本重叠：

- `VstResponseGraph.kt:245`
- `VstResponseGraph.kt:248`

LSP Graph 会先计算 bounding box，再按 priority group 丢弃冲突项目：`Graph.cpp:339-385`。

当前图还没有独立 plot rect；标签、曲线和边缘 handle 共用完整 Canvas。

**影响：** 实机截图中的底部频率文字拥挤、相互覆盖或压住曲线；边缘 handle 也会被 Canvas 裁掉一半。

**修复方向：** 建立带 left/bottom gutter 的 plot rect；所有轴映射、曲线、handle 都基于 plot rect；按实测文字宽度做碰撞剔除。

### M-05 绘制尺寸混用裸像素和 dp

触控半径使用 28dp 正确转换，但下列值直接以 Canvas 像素使用：

- 曲线宽度 4f：`VstResponseGraph.kt:261`
- halo 半径 19f/26f：`VstResponseGraph.kt:266-271`
- handle 半径 8f/10f：`VstResponseGraph.kt:271`
- 圆角 18f：`VstResponseGraph.kt:216`
- 网格宽度 1f/1.6f：`VstResponseGraph.kt:227-243`

LSP 所有对应尺寸都乘以 widget scaling。

**影响：** 不同密度设备上视觉尺寸不稳定；高密度手机上的点和线会显得过细，但 28dp 命中区仍很大，视觉目标与触控目标脱节。

**修复方向：** 在进入 Canvas 前统一把 dp 转为 px；只有归一化坐标保留无单位 Float。

### M-06 密集 FIR handle 的命中策略与视觉策略不匹配

31 段在手机宽度上每段间距远小于 28dp 命中半径。当前所有 handle 都绘制 halo，并使用二维最近距离选择：

- `VstResponseGraph.kt:65-87`
- `VstResponseGraph.kt:263-272`

已有 `nearestFixedBand()` 可按 X 选择固定频段，但 FIR 路径没有使用：`GraphMapping.kt:53`。

**影响：** 多个触控区高度重叠；当相邻段 Y 差异较大时，二维最近点可能选择用户并不想编辑的 band。截图中密集圆点还会遮挡响应曲线。

**修复方向：** FIR 使用 X-only band selection；只为 selected/active band 绘制完整 handle，其他频段使用细 fader stem、微小 tick 或不绘制 halo。

### M-07 Tap 与 Drag 使用两个并列 pointerInput 识别器

`VstResponseGraph` 在同一 Canvas 上分别安装 `detectTapGestures` 和 `detectDragGestures`：

- `VstResponseGraph.kt:156`
- `VstResponseGraph.kt:176`

两者各自消费 pointer 事件，但没有一个统一的 tap-vs-drag 状态机，也没有 Compose UI 测试覆盖事件竞争。

**影响：** 存在 tap 被 drag recognizer 抢占、drag 结束后重复选择或不同 Compose 版本行为变化的风险。

**修复方向：** 使用单个 `awaitEachGesture` 流程管理 down、touch slop、drag、up/cancel；一笔手势只触发一套 begin/change/end。

### M-08 每个拖动帧都写偏好并按最终事件分发

graph drag 调用的更新函数默认 `last=true`：

- `EffectEditorViewModel.kt:59`
- `EffectEditorViewModel.kt:70`
- `EffectEditorViewModel.kt:98`

`EffectStateStore.updatePref/updateBandPref` 每次都会 `scheduleWrite()`：

- `EffectStateStore.kt:168-186`
- `EffectStateStore.kt:189-207`

**影响：** 一次手势可能产生大量磁盘写入、服务参数分发和 AIDL republish；Compose 同时重建 handles、response samples 和 Path，容易造成拖动卡顿。

**修复方向：** 手势中只更新内存和实时 DSP（`last=false`），结束时执行一次持久化/republish；使用可取消 debounce 或显式 edit transaction。

### M-09 MBC Gain schema 与 driver 能力不一致，Ratio 标签缺少真实含义

MBC Gain 在 effect schema 和旧 UI 中是 `0..24 dB`：

- `EffectGroups.kt:415-424`
- `EffectSections.kt:896-910`

独立编辑器却提供 `-24..24 dB`：`EffectEditorScreen.kt:407`。Driver 的 `SetGain()` 实际支持正负 dB（normalized value 乘 60 dB）：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/FETCompressor.cpp:95-98`

Ratio 在状态中按百分之一存储并序列化为 `/100f`：`ViperParamsSerializer.kt:454`，独立编辑器显示为百分比字符串：`EffectEditorScreen.kt:406`。Driver 公式中该值是“超过 threshold 部分的 gain-reduction 系数”；`0.5` 等价于经典 2:1，`1.0` 等价于 limiter，超过 `1.0` 会出现负输出斜率：

- `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/FETCompressor.cpp:206-232`

**影响：** EffectPref 和旧 UI 人为禁止了 driver 支持的负 makeup gain，而独立编辑器单独开放；`RATIO 50%` 没有告诉用户它代表 2:1，`100%` 代表 limiter，`>100%` 也不是常规 compressor ratio。

**修复方向：** 参数范围和格式必须由 EffectPref/ParamSpec 单一来源生成，不得在 Composable 中重新硬编码。

### M-10 `VstGraphWorkspace` 尚未实现设计中声明的横屏布局

组件只在传入 `utilityRail` 时将 graph 与 rail 并排：`VstGraphWorkspace.kt:38-54`。当前三个编辑器都没有传入 rail，所有 band strip 和控制组始终堆叠在图下方：`EffectEditorScreen.kt:429-439`。

**影响：** 横屏仍是固定 230dp 图 + 纵向控件列表，不是已批准设计描述的两区域 workspace；实机截图中的“图偏小、下方单旋钮占空”没有真正解决。

**修复方向：** 响应式容器需要同时决定 graph 与 selected-band controls 的相对位置，而不是只处理一个当前未使用的 rail slot。

## Low / Engineering Debt

### L-01 圆角图面没有裁剪 band region

先画圆角背景，再画覆盖完整 Canvas 的矩形 region：`VstResponseGraph.kt:216-225`。Canvas 没有 clipPath，region 会覆盖圆角内部边界。

### L-02 文字和尺寸硬编码，缺少本地化与共享参数定义

`Band`, `GAIN`, `FREQ`, `XO` 等大量字符串直接写在 `EffectEditorScreen.kt`。频率表、范围、默认值也在 Screen、ViewModel、EffectGroup、Dispatcher 多处重复。

### L-03 MBC region 排序与 handle 原始顺序可能不一致

`mbcBandRegions()` 对 crossover 排序：`EqResponse.kt:162`；handle 使用原列表：`EffectEditorScreen.kt:344`。畸形乱序状态下，背景分区和 handle ID 不再一一对应。

### L-04 计划状态错误地表达“已完成”

`docs/superpowers/plans/2026-08-04-lsp-ui-alignment.md` 将自动化任务标记为完成，但人工矩阵没有完成，且上述功能回归、模型错误和崩溃路径仍存在。构建通过只能证明代码可编译，不能证明 LSP 对齐完成。

### L-05 Driver 支持 3/5 band MBC，但 Android 固定为 5 band

Driver `SetBandCount()` 明确接受 3 或 5：`MultibandCompressor.cpp:108-125`。Android schema 将 band count 固定在 `5..5`，编辑器也使用 `MBC_BAND_COUNT = 5`。如果这是产品决策应写入规格；如果不是，则 3-band 模式是尚未暴露的 driver 能力。

Driver 的 `crossover_frequencies` 容器声明为 5 项，但实际只有 `band_count - 1` 个 crossover；`ApplyMultibandCompressor()` 对 5-band 状态循环 5 次，第五次依赖 setter 静默忽略。这不是当前崩溃点，但应收敛数据模型避免误用。

## 5. 对此前结论的校正

### 保留但需要改写

1. **样条过冲**：确认存在；但正确方向不是换成 PCHIP，而是先获得可信的高密度 DSP samples，再直接画 polyline。
2. **采样密度偏低**：部分成立。独立编辑器 96 点、主预览 32 点，低于 LSP 的 640 点；但根因优先级低于错误响应模型和平均值伪曲线。
3. **handle 过密和误选**：确认存在，尤其是 31 段 FIR；应改选点/渲染策略，而不是只减小圆点。
4. **标签压住绘图区**：确认存在；LSP 的关键差异是内部 plot canvas 和碰撞剔除，而不只是字体大小。
5. **图表主次比例不佳**：实机结论成立；当前固定 230dp 和始终纵向堆叠仍未构成完整响应式 graph-first workspace。

### 撤回或降级

1. **“LSP 依靠平滑样条”**：错误。C++ `GraphMesh` 直接绘制折线/填充多边形。
2. **“当前缺少方向锁定”**：不准确。`GraphDragAxis` 已存在，FIR 为 vertical-only，crossover 为 horizontal-only。
3. **“当前网格线过疏”**：对现代码不成立。`frequencyGridLines()` 已生成每 decade 的 1..9 minor grid；当前问题是标签密度、碰撞和 gutter。
4. **“缺少填充是核心问题”**：降级为风格问题。LSP C++ 支持/使用 fill，但填充不会修复错误频响。
5. **“0 dB snap 是 LSP 引擎行为”**：不成立。LSP 提供 balance、step 和 fine tune；snap 可作为移动端增强，但不是 C++ 对齐的事实要求。

## 6. 交叉比对矩阵

| 维度 | LSP C++ | ViPER driver | 当前 Android UI | 结论 |
| --- | --- | --- | --- | --- |
| 数据源 | DSP `freq_chart()` / analyzer mesh | 有同源 coefficient，但无 response telemetry | UI 自建近似/平均值 | UI 没有使用任一真实 DSP 数据源 |
| FIR 结构 | Cascade shelf/ladder filters | 并联固定 IIR band-pass bank | `smoothStep` dB 权重 | 不能复制 LSP filter，也不能保留现近似 |
| Dynamic EQ | 真实 filter cascade | 串联 RBJ Peak/Shelf，gain 随 envelope 变化 | Gaussian bell | 类型、Q 和动态状态均不等价 |
| MBC | crossover response + 实时 VCA mesh | LR4 类 crossover + stateful FET，无 telemetry | band region + threshold dots | Threshold 被放错图；可先精确画静态 crossover |
| 频率采样 | 640 个频率点，通过 log axis 投影 | coefficient 可按任意频率求值 | 编辑器 96，主预览 32 | 密度偏低，但先修模型/sample rate |
| 多滤波器合成 | 依效果使用复数 product/sum | FIR complex sum；Dynamic complex product；MBC band sum | dB smoothstep/Gaussian 直接相加 | 当前合成方式错误 |
| 曲线绘制 | `wire_poly/draw_poly` | 不负责 UI 绘制 | cubic Bezier spline | 当前会对 DSP samples 二次造形 |
| Sample rate | Plugin 明确持有 | Driver parameter 4 可查询 | Editor graph model 未接入 | 真实 coefficient 无法匹配当前流 |
| 轴 | 统一 apply/project，支持 log | 参数使用 Hz/dB/normalized 原值 | 手写 normalized 映射 | 基础公式可用，但缺 plot rect/共享 ParamSpec |
| 标签 | bounding box + priority collision | 不适用 | clamp 坐标，无碰撞检测 | 窄屏重叠 |
| 旋钮 | balance、step、fine tune、transform | FET time 使用非线性 transform | time 换算已正确；频率仍为 linear arc | 频率与 bipolar 手感错误 |
| 更新策略 | DSP/mesh 按需，MBC 20 Hz | 部分 transcendental 在 sample loop | 每拖动帧写偏好并重建 UI | Driver 与 UI 两侧都有实时性能风险 |
| 编辑事务 | begin/change/end 完整 | raw parameter 即时应用 | graph 有，knob/reset 无 | undo 不完整且不持久 |

## 7. 建议修复顺序

### Phase 0：恢复功能完整性

1. 修复 Dynamic EQ driver 越界写。
2. 修复 crossover/reset 不实时下发 DSP 和 Dynamic reset state mismatch。
3. 建立 FIR、Dynamic EQ、MBC 的参数功能清单。
4. 独立编辑器补齐全部仍受 driver 支持的参数。
5. 移除主页面不可达代码，或在独立编辑器功能完整前暂时恢复入口。
6. 增加“每个 EffectPref 至少存在一个可见编辑入口”的策略测试。

### Phase 1：建立单一参数与频率规格

1. 建立 `EqBandSpec`/`EffectParamSpec`，统一 driver 频率、范围、默认值、单位、neutral、linear/log/nonlinear transform。
2. 删除 Screen、Dispatcher、旧 Graph 中重复的 10/15/25/31 频率表，driver table 作为 golden source。
3. 接入 `PARAM_GET_SAMPLING_RATE` 到 Editor graph state。
4. 修复 MBC Gain/Ratio 等范围与格式不一致。
5. 在状态加载和 driver raw boundary normalize/validate 所有参数和 band lists。

### Phase 2：替换响应数据管线

1. 从 `MinPhaseIIRCoeffs/IIRFilter` 实现 FIR 并联 complex-sum evaluator。
2. 从 `MultiBiquad` 实现 Dynamic EQ Peak/Low Shelf/High Shelf complex-product evaluator。
3. 从 `MultibandCompressor` 实现 crossover band response 和 unity-sum evaluator。
4. 为实时 Dynamic EQ/MBC 响应设计只读 driver telemetry；没有 telemetry 时明确显示 target/static response。
5. 使用 log-frequency 高密度采样，直接 polyline 绘制。
6. 主预览和独立编辑器共用相同 graph model。
7. MBC 分离 frequency response 与 compressor input/output curve。

### Phase 3：重建 graph viewport 与触控

1. 引入 plot rect 和轴 gutter。
2. 统一 dp-to-px 尺寸。
3. 单 pointer gesture state machine。
4. FIR 使用 X-only 最近 band，非选中 handle 降噪。
5. 标签按 measured bounds 做碰撞剔除。
6. 频率旋钮使用 log transform，bipolar 参数使用 neutral/balance。

### Phase 4：事务、性能和验证

1. 统一 begin/change/end，drag 中 `last=false`，结束时一次持久化。
2. undo/redo 原子写入状态、驱动和 preferences。
3. 添加跨 Kotlin/C++ 的 coefficient 和 response golden vector 测试，并为现有 FET time transform 补回归测试。
4. Driver 在 ASan/UBSan 下测试 raw index、Q、frequency、time 和低 sample-rate 边界。
5. 添加 Compose gesture、精确输入、横竖屏截图和畸形状态测试。

## 8. 最低验收标准

- FIR 10/15/25/31 段的 band index、频率标签、handle X 和驱动规格完全一致。
- FIR response 与 `MinPhaseIIRCoeffs/IIRFilter` golden vectors 在各支持 sample rate 下匹配。
- Dynamic EQ 的 Peak/Low Shelf/High Shelf coefficient/response 与 `MultiBiquad` 匹配，并明确 target 与 live response。
- MBC crossover response 与 driver biquad 匹配，不再把 threshold 当作频率响应；频率图和压缩 I/O 图语义分离。
- Dynamic EQ raw band index 和所有 frequency/Q/time 参数在 driver 边界安全拒绝非法值。
- 主页面与独立编辑器不再使用平均值伪曲线。
- 响应 samples 使用可信模型并直接 polyline 绘制，不再经过自由样条。
- 所有现有音频参数都有显式可见入口。
- Knob、graph、exact input、reset 都进入同一 undo/edit transaction。
- undo/redo 在应用重启后仍保持一致。
- 空、短、超长、乱序 persisted lists 不崩溃。
- 通过单元测试、Compose 交互测试、横竖屏实机矩阵和 `assembleDebug` 后，才能重新声明“LSP 对齐完成”。

## 9. 当前审计判定

**判定：未达到 LSP 绘制引擎对齐，也未达到 ViPER driver 语义对齐和功能迁移完成。**

当前实现可以编译并显示交互图，但它仍是“UI 近似曲线 + 通用样条 + 部分参数编辑器”，既不是 LSP 式的“DSP mesh + 统一轴 + 原始 polyline + 完整编辑事务”，也没有复现 ViPER driver 的并联 IIR、动态 biquad、LR4 crossover 和非线性 FET 参数语义。实机截图暴露的是最终表现，双 C++ 源码与 Kotlin 的交叉审计确认根因位于参数换算/下发、DSP 数据模型、功能迁移和交互事务，而不是单一绘制样式。
