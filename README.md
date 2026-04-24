# 纯离线Android输入法

一款完全离线、零网络请求的Android输入法。所有功能本地化，充分保护用户隐私。

## 特性

| 功能 | 状态 |
|------|------|
| 拼音26键输入 | 本地词库10万+词条 |
| 拼音9键(T9)输入 | 本地T9映射 |
| 笔画输入 | 本地笔画索引 |
| 手写输入 | TensorFlow Lite轻量模型 |
| 语音输入 | Vosk离线引擎 |
| 超级符号系统 | 4000+符号，10分类 |
| 剪贴板管理 | SQLite加密存储 |
| 用户学习 | 本地词频记录 |
| 多语言支持 | 中日韩英 |
| 皮肤系统 | 5套内置+自定义 |

## 权限说明（仅3项）

| 权限 | 用途 | 必需 |
|------|------|------|
| BIND_INPUT_METHOD | 输入法服务 | 是 |
| RECORD_AUDIO | 语音输入 | 否 |
| READ_EXTERNAL_STORAGE | 自定义皮肤 | 否 |

**明确不请求任何网络权限**（INTERNET, NETWORK_STATE等）

## 内存控制

| 场景 | 内存占用 |
|------|----------|
| 基础运行 | 40-60MB |
| 符号面板 | +20MB |
| 语音输入 | +30MB |
| **峰值控制** | **< 100MB** |

## 构建方法

### 方法1: Android Studio（推荐）

1. 下载并安装 [Android Studio](https://developer.android.com/studio)
2. 打开项目文件夹 `OfflineIME/`
3. 等待Gradle同步完成
4. 点击 `Build > Build Bundle(s) / APK(s) > Build APK(s)`
5. APK将生成在 `app/build/outputs/apk/debug/` 或 `release/`

### 方法2: 命令行

```bash
cd OfflineIME
chmod +x gradlew
./gradlew assembleDebug    # 调试版
./gradlew assembleRelease  # 发布版
```

### 方法3: Docker

```bash
cd OfflineIME
docker build -t offline-ime .
docker create --name temp offline-ime
docker cp temp:/app/app/build/outputs/apk/release/app-release-unsigned.apk ./OfflineIME.apk
docker rm temp
```

### 方法4: GitHub Actions

Fork本项目到您的GitHub仓库，每次推送将自动构建APK。

也可手动触发：进入Actions标签页 > Build APK > Run workflow

构建的APK将出现在Artifacts中。

## 安装

1. 在Android设备上启用"未知来源"安装
2. 传输APK到设备
3. 点击安装
4. 打开系统设置 > 语言与输入法 > 启用"离线输入法"
5. 在输入框中切换输入法

## 技术架构

```
app/
├── src/main/java/com/offline/ime/
│   ├── core/           # 核心服务与内存管理
│   ├── engine/         # 词库引擎
│   ├── ui/             # 键盘UI
│   ├── symbol/         # 符号系统
│   ├── clipboard/      # 剪贴板
│   └── skin/           # 皮肤
├── src/main/res/       # 资源文件
├── src/main/assets/    # 词库数据
└── build.gradle        # 构建配置
```

## 技术方案文档

完整的技术方案请参见项目文档：
- [词库引擎设计](docs/ime_engine_design.md)
- [内存优化方案](docs/android_input_memory_optimization.md)
- [安全审计报告](docs/android_offline_ime_security_audit_report.md)
- [手写识别方案](docs/android_handwriting_recognition_tech_spec.md)
- [语音输入方案](docs/voice_input_module_technical_spec.md)
- [符号系统方案](docs/super_symbol_system_design.md)
- [剪贴板方案](docs/clipboard_hub_technical_spec.md)

## 隐私声明

本输入法完全离线运行，不收集、不上传任何用户数据。所有数据（词库、剪贴板、用户习惯）均存储在设备本地。
