#!/bin/bash

echo "📦 Building ChronoVCS CLI using GRADLE..."

# 1. Build CLI JAR via Gradle
./gradlew clean build -x test

# 2. Locate JAR file inside build/libs
JAR_FILE=$(find build/libs -maxdepth 1 -type f -name "*.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
  echo "❌ ERROR: Could not find JAR file in build/libs"
  exit 1
fi

echo "✔ JAR found: $JAR_FILE"

# 3. Convert JAR path to ABSOLUTE PATH
JAR_ABS_PATH="$(cd "$(dirname "$JAR_FILE")"; pwd)/$(basename "$JAR_FILE")"
echo "📍 Absolute JAR path: $JAR_ABS_PATH"

# 4. Extract alias from application.yml safely (handles indent, tabs, spaces)
ALIAS=$(grep -E "^[[:space:]]*alias:" src/main/resources/application.yml | sed 's/.*alias:[[:space:]]*//')

if [ -z "$ALIAS" ]; then
  echo "❌ ERROR: Cannot read cli.alias from application.yml"
  exit 1
fi

echo "🔧 CLI alias detected: $ALIAS"

# 5. Create launcher script in /usr/local/bin
LAUNCHER="/usr/local/bin/$ALIAS"

echo "🛠 Creating launcher at $LAUNCHER"

sudo bash -c "echo '#!/bin/bash' > \"$LAUNCHER\""
sudo bash -c "echo 'exec java -jar \"$JAR_ABS_PATH\" \"\$@\"' >> \"$LAUNCHER\""

# 6. Make it executable
sudo chmod +x "$LAUNCHER"

echo "✔ Launcher installed → $LAUNCHER"

# 7. Ensure /usr/local/bin is in PATH
if [[ ":$PATH:" != *":/usr/local/bin:"* ]]; then
    echo "📎 Adding /usr/local/bin to PATH in ~/.zshrc"
    echo 'export PATH="/usr/local/bin:$PATH"' >> ~/.zshrc
    source ~/.zshrc
fi

echo
echo "🎉 INSTALL COMPLETE! ChronoVCS CLI is ready."
echo
echo "👉 Usage examples:"
echo "   $ALIAS init"
echo "   $ALIAS commit"
echo "   $ALIAS push"
echo "   $ALIAS status"
echo
echo "🚀 Try now: $ALIAS init"