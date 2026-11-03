# NTSD CF2 candidate changes

Existing gameplay IDs, quests, NTNH recipes and progression are unchanged. Core version 0.1.0-ntsd-cf3.

- Unmodified OFL Press Start 2P replaces mixed legacy Minecraft/TXLoader font data. No Minecraft font override.
- Three flint textures, four carpet textures, old quest-book texture and fourteen uncertain icon pixel copies replaced with runtime Minecraft texture references. Legacy item IDs remain.
- Optional third-party language overlays removed; native Core Russian/English strings and quest descriptions are unchanged.
- Standalone PNC Recipe Fix integrated as the same Drone Interface JSON. A targeted RecipeManager mixin calls PncDroneRecipePatch before parsing, replacing only the exact broken PNC 6.0.23 JSON when PNC and CC:Tweaked are loaded. Custom, valid or removed recipes are untouched. Works independently of saved datapack order.
- Font/resource provenance notices and source license texts included. Component licenses remain separate.

Build: Java 17. Portable source package has BUILDING.md and checksum-verified compile dependencies; no compiled third-party mods are included. This draft is not published or approved by CurseForge.
