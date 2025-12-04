#!/bin/bash

# ArgusOmni alias quraşdırıcı (sudo lazım deyil)

JAR_PATH="/Users/ismile/IdeaProjects/CoreScaffold/ArgusOmni-CLI/build/libs/ArgusOmni-CLI-1.0.0.jar"
ALIAS_NAME="argus"

# Yoxla JAR var mı
if [ ! -f "$JAR_PATH" ]; then
    echo "❌ JAR tapılmadı. Əvvəlcə build et:"
    echo "   ./gradlew build -x test"
    exit 1
fi

# Alias artıq var mı yoxla
if grep -q "alias $ALIAS_NAME=" ~/.zshrc 2>/dev/null; then
    echo "⚠️  Alias artıq var, update edirik..."
    # Köhnəni sil
    sed -i.bak "/$ALIAS_NAME=/d" ~/.zshrc
fi

# Yeni alias əlavə et
echo "alias $ALIAS_NAME='java -jar \"$JAR_PATH\"'" >> ~/.zshrc

echo "✅ Alias quraşdırıldı!"
echo ""
echo "Terminal-ı yenilə:"
echo "   source ~/.zshrc"
echo ""
echo "Və ya yeni terminal aç, sonra:"
echo "   $ALIAS_NAME --help"
