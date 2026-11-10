# FastLogin build inputs and provenance

This candidate source build targets immutable Velocity API 4.3.1 and Java 25 for Minecraft26.3. Runtime build45,
the proxy patch and AuthMe6.0.1 integration require separate acceptance. The previous runtime target remains26.1.2/build605. The version and SHA-256
locks in [BUILD_INPUTS.json](BUILD_INPUTS.json) record the inputs used for this source build. They do not establish
Minecraft 26.3 runtime compatibility or physical AuthMe login acceptance.

## How the build gate works

1. Maven resolves the complete dependency graph to `*/target/dependency-tree.txt`.
2. `scripts/verify-build-inputs.py --resolve` creates a temporary Maven POM from `BUILD_INPUTS.json`, resolves only
   the locked JAR/POM inputs with transitive dependencies excluded, and then verifies them. This step does not compile
   FastLogin sources or execute its annotation processors.
3. The verifier compares each resolved graph with `docs/dependency-tree/*.txt`, verifies SHA-256
   for every locked JAR and POM (both Maven's base alias and timestamped file where the coordinate remains a SNAPSHOT), verifies the exact
   `sqlite-jdbc` 3.53.4.0 JAR/POM, and verifies the four vendored system-scope JARs.
4. The full Maven test/package phase runs only after the lock passes. The parent POM fixes the archive entry timestamp
   for this fork build; two clean source copies produced byte-identical Core, Bukkit, Bungee, and Velocity JARs under
   Java 25.0.3 and the same locked Maven repository. GitHub workflows pin each Action to a full commit
   SHA. Maven Build has `contents: read`; CodeQL has `contents: read` plus `security-events: write` for SARIF upload.
   Neither workflow has `contents: write`. Both support manual `workflow_dispatch` runs on `main`.

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
| Velocity API4.3.1 and Brigadier1.0.0-20261110.144108-12 | PaperMC Maven repository declared in `velocity/pom.xml` | Immutable API release plus exact Brigadier timestamp; changed API transitive JAR/POM hashes included; not runtime acceptance |
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

## 26.3 candidate check — 10 November 2026

No Java protocol adapter changed during the API upgrade. Existing premium HMAC/binding tests and Velocity
configuration-event, reserved-name and durable backend-ACK regressions pass against 4.3.1 (23 focused tests).
Maven produced Core, Bukkit and Velocity artifacts as 1.12.0-tdr.4-SNAPSHOT. The checksum/graph verifier passed 114 inputs.
This is a targeted source-build check, not a new two-build reproducibility or physical login claim. Bukkit compiles against Paper API 26.3/build 248;
25 focused backend tests also pass. The 48 distinct focused tests
cover core+proxy+backend units; the combined 26.3 backend connection contract still needs integration acceptance.

Primary API inputs: [Velocity 4.3.1 POM](https://repo.papermc.io/repository/maven-public/com/velocitypowered/velocity-api/4.3.1/velocity-api-4.3.1.pom),
[Velocity 4.3.1 source](https://github.com/PaperMC/Velocity/tree/300f5d0823691c8c8bace0c91714c63ab52e0b77),
[Brigadier metadata](https://repo.papermc.io/repository/maven-public/com/velocitypowered/velocity-brigadier/1.0.0-SNAPSHOT/maven-metadata.xml).

### DEV integration CI,10.11.2026

Both Maven Build and CodeQL run for pushes and PRs targeting `dev` as well as
`main`. `.java-version` pins Temurin25.0.3+9 for these native Maven workflows;
the local release admission records the actual compiler vendor/build separately.
No workflow receives a deployment credential or restarts a server. Stable `main`
does not update parked PROD. Existing checksum and test steps are unchanged.
