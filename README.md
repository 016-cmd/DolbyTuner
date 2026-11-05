# Dolby Tuner

小米 / Redmi / POCO 设备上的 **免 root 实时调音与音效增强**应用。

不做文件替换、不改 tuning XML、不重启音频服务 —— 杜比参数通过 `AudioEffect` 的 DAP 参数通道直接写进 DSP，
松开滑杆就生效；另一套自建的 `DynamicsProcessing` 管线负责更细的均衡与动态处理。

> 作者：**016. · Aoi** ｜ 许可：[Apache License 2.0](LICENSE)

---

## 功能

### 杜比 GEQ（20 段图形均衡器）
- 47 Hz ~ 19.7 kHz 共 20 段，−24 ~ +24 dB（1 格 = 1/16 dB，Q4 定点）
- 实时曲线预览，横轴标真实频点
- 双击某段归零、整排归零、重置平直
- 写入后提交到参数存储，重启手机仍沿用

### 杜比音效
| 模块 | 范围 |
|---|---|
| 耳机虚拟环绕 | 开 / 关 |
| 扬声器虚拟环绕 | 开 / 关 |
| 人声增强 | 开关 + 强度 0 ~ 16 |
| 低音增强 | 开 / 关 |
| 低音增益 / 截止 / 带宽 | 0 ~ 192 / 80 ~ 300 Hz / 0 ~ 64 |

- 人声增强只抬人声频段，不压低背景
- 「重置为 DSP 默认值」按本机检测到的那套参数恢复，不是写死的一份

### 音效页（DynamicsProcessing 实时管线）
与杜比页完全隔离：这套参数随进程释放，杜比页参数退出 APP 仍生效。

- **参数均衡**：多段 PEQ（频点 / 增益 / Q 可调），曲线实时预览
- **图形均衡**：等宽频段增益，同样带曲线
- **输出增益**：整体电平，可调至 −10 dB 以下
- **等比增益**：按当前 EQ 最终曲线的能量自动反向补偿，听感音量不变（开启后总增益滑杆锁定）
- **限幅器**：阈值 / 上限 / 释放
- **多段压缩**：分段阈值、比例、起振 / 释放
- **等响补偿**：小音量下补低频与高频，避免“越听越薄”
- 进页面 / 打开总开关即全量下发，退出重进仍是 UI 上那套值
- **通话自动暂停**：普通电话与微信 / QQ 等 VoIP 通话都会把总开关临时关掉（UI 显示为关），挂断按记录恢复

### 预设
- 保存 / 加载 / 删除 / 导入 / 导出，各自独立弹窗
- 一份预设 = GEQ 20 段 + 杜比音效 + 音效页参数
- 可按设备绑定预设，插拔耳机或切换输出时自动套用

### 后台
- **开机参数自动写回**：开机后把杜比与音效参数重新下发一次
- **后台设备监听**：前台服务 + 常驻通知，息屏时也按绑定预设切换
- **后台保活**：被系统清掉后由 `JobScheduler` 定时把服务拉回来
- **后台隐藏**：最近任务里不显示本应用，服务照常运行
- 打开后台类开关时会提示两件事：最近任务里把卡片上锁、省电策略改为「无限制」，并可一键跳到系统的省电策略页面

### 外观
- **液态玻璃**：卡片是真折射（AGSL 实时采样壁纸层，API 33+），低版本 / 省电模式回退纯色玻璃
- **壁纸派生配色**：强调色、按钮、拉条、开关、曲线全部从当前壁纸取色，换壁纸一起变
- 内置三张壁纸（绝区零 / 海风 / 微醺）+ 系统相册自定义，可一键移除
- 清透 / 标准 / 浓厚 / 柔和 / 省电五档玻璃质感，圆角与厚度可调
- 可读性开关：小字默认纯黑 / 纯白，增强档 100% 不透明并按壁纸亮度反向
- 果冻按压反馈、卡片展开动画、LazyColumn 长列表（屏幕外不参与组合）
- 浅色 / 深色 / 跟随系统

### 其他
- 首次进入的 6 屏引导：功能介绍 → 设备检查 → 音效页与后台开关 → 记录本机默认值
- 快捷设置磁贴：一键开关音效页
- 设置页可重新查看引导、重新检测本机默认值、查看许可证全文

---

## 环境要求

- 小米 / Redmi / POCO 设备，系统自带杜比音效引擎
- Android 11+（`minSdk 30`，`targetSdk 36`）
- **不需要 root**，不需要 Magisk 模块

非小米系机型会在启动时被拦下并说明原因。

---

## 构建

```bash
# 需要 JDK 17+ 与 Android SDK（compileSdk 37）
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

仓库内 `app/dolbytuner.jks` 是自用签名（口令 `android`，别名 `androiddebugkey`），
`debug` 与 `release` 都绑定这一个 key，目的是让 CI 每次产出可直接覆盖安装的 APK。
**公开发布前请换成你自己的 release keystore，不要把私钥与口令提交进仓库。**

GitHub Actions 会自动编译并上传 APK 产物，见 `.github/workflows/`。

---

## 目录结构

```text
app/src/main/java/com/zero16/dolbytuner/
├── MainActivity.kt              # 入口、Gate、四个页面、预设与弹窗
├── Onboarding.kt                # 首次引导（6 屏 + 动效）
├── KeepAliveGuide.kt            # 上锁 / 省电策略引导弹窗与跳转
├── DapRealtime.kt               # DAP 会话、参数包组装、写入与提交
├── DynProc.kt                   # DynamicsProcessing 构建与参数下发
├── DpRestore.kt                 # 音效页参数保存与默认值基准
├── SysDolby.kt                  # 系统杜比状态读取
├── DeviceTuning.kt              # 按设备绑定预设
├── DeviceAutomationService.kt   # 前台服务：设备监听、常驻通知
├── KeepAliveJobService.kt       # JobScheduler 保活
├── CallGuard.kt                 # 通话状态监听
├── BootReceiver.kt              # 开机写回
├── DolbyTileService.kt          # 快捷磁贴
├── Appearance.kt                # 玻璃参数与壁纸取色
├── AppearanceSection.kt         # 外观设置卡
├── WallpaperState.kt            # 壁纸加载与生成
├── GlassBackdrop.kt / GlassCard.kt
├── Theme.kt                     # 主题模式与配色派生
├── DpEqCanvas.kt / BandCurveCanvas.kt / DapBandSlider.kt
└── UiEffectCard.kt              # 音效页卡片
reference/                       # 参数记录与基线数据（开发参考）
```

---

## 参数写入方式

```text
setParameter(5, [0x01000000][count][profile][paramId][values...])
count = values.size + 1
```

- GEQ 走参数 `110`（`graphic_equalizer_band_gains`）
- 编号体系与 Dolby `DsParams` 枚举一致（101~111 为 DAP1 参数，200+ 为 CPDP 编号）
- 所有写入都会 commit 到参数存储，开机后由 APP 重新下发一次
- 音效页走 `DynamicsProcessing` 的 `Effect` 接口，与杜比参数互不干扰

---

## 说明与免责

- 本项目只调用设备上已存在的音频引擎，不包含任何商业音效资源或私有库。
- 参数编号与写入格式来自公开资料与真机探测，实现为本项目自写。
- 调音有风险：长时间大增益可能损伤听力或扬声器，请自行控制幅度。
- 因使用本软件造成的设备异常、听力损伤或数据损失，作者不承担责任。

---

## 许可

本项目以 [Apache License 2.0](LICENSE) 发布。

```text
Copyright 2026 016. · Aoi
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```