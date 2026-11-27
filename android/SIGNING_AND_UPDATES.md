# Android test APK signing and updates

The Android package identity is fixed at `com.talker.offline`. Each GitHub Actions build sets a higher `versionCode` using the workflow run number and updates `versionName`.

## Current test-build policy

The current workflow builds an installable **debug APK** using Android Gradle's automatic debug signing. No custom keystore, GitHub signing secrets, or manual signing setup is required.

This is intentionally a test-build shortcut, not a production release-signing setup.

## Update limitation

Android always requires APK signatures; signing cannot be removed entirely. The debug keystore on a GitHub-hosted runner is temporary and is not preserved between workflow runs. Therefore, APKs built on separate runs may have different signatures. Android can reject an in-place update and require uninstalling the previous test APK first.

The higher `versionCode` is still set correctly, but it does not override Android's signature-matching requirement.

## Later production option

If in-place updates are required, configure one persistent private release keystore and use it for every release APK. Keep the keystore and passwords backed up securely; losing them prevents future updates signed with that key.

The previous `setup-signing.ps1` helper is not needed for the current debug workflow.
