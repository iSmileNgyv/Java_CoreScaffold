#!/bin/bash

echo "📦 Building ArgusOmni CLI using GRADLE..."

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
ALIAS=$(grep -E "^[[:space:]]*alias:" src/main/resources/application.yml | sed 's/.*alias:[[:space:]]*//' | tr -d '"')

if [ -z "$ALIAS" ]; then
  echo "❌ ERROR: Cannot read cli.alias from application.yml"
  exit 1
fi

echo "🔧 CLI alias detected: $ALIAS"

# 5. Try to install to /usr/local/bin (needs sudo)
LAUNCHER="/usr/local/bin/$ALIAS"

echo ""
echo "🔐 Attempting to install to $LAUNCHER (requires sudo)..."

if sudo -n true 2>/dev/null; then
    # Sudo access available without password
    sudo bash -c "echo '#!/bin/bash' > \"$LAUNCHER\""
    sudo bash -c "echo 'exec java -jar \"$JAR_ABS_PATH\" \"\$@\"' >> \"$LAUNCHER\""
    sudo chmod +x "$LAUNCHER"
    echo "✅ Global installation successful!"
    INSTALL_METHOD="global"
else
    # Sudo needs password, try to ask
    echo "Enter your password to install globally (or press Ctrl+C to skip):"
    if sudo bash -c "echo '#!/bin/bash' > \"$LAUNCHER\"" 2>/dev/null; then
        sudo bash -c "echo 'exec java -jar \"$JAR_ABS_PATH\" \"\$@\"' >> \"$LAUNCHER\""
        sudo chmod +x "$LAUNCHER"
        echo "✅ Global installation successful!"
        INSTALL_METHOD="global"
    else
        echo "⚠️  Sudo access denied or cancelled"
        INSTALL_METHOD="alias"
    fi
fi

# 6. If global install failed, create alias instead
if [ "$INSTALL_METHOD" = "alias" ]; then
    echo ""
    echo "📌 Installing via alias (no sudo needed)..."

    # Remove old alias if exists
    if [ -f ~/.zshrc ]; then
        sed -i.bak "/alias $ALIAS=/d" ~/.zshrc 2>/dev/null
    fi

    # Add new alias
    echo "alias $ALIAS='java -jar \"$JAR_ABS_PATH\"'" >> ~/.zshrc

    echo "✅ Alias installed to ~/.zshrc"
    echo ""
    echo "⚡ Run this command to activate:"
    echo "   source ~/.zshrc"
fi

# 7. Ensure /usr/local/bin is in PATH
if [[ ":$PATH:" != *":/usr/local/bin:"* ]]; then
    echo ""
    echo "📎 Adding /usr/local/bin to PATH in ~/.zshrc"
    echo 'export PATH="/usr/local/bin:$PATH"' >> ~/.zshrc
fi

echo ""
echo "═══════════════════════════════════════════════════"
echo "🎉 INSTALLATION COMPLETE! ArgusOmni CLI is ready."
echo "═══════════════════════════════════════════════════"
echo ""
echo "👉 Usage examples:"
echo "   $ALIAS run tests/example-test.yml"
echo "   $ALIAS run tests/chronovcs-test.yml --verbose"
echo "   $ALIAS --help"
echo ""

if [ "$INSTALL_METHOD" = "alias" ]; then
    echo "⚡ IMPORTANT: Run this first:"
    echo "   source ~/.zshrc"
    echo ""
fi

echo "🚀 Try now: $ALIAS --help"
