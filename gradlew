#!/bin/sh
# Gradle wrapper script
# This script downloads and executes Gradle.

# Attempt to use system gradle or download gradle
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

# Try to download and use gradle wrapper
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
WRAPPER_JAR="$GRADLE_USER_HOME/wrapper/dists/gradle-8.2-bin/gradle-8.2-bin.zip"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Gradle 8.2..."
    mkdir -p "$(dirname "$WRADLE_JAR")"
    curl -L -o "$WRAPPER_JAR" "https://services.gradle.org/distributions/gradle-8.2-bin.zip" 2>/dev/null || {
        echo "Failed to download Gradle. Please install Gradle manually."
        echo "  apt install gradle    (Debian/Ubuntu)"
        echo "  brew install gradle   (macOS)"
        exit 1
    }
fi

# Fallback: try to find gradle in common locations
for path in /usr/bin/gradle /usr/local/bin/gradle /opt/gradle/bin/gradle; do
    if [ -x "$path" ]; then
        exec "$path" "$@"
    fi
done

echo "============================================================"
echo "  Gradle not found!"
echo "============================================================"
echo ""
echo "To build this project, you need to install:"
echo "  1. Android Studio (recommended)"
echo "     https://developer.android.com/studio"
echo ""
echo "  2. Or install command-line tools:"
echo "     - Android SDK"
echo "     - Kotlin plugin"
echo "     - Gradle"
echo ""
echo "Then build with:"
echo "  ./gradlew assembleDebug"
echo ""
echo "Or open this folder in Android Studio and click Build."
echo "============================================================"
exit 1
