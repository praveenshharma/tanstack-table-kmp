# Publishing to Maven Central

This project uses the
[vanniktech/gradle-maven-publish-plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
to publish `:table-core` and `:table-compose` to Maven Central via the
[Central Portal](https://central.sonatype.com/).

The publishing config lives in
[`gradle/publishing.gradle.kts`](../gradle/publishing.gradle.kts) and is
applied to each published module. It is **dormant** until the maintainer
provides credentials and POM metadata.

## One-time setup

### 1. Reserve a Maven coordinate

Sign up at [central.sonatype.com](https://central.sonatype.com/) and either:

- Register a domain you own (the canonical option — e.g. `com.example`), or
- Use the auto-verified `io.github.<your-github-handle>` namespace (simpler;
  no DNS).

The chosen value becomes `POM_GROUP_ID` below.

### 2. Generate a GPG signing key

```bash
gpg --gen-key
gpg --list-secret-keys --keyid-format=long
# Upload public key to one of the keyservers Sonatype validates against:
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
# Export the private key for CI use (armored, single line, no newlines):
gpg --armor --export-secret-keys <KEY_ID> | base64 -w0 > /tmp/gpg-key.b64
```

### 3. Generate Sonatype user tokens

In central.sonatype.com → Account → Generate User Token. You'll receive a
username and password — these are *not* your portal login.

### 4. Provide credentials

Either:

- Write to `~/.gradle/gradle.properties` (recommended for local publishing):
  ```properties
  POM_GROUP_ID=io.github.<your-handle>
  POM_ARTIFACT_VERSION=0.1.0
  POM_URL=https://github.com/<owner>/tanstack-table-kmp
  POM_SCM_URL=https://github.com/<owner>/tanstack-table-kmp
  POM_DEVELOPER_ID=<your-handle>
  POM_DEVELOPER_NAME=<Your Name>
  POM_DEVELOPER_EMAIL=<you@example.com>

  mavenCentralUsername=<sonatype-user-token-username>
  mavenCentralPassword=<sonatype-user-token-password>
  signingInMemoryKey=<armored-gpg-private-key-single-line>
  signingInMemoryKeyId=<last-8-hex-chars>
  signingInMemoryKeyPassword=<gpg-passphrase>
  ```

- Or set the same names as environment variables prefixed
  `ORG_GRADLE_PROJECT_` (for CI / GitHub Actions).

## Publishing a release

```bash
# Verify the publishing config is active (look for "Maven publishing is inactive" — should be absent):
./gradlew :table-core:tasks --group=publishing

# Dry run — publish to local Maven for inspection:
./gradlew publishToMavenLocal --no-configuration-cache

# Real publish to Maven Central:
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

The plugin handles signing, bundling, and uploading. Artifacts land in the
Central Portal staging area; the `automaticRelease = false` setting in
[`gradle/publishing.gradle.kts`](../gradle/publishing.gradle.kts) keeps a
manual confirmation step in the portal UI before promoting to public.

## GitHub Actions

A `.github/workflows/release.yml` template (not yet committed) should run on
`v*` git tag pushes and execute `publishAndReleaseToMavenCentral`, with the
credentials passed as `secrets.*` mapped to the `ORG_GRADLE_PROJECT_*`
environment variables. Wire that up after your first manual publish succeeds.
