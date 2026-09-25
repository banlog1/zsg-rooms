# Third-Party Model Sources

- DuncanRuns' `ravines.c`, CC0/public domain:
  https://gist.github.com/DuncanRuns/c458b56b50220bbaf9ac45d5deee6634/81a4f3fbf77ffc97845b2326e927f706cd7c4f21
  `mapless_model.h` adapts initialization and midpoint simulation, making RNG
  evaluation order explicit and omitting unused guesses and example programs.
  https://creativecommons.org/publicdomain/zero/1.0/
  This published C model is not proof of identity with ZSG's unpublished Zig
  dependency or of exact terrain carving. Buried-treasure loot/resource rules
  in the same header adapt the MIT ZSG sources pinned below.
- Cubiomes, Cubitect and contributors:
  https://github.com/Cubitect/cubiomes/tree/e61f90580cbdd883214a8054670dacae655e59c0
  MIT. Built from a pinned external checkout; its LICENSE is copied beside local binaries.
- Published ZSG source, DuncanRuns and contributors:
  https://github.com/DuncanRuns/ZigSeedGlitchless/tree/073e1d1f3150213c7c3787eda7ef8106cb789817
  `model_loot.h` adapts `src/filter_common.zig`; profile ordering/resources also
  follow the public 1.16 filter sources. `ZsgNetherChecks.java` adapts
  `ZSGJavaBits.java`, preserving its route sampling, selected chest rules and
  expected barter contribution. The unmodified helper is compiled externally
  as a test oracle, not shipped in the mod. The upstream README licenses its
  published `src` and `ZSGJavaBits` as MIT, not the proprietary binaries or
  unpublished components. None of those proprietary components are included.
- GoATS-filter, Copyright (c) 2023 AeroAstroid:
  https://github.com/AeroAstroid/GoATS-filter/tree/b329d026a57cb3ceec9ab0c4c1c4651ab0b6c066
  MIT. Lake decorator and initial ravine opportunity logic informed the C model.
- VillageGenerator, profotoce59, Solver3002, Jellejurre and credited contributors:
  https://github.com/profotoce59/VillageGenerator/tree/ee9e0c6c82aeac3fef9b1ffa3f452a2a1eae6339
  Its README documents layout and loot support, but this checkout has no explicit
  project license. It remains an external, operator-compiled dependency. Do not
  vendor its source or redistribute a bundled executable without resolving its
  licensing. The mod release does not contain it.
- BastionGenerator, Xinyuiii and contributors, ZSG's DuncanRuns fork:
  https://github.com/DuncanRuns/BastionGenerator/tree/9cccd19863e941d2dc8d2820037c48d49fe7d526
  The pinned checkout has no explicit project license. It remains an external,
  operator-compiled dependency on an isolated classpath. Do not vendor or
  redistribute its source/bundled binaries without resolving licensing. No mod
  release contains it.
- SeedFinding libraries: pinned Maven versions are in
  `../model-finder/build.gradle`; village and Nether classpaths are isolated,
  with Nether using ZSGJavaBits' exact pinned versions. Operator tools and tests
  only. Preserve/review upstream notices before binary redistribution.

## MIT Notice For Adapted MIT Sources

`portal_model.h` adapts the same ZSG `filter_common.zig` ruined-portal loot
simulation and biome/surface predicates. `PortalCompletionModel` calls the
existing SeedFinding layout/terrain APIs. `PortalLavaTemplates` contains only
lava source coordinate measurements from the 13 vanilla 1.16.1 templates, not
the NBT assets or Minecraft implementation. `PortalTemplateAudit` can reproduce
the measurement check against an operator-supplied game jar without loading
Minecraft classes. These are operator tools, not mod-jar dependencies.

Copyright (c) 2023 AeroAstroid

Copyright (c) 2024 DuncanRuns

ZigSeedGlitchless public source attribution: DuncanRuns and contributors.

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
