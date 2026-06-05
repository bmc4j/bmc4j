# Third-party notices

bmc4j redistributes compiled **CBMC / JBMC** binaries (and the accompanying
`core-models.jar`) inside its per-platform engine jars
(`org.bmc4j:bmc-engine-*`). Those binaries are built and released by the
[CBMC project](https://github.com/diffblue/cbmc) and are covered by the CBMC
license reproduced below, as required by its terms for binary redistribution.

Per clause 3 of that license, the following acknowledgement applies to bmc4j's
documentation and any materials mentioning features or use of the bundled
engine:

> This product includes software developed by Daniel Kroening, Edmund Clarke,
> Computer Science Department, University of Oxford, Computer Science
> Department, Carnegie Mellon University

The published `org.bmc4j:bmc-runtime` jar additionally **bundles** two libraries
it uses internally, **relocated** under `org.bmc4j.internal.shaded.*` (so they
never appear as POM dependencies and can't clash with a consumer's own copies):
**Gson** (Apache-2.0) and **ASM** (BSD-3-Clause). Their notices are reproduced
below.

bmc4j's own source code (everything in this repository) is licensed under the
[Apache License 2.0](LICENSE) and is **not** derived from CBMC sources — the
`org.cprover.CProver`/`CProverString` classes in `bmc-runtime` are stand-ins
written from scratch against the documented intrinsic API (JBMC recognises them
by name and substitutes its own semantics; the bodies are never analysed).

---

## CBMC

Source: https://github.com/diffblue/cbmc/blob/develop/LICENSE

```
(C) 2001-2016, Daniel Kroening, Edmund Clarke,
Computer Science Department, University of Oxford
Computer Science Department, Carnegie Mellon University

All rights reserved. Redistribution and use in source and binary forms, with
or without modification, are permitted provided that the following
conditions are met:

  1. Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.

  3. All advertising materials mentioning features or use of this software
     must display the following acknowledgement:

     This product includes software developed by Daniel Kroening,
     Edmund Clarke,
     Computer Science Department, University of Oxford
     Computer Science Department, Carnegie Mellon University

  4. Neither the name of the University nor the names of its contributors
     may be used to endorse or promote products derived from this software
     without specific prior written permission.


THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
```

## MiniSat

The stock CBMC release binaries bmc4j bundles embed the **MiniSat 2** SAT
solver, which is MIT-licensed and requires notice retention:

```
MiniSat -- Copyright (c) 2003-2006, Niklas Een, Niklas Sorensson
           Copyright (c) 2007-2010, Niklas Sorensson

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

Audit note: the CBMC 6.9.0 release packages bmc4j repackages (msi / deb /
Homebrew bottle) ship the engine binaries with MiniSat 2 as the built-in SAT
solver. If a future engine bump switches or adds a bundled solver (e.g.
CaDiCaL), its notice must be added here as part of the upgrade.

## Gson (bundled, relocated, in bmc-runtime)

`org.bmc4j:bmc-runtime` bundles **Gson 2.11.0** (`com.google.code.gson:gson`),
relocated to `org.bmc4j.internal.shaded.gson`, to parse JBMC's `--json-ui`
output. Gson is licensed under the Apache License 2.0.

```
Copyright 2008 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## ASM (bundled, relocated, in bmc-runtime)

`org.bmc4j:bmc-runtime` bundles **ASM 9.8** (`org.ow2.asm:asm`), relocated to
`org.bmc4j.internal.shaded.asm`, to rewrite bytecode before handing it to JBMC.
ASM is licensed under the BSD-3-Clause license.

```
ASM: a very small and fast Java bytecode manipulation framework
Copyright (c) 2000-2011 INRIA, France Telecom
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:
1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.
3. Neither the name of the copyright holders nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
```
