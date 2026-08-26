# 🚀 Morphe Automated APK Builder (GitHub Actions)

[![Build Morphe Patched APK](https://github.com/anurag008w/morphe-builder/actions/workflows/build-morphe-apk.yml/badge.svg)](https://github.com/anurag008w/morphe-builder/actions/workflows/build-morphe-apk.yml)
[![Sync Latest Patches](https://github.com/anurag008w/morphe-builder/actions/workflows/update-patches-list.yml/badge.svg)](https://github.com/anurag008w/morphe-builder/actions/workflows/update-patches-list.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A fully automated, zero-setup cloud builder for **Morphe-patched Android applications** powered by GitHub Actions. Build, patch, sign, and release the latest versions of **YouTube**, **YouTube Music**, and **Reddit** with custom patches in 1-click.

---

## ✨ Features

- ⚡ **1-Click Multi-App Builds**: Build all apps (`YouTube`, `YouTube-Music`, `Reddit`) concurrently or individually.
- 📦 **Automated GitHub Releases**: Releases include signed APKs, latest MicroG (GmsCore), and SHA-256 checksums.
- ⚙️ **100% Pure JSON Configuration**: Manage patch settings and enabled states directly through JSON options files (`config/options-*.json`).
- 🎛️ **Workflow UI Checkbox Toggles**: Toggle disabled-by-default patches (e.g. *Clone app*, *Change installer*, *Custom branding*) directly from the GitHub Actions popup.
- 🔗 **Smart Multi-URL Downloader**: Supports custom APK download URLs (single or comma-separated) with automated app keyword mapping and direct token streaming.
- 🛡️ **Fault-Tolerant Build Engine**: Single app download or patch failures will not abort the build; remaining applications are patched and released independently.
- 🔄 **Automated Patch Synchronization**: Scheduled and manual workflows sync new patch bundles, generate option schemas, and update workflow dispatch menus automatically.

---

## 📱 Supported Applications

| Application | Package Name | Verified Stock Version | Default Patches Applied |
| :--- | :--- | :--- | :---: |
| 🔴 **YouTube** | `com.google.android.youtube` | `v21.04.223` | **79 Patches** |
| 🎵 **YouTube Music** | `com.google.android.apps.youtube.music` | `v9.15.51` | **43 Patches** |
| 🟠 **Reddit** | `com.reddit.frontpage` | `v2026.04.0` | **22 Patches** |

---

## 🚀 How to Use

### 1. Trigger the Build
1. Navigate to the **[Actions](../../actions/workflows/build-morphe-apk.yml)** tab.
2. Select **"Build Morphe Patched APK"** from the left sidebar.
3. Click **"Run workflow"** and configure your build preferences:
   - **Target Application**: Select `All` or a specific application.
   - **Installation Type**: Choose `Non-Root (with GmsCore)` or `Root (without GmsCore)`.
   - **Patch Checkboxes**: Check any optional patches you wish to enable.
   - **Target Architecture**: Choose `arm64-v8a` (recommended for smaller APK size) or `all` (universal compatibility).
   - **Custom APK URL(s)** *(Optional)*: Leave empty to use verified defaults, or provide custom URLs separated by commas.
4. Click the green **"Run workflow"** button.

### 2. Download Your Patched APKs
- Once the workflow completes (~2 minutes), navigate to the **[Releases](../../releases)** section.
- Download the generated `.apk` files directly to your Android device.

---

## 📂 Repository Structure

```text
├── .github/workflows/
│   ├── build-morphe-apk.yml     # Main APK build & release workflow
│   └── update-patches-list.yml  # Automated patch sync workflow
├── config/
│   ├── options-youtube.json     # Official YouTube patch options
│   ├── options-ytmusic.json     # Official YouTube Music patch options
│   └── options-reddit.json      # Official Reddit patch options
└── scripts/
    ├── build_apk.py             # Fault-tolerant build & patch execution engine
    └── sync_patches.py          # Patch introspection & options generator
```

---

## 🛠️ Customization

To customize patch options permanently, edit the respective JSON file in `config/`:
- Enable/disable patches: `"enabled": true` / `"enabled": false`
- Configure sub-options:
  ```json
  "Custom branding": {
      "enabled": true,
      "options": {
          "customName": "YouTube Morphe",
          "customIcon": "assets/icon.png"
      }
  }
  ```

---

## 📄 License & Credits

- Powered by [Morphe](https://github.com/MorpheApp) (`morphe-desktop`, `morphe-patches`).
- Distributed under the [MIT License](LICENSE).
