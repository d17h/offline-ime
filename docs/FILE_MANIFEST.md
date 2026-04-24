
# 剪贴板中枢技术方案 - 文件清单

## 文档文件

| 文件名 | 说明 |
|--------|------|
| `clipboard_hub_technical_spec.md` | 完整技术方案文档（主文档） |
| `build.gradle.clipboard.md` | Gradle依赖配置 |

## Kotlin源代码文件

### 数据层 (data/)

| 文件名 | 路径 | 说明 |
|--------|------|------|
| `ClipboardEntities.kt` | data/local/entity/ | Room实体定义 |
| `ClipboardDao.kt` | data/local/dao/ | 数据访问对象 |
| `ClipboardDatabase.kt` | data/local/ | Room数据库 |
| `EncryptionManager.kt` | data/security/ | 加密管理器 |
| `ClipboardRepository.kt` | data/repository/ | 仓库层实现 |

### 领域层 (domain/)

| 文件名 | 路径 | 说明 |
|--------|------|------|
| `TextExtractionEngine.kt` | domain/extraction/ | 智能拆词引擎 |
| `ClipboardCleanupManager.kt` | domain/cleanup/ | 清理管理器 |

### 工具类 (util/)

| 文件名 | 路径 | 说明 |
|--------|------|------|
| `MemoryMonitor.kt` | util/ | 内存监控与优化 |

### 展示层 (presentation/)

| 文件名 | 路径 | 说明 |
|--------|------|------|
| `ClipboardViewModel.kt` | presentation/ | ViewModel |
| `ClipboardUIComponents.kt` | presentation/components/ | UI组件 |

### 服务层 (service/)

| 文件名 | 路径 | 说明 |
|--------|------|------|
| `ClipboardMonitorService.kt` | service/ | 剪贴板监听服务 |

### 依赖注入 (di/)

| 文件名 | 路径 | 说明 |
|--------|------|------|
| `ClipboardModule.kt` | di/ | Hilt依赖注入模块 |

## 完整项目结构

```
app/src/main/java/com/ime/clipboard/
├── data/
│   ├── local/
│   │   ├── ClipboardDatabase.kt
│   │   ├── dao/
│   │   │   ├── ClipboardDao.kt
│   │   │   ├── ExtractionDao.kt
│   │   │   ├── FavoriteDao.kt
│   │   │   └── ConfigDao.kt
│   │   └── entity/
│   │       ├── ClipboardItemEntity.kt
│   │       ├── ClipboardExtractionEntity.kt
│   │       ├── ClipboardFavoriteEntity.kt
│   │       └── ClipboardConfigEntity.kt
│   ├── repository/
│   │   └── ClipboardRepository.kt
│   └── security/
│       ├── EncryptionManager.kt
│       └── FieldEncryption.kt
├── domain/
│   ├── extraction/
│   │   ├── TextExtractionEngine.kt
│   │   ├── AddressExtractor.kt
│   │   └── ExtractionPatterns.kt
│   └── cleanup/
│       └── ClipboardCleanupManager.kt
├── presentation/
│   ├── ClipboardViewModel.kt
│   └── components/
│       ├── ClipboardItemCard.kt
│       ├── ExtractionChip.kt
│       └── SearchBar.kt
├── service/
│   └── ClipboardMonitorService.kt
├── di/
│   └── ClipboardModule.kt
└── util/
    └── MemoryMonitor.kt
```

## 技术规格总结

### 数据库设计
- **表数量**: 4个（剪贴板表、拆词表、收藏表、配置表）
- **最大条目**: 200条
- **索引数量**: 10+个优化索引
- **加密方式**: SQLCipher AES-256

### 智能拆词
- **支持类型**: 12种（电话、网址、邮箱、地址、验证码等）
- **正则规则**: 15+条
- **识别精度**: 90%+
- **处理速度**: <10ms/条

### 性能指标
- **查询响应**: <50ms
- **内存占用**: <10MB
- **数据库大小**: <5MB（200条）
- **加密开销**: <5%

### 安全特性
- ✅ 数据加密存储（AES-256）
- ✅ 密钥硬件保护（Android Keystore）
- ✅ 完全本地，无网络上传
- ✅ 隐私数据自动识别

### 功能清单
- ✅ 剪贴板内容保存
- ✅ 智能拆词提取
- ✅ 搜索功能
- ✅ 编辑功能
- ✅ 删除功能
- ✅ 收藏功能
- ✅ 置顶功能
- ✅ 批量操作
- ✅ 自动清理
- ✅ 内存优化
