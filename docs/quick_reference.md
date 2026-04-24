# 语音输入模块快速参考

## 1. 快速集成清单

### 1.1 Gradle依赖
```gradle
dependencies {
    implementation 'com.alphacephei:vosk-android:0.3.47'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 1.2 权限声明
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

### 1.3 模型下载地址
```
https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip
大小: 42MB
语言: 中文普通话
```

## 2. 核心API速查

### 2.1 初始化模型
```kotlin
val model = Model(modelDir.absolutePath)
```

### 2.2 创建识别器
```kotlin
val recognizer = Recognizer(model, 16000f)
recognizer.setPartialWords(true)
```

### 2.3 送入音频数据
```kotlin
val buffer: ByteArray = ... // PCM 16-bit数据
val hasResult = recognizer.acceptWaveForm(buffer, buffer.size)
```

### 2.4 获取结果
```kotlin
// 部分结果
val partial = recognizer.partialResult  // {"partial": "..."}

// 最终结果
val final = recognizer.finalResult      // {"text": "..."}
```

### 2.5 释放资源
```kotlin
recognizer.close()
model.close()
```

## 3. 音频录制配置

```kotlin
val SAMPLE_RATE = 16000
val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

val minBufferSize = AudioRecord.getMinBufferSize(
    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
)

val audioRecord = AudioRecord(
    MediaRecorder.AudioSource.VOICE_RECOGNITION,
    SAMPLE_RATE,
    CHANNEL_CONFIG,
    AUDIO_FORMAT,
    minBufferSize * 2
)
```

## 4. 内存预算参考

| 组件 | 大小 |
|------|------|
| Vosk模型 | 20-25 MB |
| 音频缓冲区 | 0.5 MB |
| 识别器 | 2-3 MB |
| UI组件 | 1-2 MB |
| **总计** | **26-34 MB** |

## 5. 常见问题

### Q: 模型加载失败?
A: 检查模型文件完整性，确保包含am/final.mdl等必需文件

### Q: 识别准确率低?
A: 确保采样率为16kHz，检查麦克风权限和音频质量

### Q: 内存占用过高?
A: 使用对象池，及时释放资源，启用懒加载

### Q: 识别延迟大?
A: 使用流式识别，减小音频分块大小，优化线程调度

## 6. 性能指标

| 指标 | 目标值 | 实际值 |
|------|--------|--------|
| 模型大小 | < 50MB | 42MB |
| 内存增量 | < 30MB | 26-34MB |
| 识别延迟 | < 500ms | < 200ms |
| 识别准确率 | > 90% | 90-93% |
