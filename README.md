# Evil CTM

Connected textures for **Cleanroom 1.12.2**, rendered through **Demonica's fast block renderer**.

Evil CTM reads OptiFine/MCPatcher CTM rules (`optifine/ctm` and `mcpatcher/ctm` `.properties` files), emissive `_e`
textures and CTM-mod `.png.mcmeta` metadata. It applies them through Demonica's S20 `BlockQuadTransformer` API. It is a
port of [CleanContinuity](https://github.com/Q-Engineering-Source/CleanContinuity) onto Demonica. It adds the methods
CleanContinuity lacks and fixes about 15 of its bugs, such as the state-cache leak, biome filters that never matched,
CTM textures that never animated and reloads that were unsafe while chunks were building.

> **Status: alpha, not released.** It loads and renders in a ~400-mod pack (RotN), but most methods have not been
> compared with OptiFine in game yet. A licence question blocks any release (see [Release status](#release-status)).

## Requirements

| | |
|---|---|
| Loader | Cleanroom 1.12.2, 0.7.4-alpha or newer (built against 0.7.4-alpha) |
| Demonica | 0.9.0, **required** |
| Celeritas | The build your Demonica version accepts (Demonica's log names it) |
| Java | 25 (what Cleanroom 0.7 runs on) |
| Side | Client only. Servers do not need it. |

**Evil CTM turns on Demonica's fast block renderer.** That renderer is off by default, and Evil CTM has no other render
path. While Evil CTM is installed, it switches the renderer on at startup and greys out *Video Settings → Use Fast
Block Renderer* so that it cannot be switched off. Your `config/demonica-options.json` is not changed at startup. The
next time Demonica saves its settings, it writes `use_fast_block_renderer: true`, so the renderer stays on if you
remove Evil CTM later. Switch it off in Video Settings after removing Evil CTM if you want it off.

If the S20 API is missing or Demonica rejects the Celeritas jar, Evil CTM logs the reason and shows a warning in chat.
It does not fall back to the vanilla renderer.

### What never gets connected textures

Evil CTM does not hook the vanilla block renderer and does not wrap models. Anything Demonica does not send through
S20 is drawn without CTM:

- items, pistons, falling blocks and tile-entity renderers;
- blocks Demonica hands to the vanilla renderer, such as non-`MODEL` render types, Snow Real Magic layers,
  ArchitectureCraft blocks and blocks next to Component Model Hider hidden blocks.

## Features

**Methods**

- `ctm` / `glass`, `ctm_compact`
- `horizontal` / `bookshelf`, `vertical`, `horizontal+vertical` / `h+v`, `vertical+horizontal` / `v+h`
- `top`, `random`, `repeat`, `fixed`
- The overlay family: `overlay`, `overlay_ctm`, `overlay_random`, `overlay_repeat`, `overlay_fixed`,
  `overlay_horizontal`, `overlay_vertical`, `overlay_h+v` and `overlay_v+h`
- Multipass rule chains, for example `random` followed by `repeat`

**Rule properties**

- Matching: `matchBlocks` (block specs with properties and metadata), `matchTiles`, `metadata`, `faces`, `biomes`,
  `heights` / `minHeight` / `maxHeight` and `name`.
- Connection: `connect=block|tile|material|state`, `connectTiles`, `innerSeams`, `linked` and `symmetry`.
- Tiles: `tiles`, `weight`, `randomLoops`, `width` / `height`, `tintIndex` / `tintBlock`, `layer` and `renderPass`.
- Filename inference for `block_<name>.properties` and the old MCPatcher layout. Log, pillar and quartz axes are
  handled.

**Other formats**

- Emissive textures, using the suffix set in `optifine/emissive.properties`.
- CTM-mod `.png.mcmeta` metadata. See the [licence note](#release-status).
- Built-in default rules for glass, glass panes, bookshelves and sandstone. They come from Continuity's default pack
  and apply only while the vanilla textures are in use.

### Extra render layers

Overlays, emissive textures on solid blocks and CTM-mod layer rules can need a render layer the block does not
normally use. Evil CTM adds that layer to Celeritas' mesher, but only for the block states that need it. Turning off
`extra_layers` stops this. Emissive and CTM-mod quads then stay in the block's own layers, and an overlay aimed at a
layer the block does not use is not drawn. When a shader pack moves a block to another layer, Demonica's choice wins.

## Configuration

Edit `config/evilctm.json`, or open the config screen from the mod list. That screen also shows whether the render
path is working. All options default to `true`.

| Option | Effect |
|---|---|
| `connected_textures` | Apply `.properties` CTM rules from resource packs. |
| `emissive_textures` | Draw emissive `_e` textures at full brightness. |
| `ctm_mod_textures` | Read CTM-mod `.png.mcmeta` metadata. Reloads resources when changed. |
| `builtin_default_rules` | Use the built-in rules beneath resource packs. Reloads resources when changed. |
| `extra_layers` | Let blocks render in extra layers when a rule needs one. |
| `render_path_warnings` | Show a chat warning when Evil CTM cannot render. |

## Troubleshooting

**No connected textures at all.** Look for an `Evil CTM:` warning in chat or `latest.log`. The warning names the cause and how to fix it.

**A resource pack has no backing file.** Some mods register packs like this. Evil CTM skips them, and a pack that fails
to scan does not stop the others from loading.

## Building

```sh
./gradlew build
```

The build is CleanroomMC's [CleanroomModTemplate](https://github.com/CleanroomMC/CleanroomModTemplate) layout:
CleanroomGradle and Conventions, Cleanroom 0.7.4-alpha, Java 25. The first build is online: it resolves the Cleanroom
userdev from CleanroomMC's Maven, Demonica from the assets of its GitHub release, Celeritas from maven.outlands.top and
Fluidlogged API from Modrinth. After that `./gradlew build --offline` works.

- The output is `build/libs/evilctm-<version>.jar` (SRG names, the jar players install). `build/devlibs/` holds the
  MCP-named jar the dev client uses. The sources jar is next to the mod jar.
- The version comes from git tags (Conventions' Versioning). `./gradlew -q printVersion` shows it. Tag `vX.Y.Z` on
  `main` to release: the release workflow builds the jar and creates the GitHub release.
- `-PdemonicaJar=/path/to/Demonica-<version>.jar` compiles against a local Demonica build (its `build/libs` jar, SRG
  names) instead of the release. Keep `demonica_version` in `gradle.properties` on the release Demonica 0.9.0 pins
  Celeritas against.
- The JUnit tests run with `build`. They never load Demonica, Celeritas or the S20 bridge classes.
- `checkLicense` runs with `build`: every Java file needs the header from `HEADER`
  (`./gradlew applyLicenseHeader` adds it). Checkstyle and the FormatJ/ClearSkies formatters are off in
  `gradle.properties`; the tree is tab-indented and a reformat is a separate step.
- CI runs the Conventions build workflow on pushes to `main`, `develop/**` and `dev/**`.

### Testing in game

`runClient` starts a dev client with Demonica and a Celeritas dev jar. A raw Celeritas cannot run in dev because its
mixins have no refmap, so the dev jar is Demonica's `remapCeleritasForDev` output, and Demonica accepts it only through
the Celeritas pin its own build generates next to it. Both come from a built Demonica checkout, `../Demonica` by
default:

```sh
./gradlew runClient                              # Demonica checkout next door
./gradlew runClient -PdemonicaCheckout=/path/to/Demonica
```

`-PceleritasDevJar=<jar>` and `-PdemonicaPinDir=<dir>` name the two files one by one. The client runs Evil CTM from
its MCP jar and keeps Demonica's jar in `run/client/mods`, which the build owns (`syncDevMods`). Without a Celeritas
dev jar the client starts without Demonica and Evil CTM only reports its render-path warning, which is enough to check
that the Mixin config and the access transformer apply. The comment above the `cleanroom.runs` block in `build.gradle`
explains why each jar goes where it does.

For a real instance, copy the mod jar, Demonica and a Celeritas build Demonica accepts into `mods/`. Load a CTM
resource pack. Evil CTM turns on the fast block renderer itself.

These still need checking in game:
- how each method compares with OptiFine;
- whether overlays get their extra layer when Evil CTM's mesher mixin runs alongside Demonica's;
- the end textures on north/south-facing logs;
- the built-in default rules;
- a dedicated server starting without client classes.

The code that checks whether CTM can render reads Demonica internals that are not public API. Re-check it against
each new Demonica release.

## Project layout

| Package | Contents |
|---|---|
| `com.evilctm.client.compat.demonica` | S20 transformer, Demonica bridge and render-path checks |
| `com.evilctm.client.resource`, `.loader` | Resource-pack scanning, reload session and rule loaders |
| `com.evilctm.client.properties` | Parsing of `.properties` rules |
| `com.evilctm.client.processor` | Method implementations (simple, overlay and multipass) |
| `com.evilctm.client.ctm` | CTM-mod `.mcmeta` support |
| `com.evilctm.client.layer` | Extra render layer routing |
| `com.evilctm.client.mixin` | The only two mixins: Celeritas `ChunkBuilderMeshingTask` (extra layers) and `TextureMap` (emissive sprites) |
| `com.evilctm.api.client` | Extension points: loaders, quad processors and processing data keys |

## Release status

Evil CTM is not ready for release, for two reasons:

1. **Licence.** The CTM-mod connection logic (`CtmCtmLogic`, `CtmConnectionMap`, `CtmLogicBakery`) was transcribed
   from Chisel-Team's ConnectedTexturesMod. That code may be GPL-2.0, which cannot be distributed under LGPL-3.0-only.
   Before a release, one of these has to happen: the licence is confirmed compatible, Chisel-Team gives permission,
   the classes are rewritten clean-room, or CTM-mod format support is removed. The OptiFine format is not affected.
   `NOTICE.md` has the details.
2. **In-game checks.** The items under [Testing in game](#testing-in-game) are still open.

## License

LGPL-3.0-only. `LICENSE` holds the LGPL-3.0 text followed by the GPL-3.0 it supplements; `NOTICE.md` has the
derivation notices. Evil CTM is derived from CleanContinuity (DHJComical), NeoContinuity (Argon4W) and Continuity
(PepperCode1), all LGPL-3.0. The build follows CleanroomMC's CleanroomModTemplate.
