# Docker构建方案 - 无需安装Android Studio
# 使用方法:
#   docker build -t offline-ime .
#   docker create --name temp offline-ime
#   docker cp temp:/app/app/build/outputs/apk/release/app-release-unsigned.apk ./
#   docker rm temp

FROM openjdk:17-jdk-slim

# 安装必要工具
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# 设置Android SDK
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# 下载并安装Android SDK命令行工具
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# 接受许可证并安装必要组件
RUN yes | sdkmanager --licenses && \
    sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

# 设置工作目录
WORKDIR /app

# 复制项目文件
COPY . /app

# 构建APK
RUN chmod +x gradlew && \
    ./gradlew assembleRelease --no-daemon -x lint

# 输出APK路径
RUN echo "APK构建完成！" && \
    ls -la /app/app/build/outputs/apk/release/
