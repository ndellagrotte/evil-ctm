# Evil CTM notices

Evil CTM is licensed under the **GNU Lesser General Public License v3.0 only** (LGPL-3.0-only). `LICENSE` holds
the full text of the LGPL-3.0 followed by the GNU General Public License v3.0 it supplements. That file and this
notice are packed into the mod jar under `META-INF/licenses/evilctm/`. Every Java file carries the copyright and SPDX
header from `HEADER`. The sources jar is published alongside the mod jar.

## Derived works

Evil CTM is derived from:

- **CleanContinuity** by DHJComical and contributors, https://github.com/Q-Engineering-Source/CleanContinuity,
  LGPL-3.0. Most of the code under `com.evilctm` is a port of it.
- **NeoContinuity** by Argon4W, https://github.com/Argon4W/NeoContinuity, LGPL-3.0. The overlay, `ctm_compact` and
  `horizontal+vertical` code is ported from the `legacy/neoforge-26.1` copy of it that ships with CleanContinuity.
- **Continuity** by PepperCode1, https://github.com/PepperCode1/Continuity, LGPL-3.0.

The built-in CTM textures and rules (`assets/evilctm/optifine/ctm/default/**`) come from Continuity's default resource
pack (LGPL-3.0).

## ConnectedTexturesMod (CTM) logic: licence unresolved (release blocker)

The CTM-mod "magic number" logic in `com.evilctm.client.ctm.CtmCtmLogic` (and its helpers `CtmConnectionMap` and
`CtmLogicBakery`) is transcribed from Chisel-Team's ConnectedTexturesMod `CTMLogic`
(https://github.com/Chisel-Team/ConnectedTexturesMod). An earlier revision of this file recorded that project as
MIT-licensed on the strength of Demonica's `docs/CTM_RESEARCH.md`; that was never verified, and a review reports
ConnectedTexturesMod as **GPL-2.0**. GPL-2.0 code cannot be distributed under LGPL-3.0-only, so no MIT notice is given
here and **these three classes must not ship in a release** until one of the following is done:

- ConnectedTexturesMod's licence is confirmed from its own LICENSE file to permit LGPL-3.0-only distribution, or
- Chisel-Team grants permission to distribute this code under LGPL-3.0-only, or
- the connection-to-submap logic is reimplemented clean-room from documented behaviour (the CTM sheet layout and
  behaviour tests), by someone who has not read `CTMLogic`, and the "Portions transcribed" headers are dropped.

## Build scripts

The Gradle build (`build.gradle`, `settings.gradle`, the workflows and the files `extractConventions` writes) follows
CleanroomMC's [CleanroomModTemplate](https://github.com/CleanroomMC/CleanroomModTemplate), built on
[CleanroomGradle](https://github.com/CleanroomMC/CleanroomGradle) and
[Conventions](https://github.com/CleanroomMC/Conventions). No code from the earlier Unimined-based template remains.

## Compile-time references

Demonica, Celeritas and fluidlogged-api are compile-time references only. They are not distributed with Evil CTM.
