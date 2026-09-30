#!/bin/bash
# Generates the Gradle wrapper files into the Volo project root.
# Usage: ./generate-gradle-wrapper.sh [gradle_version]
# Default uses Gradle 8.10.2.
set -euo pipefail

GRADLE_VERSION="${1:-8.10.2}"
ROOT_DIR="C:/Users/hp/Documents/my builds/lokkin for now"

mkdir -p "$ROOT_DIR/gradle/wrapper"

# gradle-wrapper.properties
cat > "$ROOT_DIR/gradle/wrapper/gradle-wrapper.properties" <<EOF
distributionUrl=https\://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

# gradlew (Unix)
cat > "$ROOT_DIR/gradlew" <<'EOF'
#!/bin/sh
exec gradle "$@"
EOF
chmod +x "$ROOT_DIR/gradlew" 2>/dev/null || true

# gradlew.bat (Windows)
cat > "$ROOT_DIR/gradlew.bat" <<'EOF'
@echo off
gradle %*
EOF

echo "Gradle wrapper for ${GRADLE_VERSION} written to $ROOT_DIR/gradle/wrapper/"
echo "Place gradle-wrapper.jar alongside the properties to execute offline."
