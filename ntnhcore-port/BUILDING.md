# Build corresponding source

Java 17, Minecraft 1.20.1, ForgeGradle 6, Gradle 8.8. No compiled third-party dependencies are bundled in this source archive.
1. Obtain the exact official mods listed in BUILD_DEPENDENCIES.json through CurseForge App. SHA-256 is mandatory; renamed files are found by content hash.
2. python prepare_build.py --mods-dir "path/to/CurseForge/profile/mods"
3. Set JAVA_HOME to Java 17; run gradlew.bat build (or --offline with a populated ForgeGradle cache).
4. Output: build/libs/ntnhcore-0.1.0-ntsd-cf4.jar.

Source includes code, resource inputs, per-component notices, models and JSON. No font pixels, old mixed Minecraft/TXLoader glyph data, Improving Minecraft pixel copies or RTF+ translation overlays are included. CF4 adds a book-only adapter that reads the player's installed Minecraft font providers and builds a temporary AWT font in memory. Existing game IDs, quest strings, NTNH recipes and the CF3 PNC repair remain unchanged. Scope details are in CF-CHANGES.md and Asset-scope.txt.
