# Dolby Tuner

小米 / Redmi / POCO 设备上的 **无 root 杜比实时层调音器**。

不做文件替换、不改 tuning XML、不重启音频服务 —— 全部参数通过 `AudioEffect` 的
Dolby DAP 参数通道直接写进 DSP，松开滑杆就生效。

> 作者：**016. · Aoi** ｜ 许可：[Apache License 2.0](LICENSE)

---

## 功能

### GEQ（20 段图形均衡器）

- 47 Hz ~ 19.7 kHz 共 20 段，滑杆范围 −12 ~ +12 dB（1 段 = 1/16 dB）
- 实时曲线预览，横轴显示真实频点（1k / 1.3k / 1.7k … 19.7k）
- 双击某段归零、整排归零、重置平直
- 写入后提交到 Dolby 参数存储，重启手机仍沿用

### 音效（实时层参数）

| 模块 | 参数 | 范围 |
|---|---|---|
| 耳机虚拟环绕 | `vdhe` / 101 | 开 / 关 |
| 扬声器虚拟环绕 | `vspe` / 102 | 开 / 关 |
| 人声增强 | `deon` 105 + `dea` 108 | 开关 + 0~16 |
| 低音增强 | `beon` / 111 | 开 / 关 |
| 低音增益 | 233 | 0 ~ 192 |
| 低音截止 | 234 | 80 ~ 300 Hz |
| 低带宽 | 235 | 0 ~ 64 |

- **人声增强**只提升人声频段，不压低背景（`ded` 未使用）
- 无效参数（205 环绕解码 / 226 环绕增强量）经真机验证后已移除，不留假开关
- 「重置为 DSP 默认值」同时归零 GEQ 与全部音效

### 预设

- 保存 / 加载 / 删除 / 导入 / 导出，四个按钮各自弹窗
- 一份预设 = GEQ 20 段 + 全部音效参数
- JSON 结构：

```json
{
  "name": "预设名",
  "geq": [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  "hp": 0, "sp": 0, "bass": 0,
  "gain": 80, "cutoff": 150, "width": 16,
  "dialog": 0, "damt": 4
}
```

### 界面

- 三 Tab 液态玻璃 Dock：**GEQ ｜ 音效 ｜ 设置**
- 浅色 / 深色 / 跟随系统；自定义壁纸（系统相册）、移除壁纸
- 主页动态显示真实机型（读 `ro.product.marketname`，取不到回退 `Build.MODEL`）
- 设置页「关于」卡：开发者、机型、版本、可点开的 Apache-2.0 许可证全文

---

## 环境要求

- 小米 / Redmi / POCO 设备，系统自带 Dolby DAP 音效引擎
- Android 11+（`minSdk 30`，`targetSdk 36`）
- **不需要 root**，不需要安装 Magisk 模块

非小米系机型会在启动时被拦下并提示原因。

---

## 构建

```bash
# 需要 JDK 17+ 与 Android SDK（compileSdk 37）
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

仓库内 `app/dolbytuner.jks` 是自用的 debug 签名（口令 `android`，别名 `androiddebugkey`），
`debug` 与 `release` 都绑定这一个 key，目的是让 CI 每次产出可直接覆盖安装的 APK。
**公开发布前请换成你自己单独的 release keystore，不要把私钥与口令提交进仓库。**

GitHub Actions 会自动编译并上传 APK 产物，见 `.github/workflows/`。

---

## 参数写入方式

```text
setParameter(5, [0x01000000][count][profile][paramId][values...])
count = values.size + 1
```

- GEQ 走参数 `110`（`graphic_equalizer_band_gains`）
- 编号体系来自 Dolby `com.dolby.dax.DsParams` 枚举（101~111 为 DAP1 参数，200+ 为 CPDP 编号）
- 所有写入都会 `commit` 到 Dolby 参数存储，开机后由 APP 重新下发一次

---

## 目录结构

```text
app/src/main/java/com/zero16/dolbytuner/
├── MainActivity.kt      # 入口、Gate、GEQ / 音效 / 设置三页、预设与弹窗
├── DapRealtime.kt       # DAP 会话、参数包组装、写入与提交
├── DapBandSlider.kt     # 20 段竖滑杆
├── BandCurveCanvas.kt   # 曲线画布与频率标签
├── Theme.kt             # 主题模式与配色
├── WallpaperState.kt    # 壁纸状态
├── BlurUtils.kt         # 壁纸模糊
└── GlassCard.kt         # 液态玻璃卡片
```

---

## 说明与免责

- 本项目只调用设备上已存在的杜比引擎，不包含任何 Dolby 私有库或商业音效资源。
- 参数编号与写入格式来自公开逆向资料（`DsParams` 枚举等）与真机探测，实现为本项目自写。
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