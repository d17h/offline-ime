# 纯离线Android输入法安全与权限审计报告

## 文档信息

| 项目 | 内容 |
|------|------|
| 审计对象 | 纯离线Android输入法应用 |
| 审计版本 | v1.0 |
| 审计日期 | 2024年 |
| 审计范围 | 权限声明、网络安全、数据安全、隐私合规 |
| 审计原则 | 最小权限、零网络、本地优先 |

---

## 目录

1. [权限清单](#1-权限清单)
2. [离线验证方案](#2-离线验证方案)
3. [第三方SDK审查](#3-第三方sdk审查)
4. [数据安全](#4-数据安全)
5. [隐私合规](#5-隐私合规)
6. [代码审查清单](#6-代码审查清单)
7. [安全测试方案](#7-安全测试方案)
8. [风险清单](#8-风险清单)
9. [合规声明模板](#9-合规声明模板)

---

## 1. 权限清单

### 1.1 必需权限声明

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.offlineime">

    <!-- ==================== 必需权限 ==================== -->
    
    <!-- 输入法服务权限 - 必需 -->
    <!-- 用途：提供输入法服务，系统核心功能 -->
    <!-- 风险等级：低 - 系统标准权限 -->
    <uses-permission android:name="android.permission.BIND_INPUT_METHOD" />
    
    <!-- ==================== 可选权限 ==================== -->
    
    <!-- 录音权限 - 可选（语音输入功能） -->
    <!-- 用途：支持语音转文字输入 -->
    <!-- 风险等级：中 - 涉及敏感权限 -->
    <!-- 注意：应在运行时动态申请，提供开关选项 -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" 
        android:required="false" />
    
    <!-- 读取外部存储 - 可选（自定义皮肤） -->
    <!-- 用途：读取用户自定义皮肤文件 -->
    <!-- 风险等级：低 - 只读权限 -->
    <!-- 注意：Android 13+ 应使用 READ_MEDIA_IMAGES 替代 -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:required="false" />
    
    <!-- Android 13+ 媒体权限（替代 READ_EXTERNAL_STORAGE） -->
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES"
        android:required="false" />
    
    <!-- 振动权限 - 可选（按键反馈） -->
    <!-- 用途：提供按键振动反馈 -->
    <!-- 风险等级：低 -->
    <uses-permission android:name="android.permission.VIBRATE"
        android:required="false" />
    
    <!-- ==================== 禁止权限清单 ==================== -->
    <!-- 以下权限严禁声明： -->
    <!-- INTERNET - 网络访问 -->
    <!-- ACCESS_NETWORK_STATE - 网络状态 -->
    <!-- ACCESS_WIFI_STATE - WiFi状态 -->
    <!-- CHANGE_WIFI_STATE - 修改WiFi状态 -->
    <!-- BLUETOOTH - 蓝牙 -->
    <!-- BLUETOOTH_ADMIN - 蓝牙管理 -->
    <!-- NFC - 近场通信 -->
    <!-- SEND_SMS - 发送短信 -->
    <!-- CALL_PHONE - 拨打电话 -->
    <!-- READ_PHONE_STATE - 读取电话状态 -->
    <!-- ACCESS_FINE_LOCATION - 精确定位 -->
    <!-- ACCESS_COARSE_LOCATION - 粗略定位 -->
    
    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:usesCleartextTraffic="false"
        android:networkSecurityConfig="@xml/network_security_config">
        
        <!-- 输入法服务声明 -->
        <service
            android:name=".service.OfflineInputMethodService"
            android:permission="android.permission.BIND_INPUT_METHOD"
            android:exported="true">
            <intent-filter>
                <action android:name="android.view.InputMethod" />
            </intent-filter>
            <meta-data
                android:name="android.view.im"
                android:resource="@xml/method" />
        </service>
        
    </application>
</manifest>
```

### 1.2 权限详细说明表

| 权限名称 | 权限级别 | 用途 | 必需性 | 风险等级 | 备注 |
|---------|---------|------|--------|---------|------|
| BIND_INPUT_METHOD | 系统签名 | 输入法服务 | 必需 | 低 | 核心功能权限 |
| RECORD_AUDIO | 危险权限 | 语音输入 | 可选 | 中 | 运行时动态申请 |
| READ_EXTERNAL_STORAGE | 危险权限 | 读取皮肤 | 可选 | 低 | Android 13+ 需替换 |
| READ_MEDIA_IMAGES | 危险权限 | 读取图片 | 可选 | 低 | Android 13+ 使用 |
| VIBRATE | 正常权限 | 按键振动 | 可选 | 低 | 无需运行时申请 |

### 1.3 权限声明检查清单

```
□ 仅声明必需的 BIND_INPUT_METHOD 权限
□ 可选权限已标记 android:required="false"
□ 未声明任何网络相关权限
□ 未声明位置相关权限
□ 未声明电话相关权限
□ 未声明短信相关权限
□ 未声明蓝牙/NFC权限
□ 权限用途已在注释中说明
□ 危险权限有运行时申请逻辑
□ 权限使用符合最小必要原则
```

---

## 2. 离线验证方案

### 2.1 零网络请求验证方法

#### 2.1.1 静态代码分析

```bash
# 1. 检查AndroidManifest.xml中的网络权限
grep -E "INTERNET|NETWORK_STATE|WIFI_STATE" AndroidManifest.xml

# 2. 检查Java/Kotlin代码中的网络相关API
grep -r -E "(HttpURLConnection|OkHttp|Retrofit|Volley|HttpClient|URL\(|Socket|DatagramSocket)" \
  --include="*.java" --include="*.kt" src/

# 3. 检查网络相关导入语句
grep -r -E "import (java\.net\.|okhttp3|retrofit2|com\.android\.volley)" \
  --include="*.java" --include="*.kt" src/

# 4. 检查WebView使用（WebView可能产生网络请求）
grep -r "WebView" --include="*.java" --include="*.kt" --include="*.xml" src/ res/

# 5. 检查Firebase/Analytics等可能联网的库
grep -r -E "(Firebase|Analytics|Crashlytics|Ads)" --include="*.gradle" --include="*.xml" .
```

#### 2.1.2 网络权限检查脚本

```bash
#!/bin/bash
# network_permission_check.sh
# 纯离线应用网络权限检查脚本

echo "========== 网络权限检查开始 =========="

MANIFEST="AndroidManifest.xml"
VIOLATIONS=0

# 定义禁止的网络权限
FORBIDDEN_PERMISSIONS=(
    "android.permission.INTERNET"
    "android.permission.ACCESS_NETWORK_STATE"
    "android.permission.ACCESS_WIFI_STATE"
    "android.permission.CHANGE_WIFI_STATE"
    "android.permission.ACCESS_FINE_LOCATION"
    "android.permission.ACCESS_COARSE_LOCATION"
    "android.permission.BLUETOOTH"
    "android.permission.BLUETOOTH_ADMIN"
    "android.permission.NFC"
    "android.permission.SEND_SMS"
    "android.permission.READ_PHONE_STATE"
)

echo "检查禁止的网络权限..."
for permission in "${FORBIDDEN_PERMISSIONS[@]}"; do
    if grep -q "$permission" "$MANIFEST"; then
        echo "❌ 发现禁止权限: $permission"
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

if [ $VIOLATIONS -eq 0 ]; then
    echo "✅ 未发现禁止的网络权限"
else
    echo "❌ 发现 $VIOLATIONS 项违规权限"
    exit 1
fi

echo "========== 检查完成 =========="
```

### 2.2 抓包验证方案

#### 2.2.1 使用tcpdump进行网络监控

```bash
# 在设备上运行tcpdump监控网络流量
adb shell "tcpdump -i any -w /data/local/tmp/network.pcap" &

# 运行输入法应用，进行各种操作
# ... 测试操作 ...

# 停止抓包
adb shell "pkill tcpdump"

# 导出抓包文件
adb pull /data/local/tmp/network.pcap ./

# 使用Wireshark分析
echo "请使用Wireshark打开 network.pcap 分析是否有异常流量"
```

#### 2.2.2 使用Android Studio Profiler

```
步骤：
1. 打开Android Studio
2. 连接设备/启动模拟器
3. 选择 "Profiler" 标签
4. 选择网络监控 (Network Profiler)
5. 运行输入法应用
6. 观察网络活动图表
7. 预期结果：网络活动应为零或仅系统级流量
```

#### 2.2.3 防火墙阻断测试

```bash
# 使用iptables阻断应用所有网络访问（需要root）
APP_UID=$(adb shell "dumpsys package com.example.offlineime | grep userId" | awk '{print $1}')
adb shell "iptables -A OUTPUT -m owner --uid-owner $APP_UID -j DROP"

# 测试应用在断网环境下是否正常工作
# 如果应用功能正常，说明不依赖网络

# 测试完成后恢复
adb shell "iptables -F"
```

### 2.3 离线验证检查清单

```
□ 静态分析未发现网络权限声明
□ 代码审查未发现网络相关API调用
□ 抓包分析无应用产生的网络流量
□ 防火墙阻断后应用功能正常
□ 第三方库无隐藏网络行为
□ 日志中无网络请求记录
□ 离线状态下所有功能可用
□ 无后台网络同步任务
□ 无自动更新检查逻辑
□ 无遥测/分析数据上传
```

---

## 3. 第三方SDK审查

### 3.1 常见SDK网络行为分析

| SDK类别 | 常见SDK | 网络行为 | 离线适用性 | 建议 |
|---------|---------|---------|-----------|------|
| 统计分析 | Firebase Analytics | 实时上传 | ❌ 不适用 | 禁用或替换 |
| 崩溃报告 | Firebase Crashlytics | 上传崩溃日志 | ❌ 不适用 | 本地日志替代 |
| 广告SDK | AdMob, Unity Ads | 加载广告 | ❌ 不适用 | 完全禁用 |
| 推送服务 | FCM, JPush | 保持连接 | ❌ 不适用 | 禁用 |
| 社交分享 | 微信SDK, QQ SDK | 分享时联网 | ❌ 不适用 | 禁用 |
| 支付SDK | 支付宝, 微信支付 | 支付联网 | ❌ 不适用 | 禁用 |
| 地图服务 | 高德, 百度地图 | 加载地图 | ❌ 不适用 | 禁用 |
| OCR识别 | 百度OCR, 腾讯OCR | 云端识别 | ❌ 不适用 | 本地OCR替代 |

### 3.2 SDK选择建议

#### 推荐使用的离线SDK

| 功能需求 | 推荐方案 | 说明 |
|---------|---------|------|
| 统计分析 | 本地日志 + 导出功能 | 用户自主选择是否导出 |
| 崩溃报告 | 本地异常捕获 | ACRA (本地模式) |
| 日志记录 | Timber + 本地文件 | 纯本地存储 |
| 数据库 | Room / SQLite | 纯本地数据库 |
| 图片加载 | Glide (禁用网络) | 仅加载本地资源 |
| 依赖注入 | Hilt / Koin | 无网络需求 |
| JSON解析 | Gson / Moshi | 纯本地处理 |
| 加密库 | BouncyCastle / Tink | 本地加密运算 |

### 3.3 SDK权限审查方法

```bash
# 1. 检查Gradle依赖中的SDK
./gradlew dependencies > dependencies.txt

# 2. 分析SDK的权限需求
# 解压SDK的AAR文件，检查AndroidManifest.xml
unzip -p sdk.aar AndroidManifest.xml | grep "uses-permission"

# 3. 检查SDK的网络活动
# 使用jadx反编译SDK，搜索网络相关代码
jadx -d sdk_source sdk.aar
grep -r "HttpURLConnection\|OkHttp" sdk_source/

# 4. 检查SDK的隐私政策
# 访问SDK官方文档，确认数据收集行为
```

### 3.4 SDK审查检查清单

```
□ 列出所有第三方SDK依赖
□ 检查每个SDK的权限声明
□ 检查每个SDK的网络行为
□ 确认SDK的数据收集政策
□ 验证SDK的离线可用性
□ 检查SDK是否有开源替代方案
□ 确认SDK符合隐私法规
□ 检查SDK的安全更新状态
□ 评估SDK的维护活跃度
□ 确认SDK无后门/恶意代码
```

---

## 4. 数据安全

### 4.1 本地数据加密方案

#### 4.1.1 密钥管理架构

```kotlin
/**
 * 密钥管理器 - 纯离线输入法
 * 使用Android Keystore系统保护密钥
 */
class OfflineKeyManager(context: Context) {
    
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    
    companion object {
        const val KEY_ALIAS_MASTER = "offline_ime_master_key"
        const val KEY_ALIAS_DATA = "offline_ime_data_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
    
    /**
     * 生成或获取主密钥
     * 使用AES-256-GCM算法
     */
    fun getMasterKey(): SecretKey {
        return if (keyStore.containsAlias(KEY_ALIAS_MASTER)) {
            keyStore.getEntry(KEY_ALIAS_MASTER, null) as KeyStore.SecretKeyEntry
        } else {
            generateMasterKey()
        }.secretKey
    }
    
    private fun generateMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS_MASTER,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // 输入法需要在后台运行
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
```

#### 4.1.2 数据加密实现

```kotlin
/**
 * 数据加密管理器
 * 用于加密敏感用户数据
 */
class DataEncryptionManager(private val keyManager: OfflineKeyManager) {
    
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    
    /**
     * 加密敏感数据
     * @param plaintext 明文数据
     * @return Base64编码的密文
     */
    fun encrypt(plaintext: String): String {
        val secretKey = keyManager.getMasterKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // 组合IV和密文
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }
    
    /**
     * 解密数据
     * @param encrypted Base64编码的密文
     * @return 明文数据
     */
    fun decrypt(encrypted: String): String {
        val combined = Base64.decode(encrypted, Base64.DEFAULT)
        
        // 提取IV (GCM标准IV长度为12字节)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        
        val secretKey = keyManager.getMasterKey()
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
```

### 4.2 敏感数据保护

#### 4.2.1 需要加密的数据类型

| 数据类型 | 敏感等级 | 加密要求 | 存储位置 |
|---------|---------|---------|---------|
| 用户自定义词库 | 高 | 必须加密 | 内部存储 |
| 输入历史记录 | 高 | 必须加密 | 内部存储 |
| 语音输入缓存 | 高 | 必须加密 | 临时存储 |
| 用户设置 | 中 | 建议加密 | 内部存储 |
| 皮肤文件 | 低 | 可选加密 | 外部/内部存储 |
| 词频统计 | 中 | 建议加密 | 内部存储 |
| 剪贴板历史 | 高 | 必须加密 | 内部存储 |

#### 4.2.2 安全存储实现

```kotlin
/**
 * 安全存储管理器
 * 使用EncryptedSharedPreferences保护敏感配置
 */
class SecureStorageManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveSensitiveData(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }
    
    fun getSensitiveData(key: String): String? {
        return encryptedPrefs.getString(key, null)
    }
    
    fun removeSensitiveData(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }
    
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }
}
```

### 4.3 数据安全最佳实践

```
□ 所有敏感数据使用AES-256-GCM加密
□ 密钥存储在Android Keystore中
□ 不使用硬编码密钥
□ 敏感数据不写入外部存储
□ 临时文件使用后立即删除
□ 日志中不包含敏感信息
□ 应用卸载时清除所有数据
□ 提供数据导出/删除功能
□ 定期轮换加密密钥
□ 实施安全擦除（覆写而非简单删除）
```

---

## 5. 隐私合规

### 5.1 隐私政策要点

#### 5.1.1 纯离线输入法隐私政策框架

```markdown
# 隐私政策

## 1. 数据收集声明

**我们郑重承诺：本输入法为纯离线应用，不收集、不上传任何用户数据。**

### 我们不收集的信息：
- ❌ 您的输入内容
- ❌ 您的输入习惯
- ❌ 您的个人信息
- ❌ 您的设备信息
- ❌ 您的位置信息
- ❌ 任何网络数据

### 本地处理的数据：
- ✅ 输入历史（仅本地存储，用于联想输入）
- ✅ 自定义词库（仅本地存储）
- ✅ 应用设置（仅本地存储）

## 2. 权限使用说明

| 权限 | 用途 | 是否必需 |
|------|------|---------|
| 输入法服务 | 提供输入功能 | 是 |
| 录音 | 语音输入（可选） | 否 |
| 读取存储 | 自定义皮肤（可选） | 否 |
| 振动 | 按键反馈（可选） | 否 |

## 3. 数据存储

所有数据均存储在您的设备本地：
- 数据位置：应用私有目录
- 数据加密：使用设备级加密
- 数据控制：完全由您控制

## 4. 用户权利

您拥有以下权利：
- 查看本地存储的数据
- 导出您的数据
- 删除所有数据
- 随时卸载应用

## 5. 联系我们

如有隐私相关问题，请联系：privacy@example.com
```

### 5.2 GDPR/国内法规合规

#### 5.2.1 GDPR合规检查清单

```
□ 数据最小化原则 - 仅收集必要数据
□ 目的限制 - 数据仅用于声明目的
□ 存储限制 - 数据不长期保留
□ 数据准确性 - 允许用户更正数据
□ 完整性和保密性 - 数据加密存储
□ 合法性、公平性和透明性 - 隐私政策清晰
□ 用户权利保障：
  □ 知情权 - 明确告知数据处理
  □ 访问权 - 允许查看个人数据
  □ 更正权 - 允许修改个人数据
  □ 删除权 - 允许删除个人数据
  □ 限制处理权 - 允许限制数据处理
  □ 数据可携带权 - 允许导出数据
  □ 反对权 - 允许反对数据处理
```

#### 5.2.2 国内法规合规（个人信息保护法）

```
□ 处理个人信息有明确合理目的
□ 限于实现处理目的的最小范围
□ 公开个人信息处理规则
□ 保证个人信息质量
□ 采取安全措施保护信息
□ 个人有权查阅、复制个人信息
□ 个人有权请求更正、补充信息
□ 个人有权请求删除信息
□ 个人有权撤回同意
□ 敏感个人信息有更强保护措施
```

### 5.3 用户同意机制

```kotlin
/**
 * 隐私同意管理器
 */
class PrivacyConsentManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("privacy", Context.MODE_PRIVATE)
    
    companion object {
        const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
        const val KEY_PRIVACY_VERSION = "privacy_version"
        const val CURRENT_PRIVACY_VERSION = 1
    }
    
    /**
     * 检查是否需要显示隐私政策
     */
    fun shouldShowPrivacyPolicy(): Boolean {
        val accepted = prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)
        val version = prefs.getInt(KEY_PRIVACY_VERSION, 0)
        return !accepted || version < CURRENT_PRIVACY_VERSION
    }
    
    /**
     * 记录用户同意
     */
    fun recordConsent() {
        prefs.edit()
            .putBoolean(KEY_PRIVACY_ACCEPTED, true)
            .putInt(KEY_PRIVACY_VERSION, CURRENT_PRIVACY_VERSION)
            .apply()
    }
    
    /**
     * 撤回同意
     */
    fun withdrawConsent() {
        prefs.edit()
            .putBoolean(KEY_PRIVACY_ACCEPTED, false)
            .apply()
        // 清除所有用户数据
        clearUserData()
    }
    
    private fun clearUserData() {
        // 清除词库、设置等用户数据
    }
}
```

---

## 6. 代码审查清单

### 6.1 网络相关API检查

```
□ 检查java.net包使用
  □ URL / URLConnection
  □ HttpURLConnection
  □ Socket / ServerSocket
  □ DatagramSocket
  □ InetAddress

□ 检查第三方网络库
  □ OkHttp / Retrofit
  □ Volley
  □ Apache HttpClient
  □ Glide/Fresco网络加载

□ 检查Web组件
  □ WebView
  □ WebSettings
  □ WebViewClient
  □ JavaScriptInterface

□ 检查云服务
  □ Firebase相关API
  □ 阿里云/腾讯云SDK
  □ 第三方统计SDK

□ 检查后台任务
  □ WorkManager网络约束
  □ JobScheduler网络条件
  □ SyncAdapter
```

### 6.2 权限使用检查

```kotlin
/**
 * 权限使用审查检查点
 */
object PermissionAuditChecklist {
    
    // 检查点1：运行时权限申请
    fun checkRuntimePermissions(activity: Activity) {
        // 仅RECORD_AUDIO需要运行时申请
        // BIND_INPUT_METHOD不需要运行时申请
        // READ_EXTERNAL_STORAGE需要运行时申请（Android 6.0+）
    }
    
    // 检查点2：权限使用合理性
    fun checkPermissionRationale() {
        // 每个权限都有明确的用途说明
        // 权限使用符合最小必要原则
        // 可选权限可以关闭
    }
    
    // 检查点3：权限撤销处理
    fun checkPermissionRevocation() {
        // 权限被撤销时功能优雅降级
        // 提示用户权限用途
        // 提供重新授权入口
    }
}
```

### 6.3 数据传输检查

```
□ 检查Intent传输数据
  □ 敏感数据不通过Intent传输
  □ 使用LocalBroadcastManager替代全局广播
  
□ 检查ContentProvider
  □ 权限控制访问
  □ 不暴露敏感数据
  
□ 检查文件共享
  □ 使用FileProvider
  □ 临时权限授予
  
□ 检查日志输出
  □ 生产环境关闭详细日志
  □ 日志中不包含敏感信息
```

### 6.4 日志安全检查

```kotlin
/**
 * 安全日志管理器
 * 防止敏感信息泄露到日志
 */
object SecureLogger {
    
    private const val TAG = "OfflineIME"
    private var isDebugMode = false
    
    fun init(debugMode: Boolean) {
        isDebugMode = debugMode
    }
    
    fun d(message: String) {
        if (isDebugMode) {
            Log.d(TAG, sanitizeLog(message))
        }
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        // 错误日志可以记录，但需要脱敏
        Log.e(TAG, sanitizeLog(message), throwable)
    }
    
    /**
     * 日志脱敏处理
     */
    private fun sanitizeLog(message: String): String {
        return message
            .replace(Regex("[\u4e00-\u9fa5]{2,}"), "**") // 中文脱敏
            .replace(Regex("\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b"), "***@***.***") // 邮箱脱敏
            .replace(Regex("\b\d{11}\b"), "***********") // 手机号脱敏
    }
    
    /**
     * 禁止记录敏感信息
     */
    fun warnSensitiveLoggingAttempt() {
        if (isDebugMode) {
            Log.w(TAG, "Warning: Attempted to log potentially sensitive information")
        }
    }
}
```

---

## 7. 安全测试方案

### 7.1 静态分析工具

#### 7.1.1 推荐工具清单

| 工具名称 | 用途 | 使用方法 |
|---------|------|---------|
| MobSF | 移动安全框架 | 上传APK自动分析 |
| QARK | 静态安全分析 | 命令行扫描代码 |
| AndroBugs | 漏洞扫描 | Python脚本分析 |
| SonarQube | 代码质量 | 集成CI/CD |
| Lint | 基础检查 | Android Studio内置 |

#### 7.1.2 MobSF使用指南

```bash
# 安装MobSF
git clone https://github.com/MobSF/Mobile-Security-Framework-MobSF.git
cd Mobile-Security-Framework-MobSF
./setup.sh

# 启动服务
./run.sh

# 访问 http://localhost:8000
# 上传APK文件进行扫描

# 重点关注报告中的：
# - 权限分析
# - 网络安全配置
# - 敏感信息泄露
# - 不安全代码模式
```

### 7.2 动态测试方法

#### 7.2.1 运行时权限测试

```kotlin
/**
 * 权限测试用例
 */
class PermissionTest {
    
    @Test
    fun testRecordAudioPermission() {
        // 测试录音权限申请流程
        // 验证权限被拒绝时功能降级
        // 验证权限被授予时功能正常
    }
    
    @Test
    fun testStoragePermission() {
        // 测试存储权限申请
        // 验证无权限时的默认行为
        // 验证权限撤销后的处理
    }
}
```

#### 7.2.2 离线功能测试

```
测试场景：
1. 飞行模式下测试所有功能
2. 关闭WiFi和移动数据测试
3. 使用防火墙阻断应用网络
4. 长时间离线运行稳定性测试
5. 离线状态下数据持久化测试

预期结果：
- 所有核心功能正常工作
- 无网络请求产生
- 数据正确保存和读取
- 无异常崩溃或ANR
```

### 7.3 渗透测试要点

```
□ 反编译分析
  □ 使用jadx反编译APK
  □ 检查硬编码密钥
  □ 检查敏感信息泄露
  
□ 存储安全测试
  □ 检查SharedPreferences
  □ 检查SQLite数据库
  □ 检查外部存储文件
  □ 检查缓存文件
  
□ 组件安全测试
  □ 检查Activity导出
  □ 检查Service导出
  □ 检查BroadcastReceiver
  □ 检查ContentProvider
  
□ 日志安全测试
  □ 检查logcat输出
  □ 检查崩溃日志
  □ 检查调试信息
```

### 7.4 安全审计流程

```
阶段1：准备
  □ 确定审计范围
  □ 收集应用信息
  □ 准备测试环境

阶段2：静态分析
  □ 代码审查
  □ 权限分析
  □ 依赖分析
  □ 配置检查

阶段3：动态测试
  □ 功能测试
  □ 权限测试
  □ 离线测试
  □ 抓包分析

阶段4：渗透测试
  □ 反编译分析
  □ 存储分析
  □ 组件分析
  □ 日志分析

阶段5：报告
  □ 汇总发现
  □ 风险评估
  □ 修复建议
  □ 复测验证
```

---

## 8. 风险清单

### 8.1 潜在安全风险

| 风险编号 | 风险描述 | 风险等级 | 影响范围 | 检测方法 |
|---------|---------|---------|---------|---------|
| R001 | 第三方SDK隐藏网络请求 | 高 | 用户数据泄露 | 抓包分析 |
| R002 | 日志泄露敏感信息 | 中 | 隐私泄露 | 日志审查 |
| R003 | 本地数据未加密 | 高 | 数据被窃取 | 存储分析 |
| R004 | 硬编码密钥 | 高 | 加密失效 | 代码审查 |
| R005 | 权限过度申请 | 中 | 权限滥用 | 权限分析 |
| R006 | 组件导出风险 | 中 | 恶意调用 | 配置检查 |
| R007 | 剪贴板敏感数据 | 中 | 数据泄露 | 功能测试 |
| R008 | 输入历史恢复 | 低 | 隐私泄露 | 数据测试 |
| R009 | 语音数据残留 | 中 | 隐私泄露 | 存储分析 |
| R010 | 皮肤文件恶意代码 | 中 | 代码执行 | 文件扫描 |

### 8.2 风险等级评估

```
高风险（必须修复）：
- 任何网络数据上传
- 敏感数据未加密
- 硬编码加密密钥
- 第三方SDK恶意行为

中风险（建议修复）：
- 日志泄露敏感信息
- 权限申请过多
- 组件导出配置不当
- 临时文件未清理

低风险（可选修复）：
- 非敏感信息泄露
- 轻微权限冗余
- 日志信息过多
```

### 8.3 缓解措施

```
R001 第三方SDK隐藏网络请求：
  缓解措施：
  1. 严格审查所有SDK
  2. 使用抓包工具验证
  3. 优先选择开源SDK
  4. 定期更新SDK版本

R002 日志泄露敏感信息：
  缓解措施：
  1. 生产环境关闭调试日志
  2. 实现日志脱敏
  3. 代码审查日志输出
  4. 使用ProGuard移除日志

R003 本地数据未加密：
  缓解措施：
  1. 使用EncryptedSharedPreferences
  2. SQLCipher加密数据库
  3. 文件级加密存储
  4. 定期密钥轮换

R004 硬编码密钥：
  缓解措施：
  1. 使用Android Keystore
  2. 密钥派生而非硬编码
  3. 代码混淆保护
  4. 安全审计检查
```

### 8.4 监控建议

```
□ 定期安全审计（每季度）
□ SDK版本更新监控
□ 权限变更监控
□ 网络行为监控
□ 用户反馈监控
□ 漏洞公告订阅
□ 依赖安全扫描
□ 代码变更审查
```

---

## 9. 合规声明模板

### 9.1 隐私政策模板

```markdown
# 隐私政策

最后更新日期：2024年XX月XX日

## 引言

本隐私政策适用于【输入法应用名称】（以下简称"本应用"）。
我们高度重视您的隐私保护，本应用为纯离线输入法，
所有数据均在您的设备本地处理，不会上传至任何服务器。

## 1. 我们收集的信息

**本应用不收集任何个人信息。**

我们仅在您的设备本地处理以下数据：
- 输入历史（用于智能联想，仅本地存储）
- 自定义词库（您主动添加的词汇）
- 应用设置（您的个性化配置）

## 2. 我们使用的权限

| 权限 | 用途 | 是否必需 |
|------|------|---------|
| 输入法服务 | 提供输入功能 | 是 |
| 录音 | 语音输入功能 | 否 |
| 读取存储 | 导入自定义皮肤 | 否 |
| 振动 | 按键振动反馈 | 否 |

## 3. 数据安全

- 所有敏感数据均使用AES-256加密存储
- 加密密钥由Android系统安全保管
- 数据仅存储在应用私有目录
- 应用卸载时所有数据将被删除

## 4. 您的权利

- **查看权**：您可以查看本地存储的所有数据
- **导出权**：您可以导出您的词库和设置
- **删除权**：您可以随时删除所有数据
- **卸载权**：卸载应用将清除所有数据

## 5. 第三方服务

本应用不使用任何第三方分析、广告或云服务。

## 6. 儿童隐私

本应用不面向13岁以下儿童，我们不会故意收集儿童信息。

## 7. 政策更新

我们可能会更新本隐私政策，更新后将在应用内提示。

## 8. 联系我们

如有任何问题，请联系：privacy@example.com
```

### 9.2 权限说明文档

```markdown
# 权限使用说明

## 必需权限

### 输入法服务权限
- **权限名称**：android.permission.BIND_INPUT_METHOD
- **使用目的**：提供系统输入法服务
- **使用场景**：所有输入场景
- **数据访问**：仅访问输入框内容
- **数据处理**：本地处理，不上传
- **用户控制**：无法撤销（核心功能）

## 可选权限

### 录音权限
- **权限名称**：android.permission.RECORD_AUDIO
- **使用目的**：支持语音输入功能
- **使用场景**：用户主动点击语音输入按钮时
- **数据访问**：仅录制用户语音
- **数据处理**：本地识别，不上传
- **用户控制**：可在系统设置中关闭

### 存储权限
- **权限名称**：android.permission.READ_EXTERNAL_STORAGE
- **使用目的**：导入自定义皮肤图片
- **使用场景**：用户选择自定义皮肤时
- **数据访问**：仅访问用户选中的图片
- **数据处理**：本地读取，不上传
- **用户控制**：可在系统设置中关闭

### 振动权限
- **权限名称**：android.permission.VIBRATE
- **使用目的**：提供按键振动反馈
- **使用场景**：用户按键时
- **数据访问**：无
- **数据处理**：无
- **用户控制**：可在应用设置中关闭
```

### 9.3 用户协议要点

```markdown
# 用户协议

## 1. 服务说明

本应用为纯离线输入法，所有功能均在设备本地运行。

## 2. 用户责任

- 不得使用本应用进行违法活动
- 不得尝试破解或逆向工程
- 不得传播恶意皮肤文件

## 3. 免责声明

- 我们不对数据丢失承担责任（请定期备份）
- 语音输入准确率受环境因素影响
- 第三方皮肤文件风险由用户承担

## 4. 知识产权

- 应用代码受版权保护
- 用户词库归用户所有
- 默认词库受许可协议约束

## 5. 协议变更

我们保留修改本协议的权利，修改后将在应用内通知。

## 6. 终止

您可以随时卸载应用终止本协议。
```

---

## 附录

### A. 快速检查清单

```
□ 权限检查
  □ 仅声明必需权限
  □ 无网络权限
  □ 可选权限标记为required="false"

□ 代码检查
  □ 无网络相关API
  □ 无第三方网络SDK
  □ 敏感数据加密

□ 测试检查
  □ 离线功能正常
  □ 无网络流量
  □ 权限申请合理

□ 文档检查
  □ 隐私政策完整
  □ 权限说明清晰
  □ 用户协议合规
```

### B. 参考资源

- [Android权限最佳实践](https://developer.android.com/training/permissions)
- [Android数据安全指南](https://developer.android.com/topic/security/data)
- [GDPR合规指南](https://gdpr.eu/)
- [个人信息保护法](http://www.npc.gov.cn/)

---

**审计报告完成**

*本报告由Android安全与隐私专家生成，仅供参考。*
*实际部署前请进行完整的安全测试和合规审查。*
