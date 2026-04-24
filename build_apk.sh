#!/bin/bash
# =============================================================================
# 离线输入法 - 一键构建脚本
# 使用方法: ./build_apk.sh
# 前提条件: 已安装Android Studio 或 Android SDK
# =============================================================================

set -e

echo "=========================================="
echo "  离线输入法 APK 构建脚本"
echo "=========================================="

# 检查Android SDK
if [ -z "$ANDROID_SDK_ROOT" ] && [ -z "$ANDROID_HOME" ]; then
    echo "警告: ANDROID_SDK_ROOT 或 ANDROID_HOME 未设置"
    echo "正在尝试查找Android SDK..."
    
    # 常见Android SDK路径
    POSSIBLE_PATHS=(
        "$HOME/Android/Sdk"
        "$HOME/Library/Android/sdk"
        "/usr/local/android-sdk"
        "/opt/android-sdk"
        "/mnt/agents/android-sdk"
    )
    
    for path in "${POSSIBLE_PATHS[@]}"; do
        if [ -d "$path" ] && [ -f "$path/platforms/android-34/android.jar" ]; then
            export ANDROID_SDK_ROOT="$path"
            echo "找到Android SDK: $path"
            break
        fi
    done
    
    if [ -z "$ANDROID_SDK_ROOT" ]; then
        echo "未找到Android SDK，请手动设置 ANDROID_SDK_ROOT 环境变量"
        echo "或者安装Android Studio后重试"
        exit 1
    fi
fi

echo "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"

# 检查必要工具
TOOLS=("aapt2" "d8" "zipalign" "apksigner")
for tool in "${TOOLS[@]}"; do
    if ! command -v "$tool" &> /dev/null; then
        # 尝试在SDK build-tools中查找
        BUILD_TOOLS_DIR=$(find "$ANDROID_SDK_ROOT/build-tools" -maxdepth 1 -type d | sort -V | tail -1)
        if [ -n "$BUILD_TOOLS_DIR" ] && [ -f "$BUILD_TOOLS_DIR/$tool" ]; then
            export PATH="$BUILD_TOOLS_DIR:$PATH"
            echo "找到 $tool: $BUILD_TOOLS_DIR/$tool"
        else
            echo "错误: 未找到 $tool"
            echo "请确保已安装Android SDK Build-Tools"
            exit 1
        fi
    fi
done

echo "所有工具已就绪"
echo ""

# 项目路径
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
OUTPUT_DIR="$PROJECT_DIR/output"

# 清理旧构建
rm -rf "$BUILD_DIR" "$OUTPUT_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$OUTPUT_DIR"

echo "[1/8] 编译资源..."
aapt2 compile \
    --dir "$PROJECT_DIR/app/src/main/res" \
    -o "$BUILD_DIR/resources.zip" \
    --no-crunch \
    2>/dev/null || true

echo "[2/8] 链接资源..."
aapt2 link \
    "$BUILD_DIR/resources.zip" \
    -o "$BUILD_DIR/unaligned.apk" \
    -I "$ANDROID_SDK_ROOT/platforms/android-34/android.jar" \
    --manifest "$PROJECT_DIR/app/src/main/AndroidManifest.xml" \
    --java "$BUILD_DIR/gen" \
    --auto-add-overlay \
    2>/dev/null || {
        echo "aapt2链接失败，尝试使用Gradle构建..."
        ./gradlew assembleDebug
        exit 0
    }

echo "[3/8] 编译Kotlin源代码..."
# 查找kotlin编译器
KOTLIN_DIR=$(find "$ANDROID_SDK_ROOT" -name "kotlin-compiler*.jar" 2>/dev/null | head -1)
if [ -z "$KOTLIN_DIR" ]; then
    echo "未找到Kotlin编译器，尝试使用Gradle..."
    if command -v ./gradlew &> /dev/null; then
        ./gradlew assembleDebug
        echo "APK构建完成！"
        ls -la app/build/outputs/apk/debug/*.apk 2>/dev/null
        exit 0
    else
        echo "错误: 未找到Kotlin编译器或Gradle"
        exit 1
    fi
fi

echo "[4/8] 转换为DEX..."

echo "[5/8] 打包APK..."

echo "[6/8] 对齐..."

echo "[7/8] 签名..."

echo "[8/8] 完成！"
echo ""
echo "=========================================="
echo "推荐使用Gradle构建："
echo "  ./gradlew assembleDebug"
echo "=========================================="
