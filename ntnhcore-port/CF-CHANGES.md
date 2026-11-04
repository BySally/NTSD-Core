# NTSD Core CF5 candidate: Nether quartz generation fix

Version: **0.1.0-ntsd-cf5**, based on the exact CF4 corresponding source.

- Corrects only the `NetherQuartz` distribution's dimension from `minecraft:overworld` to `minecraft:the_nether` and its hosts to `minecraft:netherrack`.
- Leaves frequency, height, size, density, all other ore distributions and biome feature registration unchanged.
- Keeps native Nether quartz generation. The existing custom feature is not added to Nether biomes, avoiding duplicate quartz generation.
- No regeneration of saved chunks, no registry ID changes, no quest/recipe/font changes.
- CF4 binary and source remain preserved as historical references. CF5 requires its own corresponding-source publication and real CurseForge File ID before pack submission; never reuse CF4 File ID 9414966 for this binary.

## Retained CF4 font restoration and earlier fixes

Existing gameplay IDs, quests, NTNH recipes and progression are unchanged. Core version 0.1.0-ntsd-cf4. The following earlier CF2/CF3 privacy and resource fixes are retained.

- CF4 font review candidate: removes Press Start 2P. Better Questing's AWT font is generated in memory from the installed Minecraft 1.20.1 providers. No bundled legacy/Mojang glyph file and no global Minecraft font override. The result is a measured approximation, not an assertion of identical Cyrillic shapes.
- Three flint textures, four carpet textures, old quest-book texture and fourteen uncertain icon pixel copies replaced with runtime Minecraft texture references. Legacy item IDs remain.
- Optional third-party language overlays removed; native Core Russian/English strings and quest descriptions are unchanged.
- Standalone PNC Recipe Fix integrated as the same Drone Interface JSON. A targeted RecipeManager mixin calls PncDroneRecipePatch before parsing, replacing only the exact broken PNC 6.0.23 JSON when PNC and CC:Tweaked are loaded. Custom, valid or removed recipes are untouched. Works independently of saved datapack order.
- Font/resource provenance notices and source license texts included. Component licenses remain separate.

Build: Java 17. Portable source package has BUILDING.md and checksum-verified compile dependencies; no compiled third-party mods are included. This draft is not published or approved by CurseForge.
