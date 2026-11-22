# GitHub CI/CD Setup Guide (`Thai Car OBD-II Pro`)

## Pipeline Stages (`.github/workflows/android-ci-cd.yml`)
1. **Verify (`verify`)**: Runs on every PR, `main`/`master` push, `v*` tag, and manual dispatch.
   - Restores `debug.keystore` from `debug.keystore.base64` and prepares `.env`.
   - Executes `gradle :app:testDebugUnitTest` and `gradle :app:lintDebug`.
   - Uploads HTML/XML test & lint reports (`verification-reports`).
2. **Debug APK (`build-debug-apk`)**:
   - Builds `:app:assembleDebug`, computes SHA-256 checksums, and uploads `thai-car-obd2-pro-debug-apk`.
3. **Signed Release APK & AAB (`build-release-apk`)**:
   - Runs on `main`/`master` pushes, `v*` release tags, and manual runs (`build_release=true`).
   - Builds `:app:assembleRelease` and `:app:bundleRelease` (both `.apk` and `.aab`).
   - Uses repository release signing secrets when set; falls back to an ephemeral CI key only on non-tag builds.
4. **GitHub Release (`publish-github-release`)**:
   - Automatically triggers on version tags (e.g. `git tag v2.0.0 && git push origin v2.0.0`) and attaches the signed Release APK, AAB, Debug APK, and SHA-256 checksums to a GitHub Release.

## Required GitHub Repository Secrets
Configure these in **Settings → Secrets and variables → Actions**:
- `RELEASE_KEYSTORE_BASE64`: Base64-encoded upload keystore (`base64 -w0 my-upload-key.jks`), containing key alias `upload`.
- `RELEASE_STORE_PASSWORD`: Keystore password.
- `RELEASE_KEY_PASSWORD`: Upload key password.
- `GEMINI_API_KEY` *(optional)*: Injected into `.env` at build time when needed.
