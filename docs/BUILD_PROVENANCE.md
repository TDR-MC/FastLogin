# FastLogin build inputs and provenance

This fork currently targets Minecraft/Paper 26.1.2, Velocity runtime build 605, and Java 25. The version and SHA-256
locks in [BUILD_INPUTS.json](BUILD_INPUTS.json) record the inputs used for this source build. They do not establish
Minecraft 26.3 runtime compatibility or physical AuthMe login acceptance.

## How the build gate works

1. Maven resolves the complete dependency graph to `*/target/dependency-tree.txt`.
2. `scripts/verify-build-inputs.py --resolve` creates a temporary Maven POM from `BUILD_INPUTS.json`, resolves only
   the locked JAR/POM inputs with transitive dependencies excluded, and then verifies them. This step does not compile
   FastLogin sources or execute its annotation processors.
3. The verifier compares each resolved graph with `docs/dependency-tree/*.txt`, verifies SHA-256
   for every resolved SNAPSHOT JAR and POM (both Maven's base alias and timestamped file), verifies the exact
   `sqlite-jdbc` 3.53.4.0 JAR/POM, and verifies the four vendored system-scope JARs.
4. The full Maven test/package phase runs only after the lock passes. The parent POM fixes the archive entry timestamp
   for this fork build; two clean source copies produced byte-identical Core, Bukkit, Bungee, and Velocity JARs under
   Java 25.0.3 and the same locked Maven repository. GitHub workflows pin each Action to a full commit
   SHA. Build and CodeQL jobs use read permissions and support a manual `workflow_dispatch` run on `main`.

The repository's GitHub Dependency Graph is disabled, so CI does not submit a dependency snapshot or request a
repository write token. The committed graph and checksum gate are the source-build evidence.

The lock closes the first-resolution gap left by `--no-snapshot-updates`: a fresh runner may resolve a different
SNAPSHOT, but it fails before tests or packaging. An intentional dependency update requires an explicit lock refresh,
source review, clean build, and compatibility checks. The checksum gate detects changed bytes, not malicious bytes that
were already present when the lock was created.

Run the same gate locally with Maven 3.9.11 and Java 25:

```sh
mvn dependency:tree -DoutputFile=target/dependency-tree.txt --batch-mode --no-snapshot-updates --strict-checksums
python3 scripts/verify-build-inputs.py --resolve
mvn package --batch-mode --no-snapshot-updates --strict-checksums
```

For an isolated Maven repository, add `-Dmaven.repo.local=/path/to/repo` to every Maven command and pass
`--maven-repo /path/to/repo` to the Python verifier.

## Input origins

| Input | Resolved source | Provenance boundary |
| --- | --- | --- |
| Geyser and Floodgate SNAPSHOTs | OpenCollab Maven repositories declared in `core/pom.xml` and related module POMs | Exact timestamp and bytes in `BUILD_INPUTS.json`; publisher signature is not independently verified |
| Velocity API and Brigadier SNAPSHOTs | PaperMC/Velocity Maven repositories declared in `velocity/pom.xml` | Exact timestamp and bytes; Velocity API snapshot is not runtime build 605 |
| BungeeCord SNAPSHOTs | CodeMC Maven repository declared in `bungee/pom.xml` | Exact timestamp and bytes; legacy Bungee module is outside the TDR runtime compatibility promise |
| CraftAPI SNAPSHOT | CodeMC repository declared in `core/pom.xml` | Exact timestamp and bytes |
| sqlite-jdbc | Maven Central, exact version 3.53.4.0 | Provided dependency; the runtime may supply a different driver |
| `bukkit/lib/CrazyCore v10.7.7.jar` | Inherited Git-tracked binary, added in upstream history at `f8c10d68` | Original distributor URL/signature is not retained |
| `bukkit/lib/CrazyLogin v7.23.2.jar` | Inherited Git-tracked binary, added in upstream history at `f8c10d68` | Original distributor URL/signature is not retained |
| `bukkit/lib/UltraAuth v2.1.2.jar` | Inherited Git-tracked binary, added in upstream history at `a02acd2d` | Original distributor URL/signature is not retained |
| `bungee/lib/BungeeAuth-1.4.jar` | Inherited Git-tracked binary, added in upstream history at `426b458a` | Original distributor URL/signature is not retained |

The system-scope JARs are retained only for inherited legacy adapter compilation. They are not proof those adapters are
safe or compatible with the live TDR stack. Their exact hashes are in `BUILD_INPUTS.json`; replacing them needs a
separate provenance and compatibility review.
