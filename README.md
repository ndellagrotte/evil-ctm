# Evil CTM

OptiFine/MCPatcher connected and overlay textures (`optifine/ctm` and `mcpatcher/ctm` `.properties` rules), emissive (`_e`)
textures and CTM-mod `.png.mcmeta` metadata for **Cleanroom 1.12.2**, rendered through **Demonica's S20 block quad
transformer**. A port of [CleanContinuity](https://github.com/Q-Engineering-Source/CleanContinuity) onto Demonica.

## Requirements

- Cleanroom 1.12.2 with **Demonica** (built against 0.6.0) and the Celeritas build Demonica pins
  (`org.embeddedt:celeritas-forge-mc12.2:2.5.0-autobuild.9b661b70`).
- Demonica's **fast block renderer** switched on: *Video Settings → Use Fast Block Renderer*, or
  `"performance": { "use_fast_block_renderer": true }` in `config/demonica-options.json`. It is off by default.
  Evil CTM only renders on that path; when it is off (or Demonica rejects the Celeritas jar) Evil CTM logs a warning
  and prints one in chat.
- Client only. Servers do not need it.
- Java 21 or newer (the same as Demonica).

Blocks Demonica sends to the vanilla renderer (non-`MODEL` render types, Snow Real Magic layers, ArchitectureCraft,
blocks next to Component Model Hider hidden blocks), pistons, falling blocks, items and TESRs never get CTM. Evil CTM
has no hook in the vanilla block renderer and does not wrap models.

## Supported methods

- `ctm`/`glass`, `ctm_compact`, `horizontal`/`bookshelf`, `vertical`, `horizontal+vertical`/`h+v`,
  `vertical+horizontal`/`v+h`, `top`, `random`, `repeat` and `fixed`.
- The overlay family: `overlay`, `overlay_ctm`, `overlay_random`, `overlay_repeat`, `overlay_fixed`,
  `overlay_horizontal`, `overlay_vertical`, `overlay_horizontal+vertical`/`overlay_h+v` and
  `overlay_vertical+horizontal`/`overlay_v+h`.
- Multipass rule chains (for example `random` then `repeat`).
- Rules from both `optifine/ctm` and `mcpatcher/ctm`.
- CTM-mod `.png.mcmeta` metadata.
- Emissive textures, using the suffix from `optifine/emissive.properties`.

Overlays, emissive textures on solid blocks and CTM-mod layer rules can need a render layer that the block does not
normally use. Evil CTM adds that layer to Celeritas' mesher for the affected block states. The `extra_layers` option
switches this off. Emissive and CTM-mod layer quads then stay in the block's own layers, and overlays aimed at a layer
the block does not use are not drawn. Under a shader pack that moves the block to a different layer, Demonica's choice
of layer wins.

Built-in default rules for glass, glass panes, bookshelves and sandstone ship inside the mod and only apply while the
vanilla textures are in use. They can be switched off with `builtin_default_rules` in `config/evilctm.json`.

## Configuration

`config/evilctm.json` (also editable from the mod list): `connected_textures`, `emissive_textures`,
`ctm_mod_textures`, `builtin_default_rules`, `extra_layers`, `render_path_warnings`.

## Building

```
./gradlew --offline build -Pdemonica_jar=/absolute/path/to/Demonica-0.6.0-SNAPSHOT.jar
```

`demonica_jar` defaults to `../Demonica/build/libs/Demonica-0.6.0-SNAPSHOT.jar`. The jar is put on the compile
classpath unremapped (`demonica_unremapped=true` in `gradle.properties`; see the comment there). If the Celeritas or
fluidlogged-api coordinates do not resolve offline, pass `-Pceleritas_jar=<jar>` / `-Pfluidlogged_jar=<jar>`.

## Testing in game

`runClient` does not work: FML requires Demonica, which is not a runtime dependency of this project. Copy
`build/libs/evilctm-0.1.0.jar`, the Demonica jar and the pinned Celeritas jar into a real Cleanroom instance's `mods/`
folder, switch on the fast block renderer, and load a CTM resource pack.

## Release status

Not ready for release. Rendering has not been checked in game yet, and the licence of the CTM-mod connection logic
(`CtmCtmLogic`, `CtmConnectionMap`, `CtmLogicBakery`) is unresolved. See `NOTICE.md`.

## License

LGPL-3.0-only. See `LICENSE`, `COPYING` and `NOTICE.md` for the full notices and credits.
