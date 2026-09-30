# Evil CTM notices

Evil CTM is licensed under the **GNU Lesser General Public License v3.0 only** (LGPL-3.0-only). The full text is in
`LICENSE`; the GNU General Public License v3.0 it supplements is in `COPYING`. Both files, and this notice, are
packed into the mod jar under `META-INF/licenses/evilctm/`. The sources jar is published alongside the mod jar.

## Derived works

Evil CTM is derived from:

- **CleanContinuity** by DHJComical and contributors, https://github.com/Q-Engineering-Source/CleanContinuity,
  LGPL-3.0. Most of the code under `com.evilctm` is a port of it.
- **NeoContinuity** by Argon4W, https://github.com/Argon4W/NeoContinuity, LGPL-3.0. The overlay, `ctm_compact` and
  `horizontal+vertical` code is ported from the `legacy/neoforge-26.1` copy of it that ships with CleanContinuity.
- **Continuity** by PepperCode1, https://github.com/PepperCode1/Continuity, LGPL-3.0.

The built-in CTM textures and rules (`assets/evilctm/optifine/ctm/default/**`) come from Continuity's default resource
pack (LGPL-3.0).

## ConnectedTexturesMod (CTM) logic

The CTM-mod "magic number" logic in `com.evilctm.client.ctm.CtmCtmLogic` (and its helpers `CtmConnectionMap` and
`CtmLogicBakery`) is transcribed from Chisel-Team's ConnectedTexturesMod `CTMLogic`
(https://github.com/Chisel-Team/ConnectedTexturesMod). Demonica's `docs/CTM_RESEARCH.md` records that project as
MIT-licensed. **Not re-verified:** no local copy of ConnectedTexturesMod's LICENSE file was found (the CTM jars on this
machine ship none), so the licence and the exact copyright line must be confirmed before release. Under the recorded
MIT licence the notice is:

Copyright (c) Chisel-Team and contributors (exact copyright line to be confirmed)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Build scripts

The Gradle build scripts come from kappa-maintainer's CleanroomModTemplate, under the MIT licence:

Copyright (c) 2025 kappa-maintainer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Compile-time references

Demonica, Celeritas and fluidlogged-api are compile-time references only. They are not distributed with Evil CTM.
