# Nuclear Tech: Second Dawn — Core

**0.1.0-ntsd-cf4 — BySally, Project Creator & Maintainer.**

Quest, progression and technology integrations for Nuclear Tech: Second Dawn on
Minecraft 1.20.1, Forge 47.4.28 and Java 17. Technical Mod ID: `ntnhcore`.

## Exact corresponding source

- [Versioned CF4 source tree](https://github.com/BySally/NTSD-Core/tree/v0.1.0-ntsd-cf4/ntnhcore-port)
- [Original CF4 corresponding-source ZIP](https://github.com/BySally/NTSD-Core/releases/download/v0.1.0-ntsd-cf4/ntnhcore-0.1.0-ntsd-cf4-corresponding-source.zip)
- [Build instructions](BUILDING.md) · [Licenses](LICENSES.md) · [Credits](CREDITS.md)

The `ntnhcore-port/` subtree contains all **1,358 files byte-for-byte** from the
corresponding-source archive used for the tested CF4 build. Its unmodified Gradle
wrapper and build-tool notices are included; dependency mod binaries and build
caches are excluded. [SOURCE_RELEASE.json](SOURCE_RELEASE.json) records the version
and digests. A prior clean Java 17 rebuild matched every class and resource in the
distributed JAR; only generated manifest build timestamps may differ.

```text
JAR SHA-256:    4a4919433050c87421eeacfef882d8c5e5cdd0e7f9880b8526724d497883a6c0
Source SHA-256: 6efac932c546dcbd89b14d5824ddf13ec6bf22bb600bdc667aded73b0a117307
```

CF4 restores the owner-approved thin questbook font A through a book-only adapter
which reads installed Minecraft font providers into memory. It includes no font
dataset and makes no global Minecraft font override. Existing game IDs, quests,
recipes and progression remain unchanged. The CF3 privacy cleanup and targeted
PneumaticCraft 6.0.23 Drone Interface repair are retained. This is not a complete
NTNH port or a guarantee of compatibility with every older world.

The [CF3 tag](https://github.com/BySally/NTSD-Core/tree/v0.1.0-ntsd-cf3) remains
available for the older binary; do not use CF3 sources as the CF4 source.

Part of [Nuclear Tech: Second Dawn](https://github.com/BySally/NTSD).
[Website](https://bysally.github.io/NTSD/) · [Issues](https://github.com/BySally/NTSD/issues).
NTSD is an independent community project, not an official Minecraft, Mojang, HBM
or original NTNH product. Upstream credit does not imply endorsement. Mixed code
and resource licenses remain in effect. NTSD artwork, where shown, was AI-generated
with OpenAI under BySally's direction; this does not establish exclusive copyright
in every generated element.
