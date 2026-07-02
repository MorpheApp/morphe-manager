# [1.23.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.23.0-dev.3...v1.23.0-dev.4) (2026-07-01)


### Features

* Add Replace/Merge choice dialog on settings and selections import ([#683](https://github.com/MorpheApp/morphe-manager/issues/683)) ([fc9c892](https://github.com/MorpheApp/morphe-manager/commit/fc9c892ddbc28c89136d09c1caabe4fdae50804b))

# [1.23.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.23.0-dev.2...v1.23.0-dev.3) (2026-07-01)


### Bug Fixes

* Resolve TalkBack accessibility issues ([#676](https://github.com/MorpheApp/morphe-manager/issues/676)) ([1b9c246](https://github.com/MorpheApp/morphe-manager/commit/1b9c2467670b02861e9a8c42a182f02fca4df099))


### Features

* Adaptive `ActionPillRow` with compress-to-fit ([9554fb3](https://github.com/MorpheApp/morphe-manager/commit/9554fb3f8adb67dcfa09814c37a40d6120da0b82))

# [1.23.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.23.0-dev.1...v1.23.0-dev.2) (2026-06-29)


### Bug Fixes

* Apply imported manager language immediately ([5722523](https://github.com/MorpheApp/morphe-manager/commit/5722523d01d66db37eb38c563d87da97641b6407))
* Preserve saved original APK after root mount installation ([#673](https://github.com/MorpheApp/morphe-manager/issues/673)) ([2a36543](https://github.com/MorpheApp/morphe-manager/commit/2a36543580b9dca8e8eeea100b0085867cb2e73e))


### Features

* Include file picker preferences in settings export/import ([46e37e2](https://github.com/MorpheApp/morphe-manager/commit/46e37e2915dc92b6a655127155935e63c0b04efc))

# [1.23.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0...v1.23.0-dev.1) (2026-06-28)


### Bug Fixes

* Exit remote patcher process on cancellation to release wakelock ([46ed1be](https://github.com/MorpheApp/morphe-manager/commit/46ed1be52a327cedfb4fa4982e31ac0a4f378dc4))
* Preserve localized step name when preparing split APKs ([290ea99](https://github.com/MorpheApp/morphe-manager/commit/290ea99b471f4277cc81c5504232001765fc052b))
* Remember patch selection across multiple sources ([#667](https://github.com/MorpheApp/morphe-manager/issues/667)) ([262b4ea](https://github.com/MorpheApp/morphe-manager/commit/262b4eab8991a03428dc2eaf0ab5c487b2137426))
* Use perceptual luminance midpoint for content color on brand backgrounds ([164fc6e](https://github.com/MorpheApp/morphe-manager/commit/164fc6e032c49f460f4b879b1cf4e7a0f861de7d))


### Features

* Add `Show older releases` expander to changelog dialogs ([#668](https://github.com/MorpheApp/morphe-manager/issues/668)) ([2ad8756](https://github.com/MorpheApp/morphe-manager/commit/2ad87560038f04b90fcc8dabb46132e9f626d381))

# [1.22.0](https://github.com/MorpheApp/morphe-manager/compare/v1.21.0...v1.22.0) (2026-06-26)


### Bug Fixes

* Accessibility improvements for bottom sheet ([40b98cb](https://github.com/MorpheApp/morphe-manager/commit/40b98cbf9af0dacd2f559d6f91f64612f7c893d2))
* Add Cancel button to Select APK dialog ([4b06e2e](https://github.com/MorpheApp/morphe-manager/commit/4b06e2e31b257e88df24b867c0acdf279d872746))
* Add invisible TalkBack back button in settings ([00ffc62](https://github.com/MorpheApp/morphe-manager/commit/00ffc6283b52a16fd3659e749006ebbdc16990cc))
* Add missing license metadata for JitPack libraries and embed offline license texts ([858aa8f](https://github.com/MorpheApp/morphe-manager/commit/858aa8fbf5c456e4336b5dc294c77f6f76f6ab0a))
* Animate `Close` button entrance in `InstalledAppInfoDialog` ([665e360](https://github.com/MorpheApp/morphe-manager/commit/665e36059755cb82e45b8c22a92791dcc815eeda))
* Block UI during APK selection processing ([36b0a92](https://github.com/MorpheApp/morphe-manager/commit/36b0a923bf308cfcd3bc4724469bf4985bbe7088))
* Center `PulsingLogoIndicator` in `BundlePatchesDialog` and replace `LoadingIndicator` ([5bc796b](https://github.com/MorpheApp/morphe-manager/commit/5bc796be434d3254b906d68f00712212e0d4e3d0))
* Decouple app DB and filesystem writes from ViewModel lifecycle ([b8776a0](https://github.com/MorpheApp/morphe-manager/commit/b8776a04cab7c1677a20ebb26f82b0041263e10d))
* Ensure `APKMirror` button contrasts with dialog background in all themes ([4369d24](https://github.com/MorpheApp/morphe-manager/commit/4369d24bc76c870877f3d654e358d64125f796f2))
* Exclude nested `.apk` entries from split archive detection ([#644](https://github.com/MorpheApp/morphe-manager/issues/644)) ([0b4d6ee](https://github.com/MorpheApp/morphe-manager/commit/0b4d6ee006e098cbaed7b01ec529e5a585e1e8ec))
* Expose swipe actions and improve accessibility for screen readers ([e913918](https://github.com/MorpheApp/morphe-manager/commit/e913918a9e4814bf80e5f4045e714752dc97d2dc))
* Handle incoming intents from any screen, not just HomeScreen ([d004c21](https://github.com/MorpheApp/morphe-manager/commit/d004c21f5198a5020274ef9b18488fdfd5712ee1))
* Improve accessibility with screen reader actions and labeled buttons ([9753c49](https://github.com/MorpheApp/morphe-manager/commit/9753c49624859dbe81ba1747693ecb4edb9341b8))
* Improve system tab icon ([d2c5926](https://github.com/MorpheApp/morphe-manager/commit/d2c5926b349176f322f91771ef868589437251c3))
* Make split APK preparation step labels translatable via string resources ([6d6d444](https://github.com/MorpheApp/morphe-manager/commit/6d6d4443cb528e930577f06966c4bdcb24f40173))
* Offload APK parsing and installer plan resolution off the main thread ([8c9125b](https://github.com/MorpheApp/morphe-manager/commit/8c9125bb6744fee382fcc48cfef2d3d7b7e0ada7))
* Persist installed app version reliably after patching ([089cc7e](https://github.com/MorpheApp/morphe-manager/commit/089cc7e87a10322a4d19eb22bf35531e10db1728))
* Prevent `AppInfo` hero from blending into background on extreme accent colors ([742ed60](https://github.com/MorpheApp/morphe-manager/commit/742ed604df9ba260b45d21686d984d12f067f623))
* Prevent tab chip squishing in expert patcher on long translations ([ec720dd](https://github.com/MorpheApp/morphe-manager/commit/ec720dd56b7254fbf822227002ddf28e3e26d438))
* Prevent text compression in headers on tablet portrait ([#651](https://github.com/MorpheApp/morphe-manager/issues/651)) ([b1783fc](https://github.com/MorpheApp/morphe-manager/commit/b1783fc5e844d256dc79c664ce8c6889d9327ca3))
* Reduce patcher action bar button spacing on tablet portrait ([0a8b79b](https://github.com/MorpheApp/morphe-manager/commit/0a8b79b4cb1a017cb1701f6e59b4b0c108b7e70f))
* Replace `context.getString()` with `stringResource()` in composables ([c173344](https://github.com/MorpheApp/morphe-manager/commit/c1733448c6131eadcddf34ee25be2e8ca5bd5d4c))
* Replace context resource calls with Compose APIs and clean up stale suppressions ([9cb9132](https://github.com/MorpheApp/morphe-manager/commit/9cb9132c1f79835ba11297d27401bb2ee32bda1f))
* Show install error instead of uninstall dialog when target package is not installed ([6a7dcc1](https://github.com/MorpheApp/morphe-manager/commit/6a7dcc1089dbff3f27539d740b1ee782bb29637d))
* Show patching log in error dialog instead of generic placeholder when no failed step message ([bcc5f50](https://github.com/MorpheApp/morphe-manager/commit/bcc5f501fc1ded0c13a11301de5577510decf3d6))
* Suppress completion notification when patching is canceled by the user ([5cd4509](https://github.com/MorpheApp/morphe-manager/commit/5cd4509b628f57bbde8bb88015fc9d9d6cd6b86b))
* Suppress patching complete notification when app is in foreground or auto-install is pending ([45d78a1](https://github.com/MorpheApp/morphe-manager/commit/45d78a17b0379bdb5f2c4030df6db42a0efec41b))
* Trigger Shizuku auto-install in the background without waiting for foreground ([#642](https://github.com/MorpheApp/morphe-manager/issues/642)) ([eee8626](https://github.com/MorpheApp/morphe-manager/commit/eee8626bb86d385a2036111cc1a75cf18dc6f141))
* Truncate version text with ellipsis to prevent update badge from being pushed off-screen ([ee85cb2](https://github.com/MorpheApp/morphe-manager/commit/ee85cb2891d41b206ccb5375ccac9c410d20b9cc))
* Unify vertical divider insets across home, settings and app info dialog ([681982f](https://github.com/MorpheApp/morphe-manager/commit/681982f6eacc0d7060388e2f0813d8fd649beed8))
* Use `LinearEasing` for settings exit fade to keep slide visible ([f9be268](https://github.com/MorpheApp/morphe-manager/commit/f9be268e6f3af929418998633ddf615249b48279))


### Features

* Add app list reorder mode to home screen multiselect bar ([#645](https://github.com/MorpheApp/morphe-manager/issues/645)) ([c0939e4](https://github.com/MorpheApp/morphe-manager/commit/c0939e44976ece5e42598a9538e176d3b5a70e3b))
* Add pulsing logo indicator ([52733f8](https://github.com/MorpheApp/morphe-manager/commit/52733f8ecd0d1f2792ec801b652139aacd8cf867))
* Add slide-up push transition for settings screen ([d5d8fc9](https://github.com/MorpheApp/morphe-manager/commit/d5d8fc9204c1c0bb4dcca59088489e20e5a8e501))
* Check `versionCode` when resolving patch compatibility ([#639](https://github.com/MorpheApp/morphe-manager/issues/639)) ([08889f3](https://github.com/MorpheApp/morphe-manager/commit/08889f319abc4fe41eb887e45f1666f6cbe5a014))
* Improve adaptive layout for tablet and landscape mode ([7f997ae](https://github.com/MorpheApp/morphe-manager/commit/7f997aeb122b67d27e34283fb54c0884451ae789))
* Parallelize bundle refresh and improve update snackbar ([b4e74a5](https://github.com/MorpheApp/morphe-manager/commit/b4e74a5ea2db17fec8d007b2d1cb5c57284421e2))
* Show completion notification after patching finishes ([cd2da25](https://github.com/MorpheApp/morphe-manager/commit/cd2da2587fc8f2967dba61ac8ba74e36fdbfc54d))
* Show home greetings based on time of day ([#632](https://github.com/MorpheApp/morphe-manager/issues/632)) ([1f30763](https://github.com/MorpheApp/morphe-manager/commit/1f30763833889cdea7b8e793d65bea6062edef5b))

# [1.22.0-dev.12](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.11...v1.22.0-dev.12) (2026-06-24)


### Bug Fixes

* Decouple app DB and filesystem writes from ViewModel lifecycle ([b8776a0](https://github.com/MorpheApp/morphe-manager/commit/b8776a04cab7c1677a20ebb26f82b0041263e10d))
* Handle incoming intents from any screen, not just HomeScreen ([d004c21](https://github.com/MorpheApp/morphe-manager/commit/d004c21f5198a5020274ef9b18488fdfd5712ee1))
* Offload APK parsing and installer plan resolution off the main thread ([8c9125b](https://github.com/MorpheApp/morphe-manager/commit/8c9125bb6744fee382fcc48cfef2d3d7b7e0ada7))
* Persist installed app version reliably after patching ([089cc7e](https://github.com/MorpheApp/morphe-manager/commit/089cc7e87a10322a4d19eb22bf35531e10db1728))
* Suppress patching complete notification when app is in foreground or auto-install is pending ([45d78a1](https://github.com/MorpheApp/morphe-manager/commit/45d78a17b0379bdb5f2c4030df6db42a0efec41b))

# [1.22.0-dev.11](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.10...v1.22.0-dev.11) (2026-06-23)


### Bug Fixes

* Add missing license metadata for JitPack libraries and embed offline license texts ([858aa8f](https://github.com/MorpheApp/morphe-manager/commit/858aa8fbf5c456e4336b5dc294c77f6f76f6ab0a))
* Make split APK preparation step labels translatable via string resources ([6d6d444](https://github.com/MorpheApp/morphe-manager/commit/6d6d4443cb528e930577f06966c4bdcb24f40173))
* Show patching log in error dialog instead of generic placeholder when no failed step message ([bcc5f50](https://github.com/MorpheApp/morphe-manager/commit/bcc5f501fc1ded0c13a11301de5577510decf3d6))
* Suppress completion notification when patching is canceled by the user ([5cd4509](https://github.com/MorpheApp/morphe-manager/commit/5cd4509b628f57bbde8bb88015fc9d9d6cd6b86b))
* Truncate version text with ellipsis to prevent update badge from being pushed off-screen ([ee85cb2](https://github.com/MorpheApp/morphe-manager/commit/ee85cb2891d41b206ccb5375ccac9c410d20b9cc))

# [1.22.0-dev.10](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.9...v1.22.0-dev.10) (2026-06-22)


### Features

* Show completion notification after patching finishes ([cd2da25](https://github.com/MorpheApp/morphe-manager/commit/cd2da2587fc8f2967dba61ac8ba74e36fdbfc54d))

# [1.22.0-dev.9](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.8...v1.22.0-dev.9) (2026-06-22)


### Bug Fixes

* Ensure `APKMirror` button contrasts with dialog background in all themes ([4369d24](https://github.com/MorpheApp/morphe-manager/commit/4369d24bc76c870877f3d654e358d64125f796f2))
* Prevent `AppInfo` hero from blending into background on extreme accent colors ([742ed60](https://github.com/MorpheApp/morphe-manager/commit/742ed604df9ba260b45d21686d984d12f067f623))

# [1.22.0-dev.8](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.7...v1.22.0-dev.8) (2026-06-21)


### Bug Fixes

* Center `PulsingLogoIndicator` in `BundlePatchesDialog` and replace `LoadingIndicator` ([5bc796b](https://github.com/MorpheApp/morphe-manager/commit/5bc796be434d3254b906d68f00712212e0d4e3d0))
* Replace `context.getString()` with `stringResource()` in composables ([c173344](https://github.com/MorpheApp/morphe-manager/commit/c1733448c6131eadcddf34ee25be2e8ca5bd5d4c))
* Replace context resource calls with Compose APIs and clean up stale suppressions ([9cb9132](https://github.com/MorpheApp/morphe-manager/commit/9cb9132c1f79835ba11297d27401bb2ee32bda1f))

# [1.22.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.6...v1.22.0-dev.7) (2026-06-21)


### Bug Fixes

* Block UI during APK selection processing ([36b0a92](https://github.com/MorpheApp/morphe-manager/commit/36b0a923bf308cfcd3bc4724469bf4985bbe7088))


### Features

* Add pulsing logo indicator ([52733f8](https://github.com/MorpheApp/morphe-manager/commit/52733f8ecd0d1f2792ec801b652139aacd8cf867))

# [1.22.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.5...v1.22.0-dev.6) (2026-06-21)


### Bug Fixes

* Prevent tab chip squishing in expert patcher on long translations ([ec720dd](https://github.com/MorpheApp/morphe-manager/commit/ec720dd56b7254fbf822227002ddf28e3e26d438))


### Features

* Parallelize bundle refresh and improve update snackbar ([b4e74a5](https://github.com/MorpheApp/morphe-manager/commit/b4e74a5ea2db17fec8d007b2d1cb5c57284421e2))

# [1.22.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.4...v1.22.0-dev.5) (2026-06-20)


### Bug Fixes

* Accessibility improvements for bottom sheet ([40b98cb](https://github.com/MorpheApp/morphe-manager/commit/40b98cbf9af0dacd2f559d6f91f64612f7c893d2))
* Add Cancel button to Select APK dialog ([4b06e2e](https://github.com/MorpheApp/morphe-manager/commit/4b06e2e31b257e88df24b867c0acdf279d872746))
* Add invisible TalkBack back button in settings ([00ffc62](https://github.com/MorpheApp/morphe-manager/commit/00ffc6283b52a16fd3659e749006ebbdc16990cc))
* Animate `Close` button entrance in `InstalledAppInfoDialog` ([665e360](https://github.com/MorpheApp/morphe-manager/commit/665e36059755cb82e45b8c22a92791dcc815eeda))
* Unify vertical divider insets across home, settings and app info dialog ([681982f](https://github.com/MorpheApp/morphe-manager/commit/681982f6eacc0d7060388e2f0813d8fd649beed8))

# [1.22.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.3...v1.22.0-dev.4) (2026-06-20)


### Bug Fixes

* Expose swipe actions and improve accessibility for screen readers ([e913918](https://github.com/MorpheApp/morphe-manager/commit/e913918a9e4814bf80e5f4045e714752dc97d2dc))
* Improve accessibility with screen reader actions and labeled buttons ([9753c49](https://github.com/MorpheApp/morphe-manager/commit/9753c49624859dbe81ba1747693ecb4edb9341b8))
* Improve system tab icon ([d2c5926](https://github.com/MorpheApp/morphe-manager/commit/d2c5926b349176f322f91771ef868589437251c3))
* Prevent text compression in headers on tablet portrait ([#651](https://github.com/MorpheApp/morphe-manager/issues/651)) ([b1783fc](https://github.com/MorpheApp/morphe-manager/commit/b1783fc5e844d256dc79c664ce8c6889d9327ca3))
* Reduce patcher action bar button spacing on tablet portrait ([0a8b79b](https://github.com/MorpheApp/morphe-manager/commit/0a8b79b4cb1a017cb1701f6e59b4b0c108b7e70f))
* Use `LinearEasing` for settings exit fade to keep slide visible ([f9be268](https://github.com/MorpheApp/morphe-manager/commit/f9be268e6f3af929418998633ddf615249b48279))


### Features

* Add slide-up push transition for settings screen ([d5d8fc9](https://github.com/MorpheApp/morphe-manager/commit/d5d8fc9204c1c0bb4dcca59088489e20e5a8e501))
* Improve adaptive layout for tablet and landscape mode ([7f997ae](https://github.com/MorpheApp/morphe-manager/commit/7f997aeb122b67d27e34283fb54c0884451ae789))

# [1.22.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.2...v1.22.0-dev.3) (2026-06-18)


### Bug Fixes

* Trigger Shizuku auto-install in the background without waiting for foreground ([#642](https://github.com/MorpheApp/morphe-manager/issues/642)) ([eee8626](https://github.com/MorpheApp/morphe-manager/commit/eee8626bb86d385a2036111cc1a75cf18dc6f141))

# [1.22.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.22.0-dev.1...v1.22.0-dev.2) (2026-06-17)


### Bug Fixes

* Exclude nested `.apk` entries from split archive detection ([#644](https://github.com/MorpheApp/morphe-manager/issues/644)) ([0b4d6ee](https://github.com/MorpheApp/morphe-manager/commit/0b4d6ee006e098cbaed7b01ec529e5a585e1e8ec))


### Features

* Add app list reorder mode to home screen multiselect bar ([#645](https://github.com/MorpheApp/morphe-manager/issues/645)) ([c0939e4](https://github.com/MorpheApp/morphe-manager/commit/c0939e44976ece5e42598a9538e176d3b5a70e3b))

# [1.22.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.21.0...v1.22.0-dev.1) (2026-06-15)


### Bug Fixes

* Show install error instead of uninstall dialog when target package is not installed ([6a7dcc1](https://github.com/MorpheApp/morphe-manager/commit/6a7dcc1089dbff3f27539d740b1ee782bb29637d))


### Features

* Check `versionCode` when resolving patch compatibility ([#639](https://github.com/MorpheApp/morphe-manager/issues/639)) ([08889f3](https://github.com/MorpheApp/morphe-manager/commit/08889f319abc4fe41eb887e45f1666f6cbe5a014))
* Show home greetings based on time of day ([#632](https://github.com/MorpheApp/morphe-manager/issues/632)) ([1f30763](https://github.com/MorpheApp/morphe-manager/commit/1f30763833889cdea7b8e793d65bea6062edef5b))

# [1.21.0](https://github.com/MorpheApp/morphe-manager/compare/v1.20.0...v1.21.0) (2026-06-10)


### Bug Fixes

* Cancelled install callbacks on some OEM systems ([#598](https://github.com/MorpheApp/morphe-manager/issues/598)) ([6ec8543](https://github.com/MorpheApp/morphe-manager/commit/6ec854345ae6bd59a51de1998a943566fa20db4f))
* Invert vertical parallax axis ([503e38e](https://github.com/MorpheApp/morphe-manager/commit/503e38ec4461d3f4d6372358bb8b310ac8875f22))


### Features

* Add onboarding tour triggered after first patch ([#611](https://github.com/MorpheApp/morphe-manager/issues/611)) ([a7ab6df](https://github.com/MorpheApp/morphe-manager/commit/a7ab6dfb576d20a9ef527ec759ed33ee57189772))
* Auto-install patched APK with Shizuku after patching completes ([c43c663](https://github.com/MorpheApp/morphe-manager/commit/c43c66368d175be5d6bfc97ebb98f5880130f833))

# [1.21.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.21.0-dev.2...v1.21.0-dev.3) (2026-06-03)


### Bug Fixes

* Invert vertical parallax axis ([503e38e](https://github.com/MorpheApp/morphe-manager/commit/503e38ec4461d3f4d6372358bb8b310ac8875f22))


### Features

* Add onboarding tour triggered after first patch ([#611](https://github.com/MorpheApp/morphe-manager/issues/611)) ([a7ab6df](https://github.com/MorpheApp/morphe-manager/commit/a7ab6dfb576d20a9ef527ec759ed33ee57189772))

# [1.21.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.21.0-dev.1...v1.21.0-dev.2) (2026-06-01)


### Bug Fixes

* Cancelled install callbacks on some OEM systems ([#598](https://github.com/MorpheApp/morphe-manager/issues/598)) ([6ec8543](https://github.com/MorpheApp/morphe-manager/commit/6ec854345ae6bd59a51de1998a943566fa20db4f))

# [1.21.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.20.0...v1.21.0-dev.1) (2026-05-31)


### Features

* Auto-install patched APK with Shizuku after patching completes ([c43c663](https://github.com/MorpheApp/morphe-manager/commit/c43c66368d175be5d6bfc97ebb98f5880130f833))

# [1.20.0](https://github.com/MorpheApp/morphe-manager/compare/v1.19.1...v1.20.0) (2026-05-29)


### Bug Fixes

* Center success icon and text in landscape two-column layout ([3189c05](https://github.com/MorpheApp/morphe-manager/commit/3189c05ef75c9b2c9933e124c24d43f5f209b18b))
* Correct shimmer highlight direction in light theme ([0639ccb](https://github.com/MorpheApp/morphe-manager/commit/0639ccb224e813781b7ca029d630eb1e64a6300c))
* Enable legacy external storage for file picker on Android 10 ([5c770fe](https://github.com/MorpheApp/morphe-manager/commit/5c770fed342fe4dcf43f7be7d7cf408e96915e31))


### Features

* Replace horizontal shimmer with diagonal sweep animation ([c658185](https://github.com/MorpheApp/morphe-manager/commit/c658185690b0a4c0940b11f0089c6573a00461bc))

## [1.19.1](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0...v1.19.1) (2026-05-29)


### Bug Fixes

* Bundle changelog shimmer, parsing freeze, and scroll-reload ([ba9b762](https://github.com/MorpheApp/morphe-manager/commit/ba9b762a96eeecd0245c96c6baf739ba78ff9fba))
* Correct release page URL format for GitLab ([df23aa6](https://github.com/MorpheApp/morphe-manager/commit/df23aa6774ed052c2b641a21517c49909b7d54a2))
* Distinguish file picker read errors from empty directories on Android 11+ ([e181417](https://github.com/MorpheApp/morphe-manager/commit/e181417019239408b23a9a075b42e9399323eb23))
* Limit dev changelog parsing and display to recent entries ([9f3e108](https://github.com/MorpheApp/morphe-manager/commit/9f3e1089cfca5c426077d045c738e9de352e2688))
* Scale shimmer gradient to component width ([a101524](https://github.com/MorpheApp/morphe-manager/commit/a101524ac21976db11b8b826be3ae5b30c16c49d))

# [1.19.0](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0...v1.19.0) (2026-05-29)


### Bug Fixes

* Add spacing between file name and detail in file picker ([5f94287](https://github.com/MorpheApp/morphe-manager/commit/5f942876329019ffbb4f249b76fe1d1547fd9330))
* Align APK and split APK icon sizes in file picker ([29858a8](https://github.com/MorpheApp/morphe-manager/commit/29858a82c3bb193a6e142f2b3f2fc92b42ae2282))
* Always restart game on selection to clear game-over state ([0caac2e](https://github.com/MorpheApp/morphe-manager/commit/0caac2ea2668151c22bc405754d772c004b8cab1))
* Always show saved APK option when versions match installed ([293d2db](https://github.com/MorpheApp/morphe-manager/commit/293d2db7799ebe8ff64b7cd383723333bbdbe235))
* Back gesture closes search before navigating in file picker ([2e7b9c1](https://github.com/MorpheApp/morphe-manager/commit/2e7b9c168d003a1347f6a48c2a2f7c9d2ba88408))
* Correct game tablet layout ([98fc3b3](https://github.com/MorpheApp/morphe-manager/commit/98fc3b3a846ba9a6466b5460498d06c6f9ff42a5))
* Crash on `.mpp` icon load ([6ca5114](https://github.com/MorpheApp/morphe-manager/commit/6ca51147200d50b5a54ce4ccd4b0ed858ddef0c8))
* Delay focus request so keyboard animates smoothly in release builds ([c1f7145](https://github.com/MorpheApp/morphe-manager/commit/c1f7145c8cbc49cb464841b3bb6c3079be84ce31))
* Eliminate empty-state flash and clear search query after exit animation ([4e3f30e](https://github.com/MorpheApp/morphe-manager/commit/4e3f30e7c018516bc5d0c2b5592324c9438a7451))
* Filter only JSON files in patch selections import custom file picker ([248849a](https://github.com/MorpheApp/morphe-manager/commit/248849a08575090daf65a43720badf2e9ca15174))
* Fix search keyboard delay and header crossfade animation in file picker ([43bfc61](https://github.com/MorpheApp/morphe-manager/commit/43bfc6164fed7f8321e7ff22cd5b7d53f3c640e9))
* Keystore compatibility with CLI ([f8cde49](https://github.com/MorpheApp/morphe-manager/commit/f8cde49a1545a50ee888782d5876f3dfb387ca52))
* Redesign Dino character as a proper T-Rex silhouette ([2472270](https://github.com/MorpheApp/morphe-manager/commit/2472270187871b04e74d8e5e5475073561ce333c))
* Redraw dino character as Chrome T-Rex pixel art ([e8b2851](https://github.com/MorpheApp/morphe-manager/commit/e8b285172242a7f430543242111fd30f9dddba12))
* Remove mini-games and logs button from simple mode ([4117875](https://github.com/MorpheApp/morphe-manager/commit/4117875d45cda60412b14acd96e4326eccd944ec))
* Remove notification sound feedback on patching completion ([b0b5e94](https://github.com/MorpheApp/morphe-manager/commit/b0b5e9478418ab0e75d518d63c7ac30b0e33d003))
* Remove redundant checkmark from file picker selection ([5222e14](https://github.com/MorpheApp/morphe-manager/commit/5222e1477b80e4843ed875bf6f426daa7c3c20b0))
* Show back-to-patching button on simple mode success screen ([90543e4](https://github.com/MorpheApp/morphe-manager/commit/90543e4bcf0bf4a719aed1a079015473ad70cff2))
* Show SD card in file picker and debug logs ([7424cd0](https://github.com/MorpheApp/morphe-manager/commit/7424cd00bc0506c244f385005e85e0fe045d74fe))
* Spawn Flappy pipes by position instead of real time ([5c654a9](https://github.com/MorpheApp/morphe-manager/commit/5c654a93c9b97df126b8c9e8ca3aecad73d13a20))
* Trigger game-over haptic only once ([7a00238](https://github.com/MorpheApp/morphe-manager/commit/7a002381e5d661aae029979e125e9576abe669e1))
* Use produceState to eliminate empty-state flash on directory change ([bf30ad7](https://github.com/MorpheApp/morphe-manager/commit/bf30ad7988dbab5d37c170535f5d7a6689f8c034))
* Use SD card and developer mode icons in file picker ([2fb9079](https://github.com/MorpheApp/morphe-manager/commit/2fb9079c33d2d5c122e5cd8f607c5bee557bad88))


### Features

* Add `Crossfade` and `AnimatedVisibility` transitions to file picker ([f935e66](https://github.com/MorpheApp/morphe-manager/commit/f935e669c4878d6796e57fe0d27db5f6f13c3ce4))
* Add 2048 mini-game on the patcher screen ([18e502d](https://github.com/MorpheApp/morphe-manager/commit/18e502d70f7b11373dd9ae9da3ea5ebcb59dc599))
* Add animated clouds and seagulls to Dino game ([b6da268](https://github.com/MorpheApp/morphe-manager/commit/b6da268fc659b7781c7bd17d3cc5940e3e0bdc8c))
* Add bird rotation, fix accidental restart on game-over tap in Flappy and Dino ([1d3b1ff](https://github.com/MorpheApp/morphe-manager/commit/1d3b1ff1030a29756837385a58b66f0879cea1d7))
* Add built-in file picker ([#576](https://github.com/MorpheApp/morphe-manager/issues/576)) ([85191a8](https://github.com/MorpheApp/morphe-manager/commit/85191a86a6ba5ac745c63779a3bbd6966bff5434))
* Add file-type icons for keystores and JSON in file picker ([0a0ed51](https://github.com/MorpheApp/morphe-manager/commit/0a0ed51d35db010b42429b90e2c32367bec0712b))
* Add Flappy mini-game on the patcher screen ([18ffc75](https://github.com/MorpheApp/morphe-manager/commit/18ffc75641a8cefc148ca17dfc6bd2fb74fe9788))
* Add game-over haptic feedback ([3127ac6](https://github.com/MorpheApp/morphe-manager/commit/3127ac6e4a5376e87db6ecda34ba52d942cf7154))
* Add installed app picker for universal patch flow ([afdb28d](https://github.com/MorpheApp/morphe-manager/commit/afdb28d438b383f005d4f46f0bbb95eec492233e))
* Add pause screen when leaving game mid-session ([ea50122](https://github.com/MorpheApp/morphe-manager/commit/ea501223cb1b2dace122c555905d1ad792b63df0))
* Add per-session high score to all mini-games; reduce Flappy Bird tap impulse ([d8f2d79](https://github.com/MorpheApp/morphe-manager/commit/d8f2d79f697bc65107d03c1b1a5d0d950b06bcda))
* Add root filesystem entry to file picker for rooted devices ([1d928e2](https://github.com/MorpheApp/morphe-manager/commit/1d928e2e2152aee86c208972c0fc3ab008be192b))
* Add search to file picker ([e9388ed](https://github.com/MorpheApp/morphe-manager/commit/e9388edfebe6124baaad5fc4d71ef19d130c206e))
* Add Snake and Dino mini-games on the patcher screen ([1a6039b](https://github.com/MorpheApp/morphe-manager/commit/1a6039bc5fad7f76885bec31b07cbe3c51dd8c0c))
* Extract shared `GameScoreRow` to eliminate score row duplication ([5ae8057](https://github.com/MorpheApp/morphe-manager/commit/5ae805736b312c1f71554c31956d93dfa734199d))
* Persist mini-game high scores across sessions ([510b526](https://github.com/MorpheApp/morphe-manager/commit/510b526560a857311f3cc61bb0da7e2c6ab05d40))
* Replace storage menu with breadcrumb path navigation in file picker ([d60f73b](https://github.com/MorpheApp/morphe-manager/commit/d60f73b75ad25595a555f181f324cb3c6a73f3a4))
* Select file immediately on tap in file picker ([80070b8](https://github.com/MorpheApp/morphe-manager/commit/80070b85f95ae7026a72b60eea57e17d3026d25c))
* Show icons for `.apkm` and `.xapk` files in file picker ([b1a6ee2](https://github.com/MorpheApp/morphe-manager/commit/b1a6ee24718b3c1433a0126eb2bb4e2117a75ed9))
* Show left-truncated breadcrumb path in file picker path bar ([0489850](https://github.com/MorpheApp/morphe-manager/commit/0489850b4128ff4958e7a4cde9d93c5c9676258b))
* Update Snake palette to brand colors ([4399783](https://github.com/MorpheApp/morphe-manager/commit/4399783d8cc46260c5a2fbbbab179831613663a4))

# [1.19.0-dev.14](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.13...v1.19.0-dev.14) (2026-05-27)


### Bug Fixes

* Keystore compatibility with CLI ([f8cde49](https://github.com/MorpheApp/morphe-manager/commit/f8cde49a1545a50ee888782d5876f3dfb387ca52))
* Use SD card and developer mode icons in file picker ([2fb9079](https://github.com/MorpheApp/morphe-manager/commit/2fb9079c33d2d5c122e5cd8f607c5bee557bad88))

# [1.19.0-dev.13](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.12...v1.19.0-dev.13) (2026-05-27)


### Bug Fixes

* Show SD card in file picker and debug logs ([7424cd0](https://github.com/MorpheApp/morphe-manager/commit/7424cd00bc0506c244f385005e85e0fe045d74fe))

# [1.19.0-dev.12](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.11...v1.19.0-dev.12) (2026-05-27)


### Bug Fixes

* Always show saved APK option when versions match installed ([293d2db](https://github.com/MorpheApp/morphe-manager/commit/293d2db7799ebe8ff64b7cd383723333bbdbe235))


### Features

* Add installed app picker for universal patch flow ([afdb28d](https://github.com/MorpheApp/morphe-manager/commit/afdb28d438b383f005d4f46f0bbb95eec492233e))

# [1.19.0-dev.11](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.10...v1.19.0-dev.11) (2026-05-25)


### Features

* Persist mini-game high scores across sessions ([510b526](https://github.com/MorpheApp/morphe-manager/commit/510b526560a857311f3cc61bb0da7e2c6ab05d40))

# [1.19.0-dev.10](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.9...v1.19.0-dev.10) (2026-05-25)


### Features

* Add bird rotation, fix accidental restart on game-over tap in Flappy and Dino ([1d3b1ff](https://github.com/MorpheApp/morphe-manager/commit/1d3b1ff1030a29756837385a58b66f0879cea1d7))
* Add per-session high score to all mini-games; reduce Flappy Bird tap impulse ([d8f2d79](https://github.com/MorpheApp/morphe-manager/commit/d8f2d79f697bc65107d03c1b1a5d0d950b06bcda))

# [1.19.0-dev.9](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.8...v1.19.0-dev.9) (2026-05-24)


### Bug Fixes

* Redraw dino character as Chrome T-Rex pixel art ([e8b2851](https://github.com/MorpheApp/morphe-manager/commit/e8b285172242a7f430543242111fd30f9dddba12))
* Remove mini-games and logs button from simple mode ([4117875](https://github.com/MorpheApp/morphe-manager/commit/4117875d45cda60412b14acd96e4326eccd944ec))


### Features

* Add animated clouds and seagulls to Dino game ([b6da268](https://github.com/MorpheApp/morphe-manager/commit/b6da268fc659b7781c7bd17d3cc5940e3e0bdc8c))
* Update Snake palette to brand colors ([4399783](https://github.com/MorpheApp/morphe-manager/commit/4399783d8cc46260c5a2fbbbab179831613663a4))

# [1.19.0-dev.8](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.7...v1.19.0-dev.8) (2026-05-24)


### Bug Fixes

* Always restart game on selection to clear game-over state ([0caac2e](https://github.com/MorpheApp/morphe-manager/commit/0caac2ea2668151c22bc405754d772c004b8cab1))
* Redesign Dino character as a proper T-Rex silhouette ([2472270](https://github.com/MorpheApp/morphe-manager/commit/2472270187871b04e74d8e5e5475073561ce333c))
* Show back-to-patching button on simple mode success screen ([90543e4](https://github.com/MorpheApp/morphe-manager/commit/90543e4bcf0bf4a719aed1a079015473ad70cff2))


### Features

* Add 2048 mini-game on the patcher screen ([18e502d](https://github.com/MorpheApp/morphe-manager/commit/18e502d70f7b11373dd9ae9da3ea5ebcb59dc599))
* Add Flappy mini-game on the patcher screen ([18ffc75](https://github.com/MorpheApp/morphe-manager/commit/18ffc75641a8cefc148ca17dfc6bd2fb74fe9788))
* Add game-over haptic feedback ([3127ac6](https://github.com/MorpheApp/morphe-manager/commit/3127ac6e4a5376e87db6ecda34ba52d942cf7154))
* Add pause screen when leaving game mid-session ([ea50122](https://github.com/MorpheApp/morphe-manager/commit/ea501223cb1b2dace122c555905d1ad792b63df0))
* Add Snake and Dino mini-games on the patcher screen ([1a6039b](https://github.com/MorpheApp/morphe-manager/commit/1a6039bc5fad7f76885bec31b07cbe3c51dd8c0c))
* Extract shared `GameScoreRow` to eliminate score row duplication ([5ae8057](https://github.com/MorpheApp/morphe-manager/commit/5ae805736b312c1f71554c31956d93dfa734199d))

# [1.19.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.6...v1.19.0-dev.7) (2026-05-24)


### Bug Fixes

* Eliminate empty-state flash and clear search query after exit animation ([4e3f30e](https://github.com/MorpheApp/morphe-manager/commit/4e3f30e7c018516bc5d0c2b5592324c9438a7451))
* Fix search keyboard delay and header crossfade animation in file picker ([43bfc61](https://github.com/MorpheApp/morphe-manager/commit/43bfc6164fed7f8321e7ff22cd5b7d53f3c640e9))
* Use produceState to eliminate empty-state flash on directory change ([bf30ad7](https://github.com/MorpheApp/morphe-manager/commit/bf30ad7988dbab5d37c170535f5d7a6689f8c034))

# [1.19.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.5...v1.19.0-dev.6) (2026-05-24)


### Features

* Add `Crossfade` and `AnimatedVisibility` transitions to file picker ([f935e66](https://github.com/MorpheApp/morphe-manager/commit/f935e669c4878d6796e57fe0d27db5f6f13c3ce4))
* Add root filesystem entry to file picker for rooted devices ([1d928e2](https://github.com/MorpheApp/morphe-manager/commit/1d928e2e2152aee86c208972c0fc3ab008be192b))
* Replace storage menu with breadcrumb path navigation in file picker ([d60f73b](https://github.com/MorpheApp/morphe-manager/commit/d60f73b75ad25595a555f181f324cb3c6a73f3a4))
* Show left-truncated breadcrumb path in file picker path bar ([0489850](https://github.com/MorpheApp/morphe-manager/commit/0489850b4128ff4958e7a4cde9d93c5c9676258b))

# [1.19.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.4...v1.19.0-dev.5) (2026-05-23)


### Bug Fixes

* Align APK and split APK icon sizes in file picker ([29858a8](https://github.com/MorpheApp/morphe-manager/commit/29858a82c3bb193a6e142f2b3f2fc92b42ae2282))
* Filter only JSON files in patch selections import custom file picker ([248849a](https://github.com/MorpheApp/morphe-manager/commit/248849a08575090daf65a43720badf2e9ca15174))


### Features

* Add file-type icons for keystores and JSON in file picker ([0a0ed51](https://github.com/MorpheApp/morphe-manager/commit/0a0ed51d35db010b42429b90e2c32367bec0712b))
* Select file immediately on tap in file picker ([80070b8](https://github.com/MorpheApp/morphe-manager/commit/80070b85f95ae7026a72b60eea57e17d3026d25c))
* Show icons for `.apkm` and `.xapk` files in file picker ([b1a6ee2](https://github.com/MorpheApp/morphe-manager/commit/b1a6ee24718b3c1433a0126eb2bb4e2117a75ed9))

# [1.19.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.3...v1.19.0-dev.4) (2026-05-23)


### Bug Fixes

* Back gesture closes search before navigating in file picker ([2e7b9c1](https://github.com/MorpheApp/morphe-manager/commit/2e7b9c168d003a1347f6a48c2a2f7c9d2ba88408))
* Delay focus request so keyboard animates smoothly in release builds ([c1f7145](https://github.com/MorpheApp/morphe-manager/commit/c1f7145c8cbc49cb464841b3bb6c3079be84ce31))

# [1.19.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.2...v1.19.0-dev.3) (2026-05-23)


### Bug Fixes

* Add spacing between file name and detail in file picker ([5f94287](https://github.com/MorpheApp/morphe-manager/commit/5f942876329019ffbb4f249b76fe1d1547fd9330))
* Remove redundant checkmark from file picker selection ([5222e14](https://github.com/MorpheApp/morphe-manager/commit/5222e1477b80e4843ed875bf6f426daa7c3c20b0))


### Features

* Add search to file picker ([e9388ed](https://github.com/MorpheApp/morphe-manager/commit/e9388edfebe6124baaad5fc4d71ef19d130c206e))

# [1.19.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.19.0-dev.1...v1.19.0-dev.2) (2026-05-23)


### Bug Fixes

* Crash on `.mpp` icon load ([6ca5114](https://github.com/MorpheApp/morphe-manager/commit/6ca51147200d50b5a54ce4ccd4b0ed858ddef0c8))

# [1.19.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0...v1.19.0-dev.1) (2026-05-23)


### Bug Fixes

* Remove notification sound feedback on patching completion ([b0b5e94](https://github.com/MorpheApp/morphe-manager/commit/b0b5e9478418ab0e75d518d63c7ac30b0e33d003))


### Features

* Add built-in file picker ([#576](https://github.com/MorpheApp/morphe-manager/issues/576)) ([85191a8](https://github.com/MorpheApp/morphe-manager/commit/85191a86a6ba5ac745c63779a3bbd6966bff5434))

# [1.18.0](https://github.com/MorpheApp/morphe-manager/compare/v1.17.1...v1.18.0) (2026-05-22)


### Bug Fixes

* App info dialog could show wrong app's data after installing a different app ([94139e6](https://github.com/MorpheApp/morphe-manager/commit/94139e66d01a05f947e4d56074504faf096478cc))
* Check both `stable` and `dev` channels when pre-releases are enabled for JSON sources ([b4e02bd](https://github.com/MorpheApp/morphe-manager/commit/b4e02bdbbfbb468a3a447305a42e60a2d1092328))
* Disable fade overlay in two-column layout ([3d83c53](https://github.com/MorpheApp/morphe-manager/commit/3d83c5327e783dbda9ced58dccda1fe9d4b9b268))
* Don't flag blank-default required options as missing ([9a6fa0c](https://github.com/MorpheApp/morphe-manager/commit/9a6fa0c55a84d6fb92807ee7241bc916429a5b2e))
* Eliminate `AnimatedVisibility` layout jumps by moving spacing inside animated content ([e5f5115](https://github.com/MorpheApp/morphe-manager/commit/e5f51156fa4a079e5a2c6e28e8c40b08ea5514a2))
* Exclude device state preferences from settings export/import ([f29a158](https://github.com/MorpheApp/morphe-manager/commit/f29a158a15ef1bd912e1fd08dd8ede62035cd6c5))
* Fix empty APK file picker on Android 16 with work profile ([#561](https://github.com/MorpheApp/morphe-manager/issues/561)) ([54e8290](https://github.com/MorpheApp/morphe-manager/commit/54e82903fe339487fa035e35c6d0370f95977d7f))
* Fix Patch button text contrast in WarningBanner ([8c4caab](https://github.com/MorpheApp/morphe-manager/commit/8c4caabd0d76b64fa512d1a9c217a21188488d29))
* Launch system installer dialog for manager self-updates ([#558](https://github.com/MorpheApp/morphe-manager/issues/558)) ([654e15b](https://github.com/MorpheApp/morphe-manager/commit/654e15b6dd58edd5a63154fb659f8ce72dc74396))
* Pass explicit tint to `MorpheIcon` inside `Button` to restore `LocalContentColor` inheritance ([4337291](https://github.com/MorpheApp/morphe-manager/commit/433729159a0c4809601b550e1e4c87e0fbedd6cb))
* Pre-release version history shown in update dialog for stable users ([9d0debd](https://github.com/MorpheApp/morphe-manager/commit/9d0debdb3786cffbea339fa300e8c58c9168920f))
* Prevent moving app to SD card to avoid native lib linking failure ([d10db7d](https://github.com/MorpheApp/morphe-manager/commit/d10db7da99ed7608764be3160e0f9fd2060fdd9c))
* Reduce icon button spacing in dialog text fields ([251f839](https://github.com/MorpheApp/morphe-manager/commit/251f8395cd390e9903b78a0effcdd0663ef76cd2))
* Remove redundant notification icon XML generation ([97c7009](https://github.com/MorpheApp/morphe-manager/commit/97c7009c3bdab1e4cdd40fb78f2a6a534723bf91))
* Resolve changelog dialog rendering jank after `markdown-renderer` update ([58fdc0f](https://github.com/MorpheApp/morphe-manager/commit/58fdc0f4dbf42f0b0fe36a344ccfb39bc9cb33e0))
* Restyle `MultiSelectBar` to match bundle action bar with select/deselect all ([cc27b07](https://github.com/MorpheApp/morphe-manager/commit/cc27b0722532d02d642e3eed5afe31dfff51df78))
* Retry patcher process on exit code 139 (SIGSEGV) as low-memory failure ([608b8d5](https://github.com/MorpheApp/morphe-manager/commit/608b8d51ae0e547fa00b66a4db2a0b62b748f8e2))
* Set `Switch` checked icon color to primary to match track color ([b4e4ed4](https://github.com/MorpheApp/morphe-manager/commit/b4e4ed426625d2d554fae8f588a843aae00d0a05))
* Show descriptive confirmation toasts for expert mode patch actions ([5e1931d](https://github.com/MorpheApp/morphe-manager/commit/5e1931d6453e9050509c9c29d02f7bbb5e3f0dae))
* Source rename not reflected in patch flow until restart ([3d9a1d8](https://github.com/MorpheApp/morphe-manager/commit/3d9a1d8aab16f0de6aadc47c37569c55496ff17c))
* Update translations from Crowdin ([648f146](https://github.com/MorpheApp/morphe-manager/commit/648f146a5a3bbed05132f10a824ef1c599591bf0))


### Features

* Add "Use installed APK" button to APK availability dialog ([#552](https://github.com/MorpheApp/morphe-manager/issues/552)) ([9d2df3f](https://github.com/MorpheApp/morphe-manager/commit/9d2df3f591eb46703e1fc0203c45a69a5129c375))
* Add battery optimization exclusion prompt before patching ([1b95a87](https://github.com/MorpheApp/morphe-manager/commit/1b95a87123be035d99a42123773fb9a9f5945b87))
* Add file picker for file path patch options ([18620ed](https://github.com/MorpheApp/morphe-manager/commit/18620edd988e85c9966088daa83808e220da35a0))
* Add monochrome adaptive icon generation ([#571](https://github.com/MorpheApp/morphe-manager/issues/571)) ([bd0fcab](https://github.com/MorpheApp/morphe-manager/commit/bd0fcab96a2ee049c00150ba26a9991e5ad86539))
* Add notification sound feedback on patching completion ([1167339](https://github.com/MorpheApp/morphe-manager/commit/116733918ce09c4e290ff66af29ab3fde1e107dc))
* Add share, export, and install action buttons to APK management dialog ([0f51573](https://github.com/MorpheApp/morphe-manager/commit/0f5157382886cf27595240484d3579a7a0ff61f0))
* Add toast feedback and tooltips to `MultiSelectBar` pill buttons ([fee8095](https://github.com/MorpheApp/morphe-manager/commit/fee8095098006baf327d31f0e995baf303eded3b))
* Add toast feedback and tooltips to `SourceManagementSheet` pill buttons ([db2dd71](https://github.com/MorpheApp/morphe-manager/commit/db2dd71ffb433fda66c3cdaffb749c01ef618754))
* Add tooltips to `ActionPillButton`s in APK and patch selection dialogs ([8dd77b4](https://github.com/MorpheApp/morphe-manager/commit/8dd77b4ddf4afd11bd9d755a3478841e7cb18508))
* Close search with back gesture in expert mode dialog ([6c01ac1](https://github.com/MorpheApp/morphe-manager/commit/6c01ac1d44b95976c7edc594149eba3372484ed6))
* Close search with back gesture on home screen ([09e0dee](https://github.com/MorpheApp/morphe-manager/commit/09e0deeef76e3831b6345377ed091811e2712ea9))
* Consolidate import/export settings into grouped rows ([7bea066](https://github.com/MorpheApp/morphe-manager/commit/7bea0665191a6c6b5f24934e073143d2dd741ff4))
* Open expert dialog when APK shared from file manager ([#559](https://github.com/MorpheApp/morphe-manager/issues/559)) ([35b0d4d](https://github.com/MorpheApp/morphe-manager/commit/35b0d4dd44f2a280e93beb1d5b80fb119b52e172))
* Polish settings screens UI ([3d5b4ca](https://github.com/MorpheApp/morphe-manager/commit/3d5b4ca3ed94afca7bafa9111a3314b61cb9fe34))
* Scope drag handle to `BundleCardHeader` to prevent tooltip conflict ([2b34281](https://github.com/MorpheApp/morphe-manager/commit/2b34281c8f3faf655bbc1562be411cd60a087ac3))
* Update patcher notification with stage and patch name progress ([0c1afb5](https://github.com/MorpheApp/morphe-manager/commit/0c1afb50b2072e8808c30f5dcbdc29bbe19aba05))

# [1.18.0-dev.12](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.11...v1.18.0-dev.12) (2026-05-21)


### Features

* Add notification sound feedback on patching completion ([1167339](https://github.com/MorpheApp/morphe-manager/commit/116733918ce09c4e290ff66af29ab3fde1e107dc))

# [1.18.0-dev.11](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.10...v1.18.0-dev.11) (2026-05-21)


### Bug Fixes

* Remove redundant notification icon XML generation ([97c7009](https://github.com/MorpheApp/morphe-manager/commit/97c7009c3bdab1e4cdd40fb78f2a6a534723bf91))

# [1.18.0-dev.10](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.9...v1.18.0-dev.10) (2026-05-21)


### Bug Fixes

* Check both `stable` and `dev` channels when pre-releases are enabled for JSON sources ([b4e02bd](https://github.com/MorpheApp/morphe-manager/commit/b4e02bdbbfbb468a3a447305a42e60a2d1092328))
* Pass explicit tint to `MorpheIcon` inside `Button` to restore `LocalContentColor` inheritance ([4337291](https://github.com/MorpheApp/morphe-manager/commit/433729159a0c4809601b550e1e4c87e0fbedd6cb))
* Set `Switch` checked icon color to primary to match track color ([b4e4ed4](https://github.com/MorpheApp/morphe-manager/commit/b4e4ed426625d2d554fae8f588a843aae00d0a05))


### Features

* Add monochrome adaptive icon generation ([#571](https://github.com/MorpheApp/morphe-manager/issues/571)) ([bd0fcab](https://github.com/MorpheApp/morphe-manager/commit/bd0fcab96a2ee049c00150ba26a9991e5ad86539))

# [1.18.0-dev.9](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.8...v1.18.0-dev.9) (2026-05-20)


### Features

* Add toast feedback and tooltips to `MultiSelectBar` pill buttons ([fee8095](https://github.com/MorpheApp/morphe-manager/commit/fee8095098006baf327d31f0e995baf303eded3b))
* Add toast feedback and tooltips to `SourceManagementSheet` pill buttons ([db2dd71](https://github.com/MorpheApp/morphe-manager/commit/db2dd71ffb433fda66c3cdaffb749c01ef618754))
* Add tooltips to `ActionPillButton`s in APK and patch selection dialogs ([8dd77b4](https://github.com/MorpheApp/morphe-manager/commit/8dd77b4ddf4afd11bd9d755a3478841e7cb18508))
* Scope drag handle to `BundleCardHeader` to prevent tooltip conflict ([2b34281](https://github.com/MorpheApp/morphe-manager/commit/2b34281c8f3faf655bbc1562be411cd60a087ac3))

# [1.18.0-dev.8](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.7...v1.18.0-dev.8) (2026-05-20)


### Bug Fixes

* Eliminate `AnimatedVisibility` layout jumps by moving spacing inside animated content ([e5f5115](https://github.com/MorpheApp/morphe-manager/commit/e5f51156fa4a079e5a2c6e28e8c40b08ea5514a2))


### Features

* Add share, export, and install action buttons to APK management dialog ([0f51573](https://github.com/MorpheApp/morphe-manager/commit/0f5157382886cf27595240484d3579a7a0ff61f0))
* Consolidate import/export settings into grouped rows ([7bea066](https://github.com/MorpheApp/morphe-manager/commit/7bea0665191a6c6b5f24934e073143d2dd741ff4))
* Polish settings screens UI ([3d5b4ca](https://github.com/MorpheApp/morphe-manager/commit/3d5b4ca3ed94afca7bafa9111a3314b61cb9fe34))

# [1.18.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.6...v1.18.0-dev.7) (2026-05-19)


### Bug Fixes

* Fix empty APK file picker on Android 16 with work profile ([#561](https://github.com/MorpheApp/morphe-manager/issues/561)) ([54e8290](https://github.com/MorpheApp/morphe-manager/commit/54e82903fe339487fa035e35c6d0370f95977d7f))
* Fix Patch button text contrast in WarningBanner ([8c4caab](https://github.com/MorpheApp/morphe-manager/commit/8c4caabd0d76b64fa512d1a9c217a21188488d29))
* Restyle `MultiSelectBar` to match bundle action bar with select/deselect all ([cc27b07](https://github.com/MorpheApp/morphe-manager/commit/cc27b0722532d02d642e3eed5afe31dfff51df78))
* Show descriptive confirmation toasts for expert mode patch actions ([5e1931d](https://github.com/MorpheApp/morphe-manager/commit/5e1931d6453e9050509c9c29d02f7bbb5e3f0dae))


### Features

* Close search with back gesture in expert mode dialog ([6c01ac1](https://github.com/MorpheApp/morphe-manager/commit/6c01ac1d44b95976c7edc594149eba3372484ed6))
* Close search with back gesture on home screen ([09e0dee](https://github.com/MorpheApp/morphe-manager/commit/09e0deeef76e3831b6345377ed091811e2712ea9))
* Update patcher notification with stage and patch name progress ([0c1afb5](https://github.com/MorpheApp/morphe-manager/commit/0c1afb50b2072e8808c30f5dcbdc29bbe19aba05))

# [1.18.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.5...v1.18.0-dev.6) (2026-05-18)


### Bug Fixes

* Exclude device state preferences from settings export/import ([f29a158](https://github.com/MorpheApp/morphe-manager/commit/f29a158a15ef1bd912e1fd08dd8ede62035cd6c5))
* Retry patcher process on exit code 139 (SIGSEGV) as low-memory failure ([608b8d5](https://github.com/MorpheApp/morphe-manager/commit/608b8d51ae0e547fa00b66a4db2a0b62b748f8e2))

# [1.18.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.4...v1.18.0-dev.5) (2026-05-18)


### Features

* Add battery optimization exclusion prompt before patching ([1b95a87](https://github.com/MorpheApp/morphe-manager/commit/1b95a87123be035d99a42123773fb9a9f5945b87))
* Open expert dialog when APK shared from file manager ([#559](https://github.com/MorpheApp/morphe-manager/issues/559)) ([35b0d4d](https://github.com/MorpheApp/morphe-manager/commit/35b0d4dd44f2a280e93beb1d5b80fb119b52e172))

# [1.18.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.3...v1.18.0-dev.4) (2026-05-17)


### Bug Fixes

* Don't flag blank-default required options as missing ([9a6fa0c](https://github.com/MorpheApp/morphe-manager/commit/9a6fa0c55a84d6fb92807ee7241bc916429a5b2e))
* Reduce icon button spacing in dialog text fields ([251f839](https://github.com/MorpheApp/morphe-manager/commit/251f8395cd390e9903b78a0effcdd0663ef76cd2))


### Features

* Add file picker for file path patch options ([18620ed](https://github.com/MorpheApp/morphe-manager/commit/18620edd988e85c9966088daa83808e220da35a0))

# [1.18.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.2...v1.18.0-dev.3) (2026-05-17)


### Bug Fixes

* Launch system installer dialog for manager self-updates ([#558](https://github.com/MorpheApp/morphe-manager/issues/558)) ([654e15b](https://github.com/MorpheApp/morphe-manager/commit/654e15b6dd58edd5a63154fb659f8ce72dc74396))

# [1.18.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.18.0-dev.1...v1.18.0-dev.2) (2026-05-17)


### Bug Fixes

* Update translations from Crowdin ([648f146](https://github.com/MorpheApp/morphe-manager/commit/648f146a5a3bbed05132f10a824ef1c599591bf0))

# [1.18.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.17.1...v1.18.0-dev.1) (2026-05-17)


### Bug Fixes

* App info dialog could show wrong app's data after installing a different app ([94139e6](https://github.com/MorpheApp/morphe-manager/commit/94139e66d01a05f947e4d56074504faf096478cc))
* Disable fade overlay in two-column layout ([3d83c53](https://github.com/MorpheApp/morphe-manager/commit/3d83c5327e783dbda9ced58dccda1fe9d4b9b268))
* Pre-release version history shown in update dialog for stable users ([9d0debd](https://github.com/MorpheApp/morphe-manager/commit/9d0debdb3786cffbea339fa300e8c58c9168920f))
* Resolve changelog dialog rendering jank after `markdown-renderer` update ([58fdc0f](https://github.com/MorpheApp/morphe-manager/commit/58fdc0f4dbf42f0b0fe36a344ccfb39bc9cb33e0))
* Source rename not reflected in patch flow until restart ([3d9a1d8](https://github.com/MorpheApp/morphe-manager/commit/3d9a1d8aab16f0de6aadc47c37569c55496ff17c))


### Features

* Add "Use installed APK" button to APK availability dialog ([#552](https://github.com/MorpheApp/morphe-manager/issues/552)) ([9d2df3f](https://github.com/MorpheApp/morphe-manager/commit/9d2df3f591eb46703e1fc0203c45a69a5129c375))

# [1.18.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.17.1...v1.18.0-dev.1) (2026-05-17)


### Bug Fixes

* Disable fade overlay in two-column layout ([ea51995](https://github.com/MorpheApp/morphe-manager/commit/ea5199543af70e3aea1460702918d67d97376917))
* Resolve changelog dialog rendering jank after `markdown-renderer` update ([b969be9](https://github.com/MorpheApp/morphe-manager/commit/b969be9d17f3bd7b311bf18053510ba5682065ea))


### Features

* Add "Use installed APK" button to APK availability dialog ([#552](https://github.com/MorpheApp/morphe-manager/issues/552)) ([550eed1](https://github.com/MorpheApp/morphe-manager/commit/550eed1516fb76e1b72c3bb99e83e6ee30686163))

## [1.17.1](https://github.com/MorpheApp/morphe-manager/compare/v1.17.0...v1.17.1) (2026-05-12)


### Bug Fixes

* Fallback to nearest available density when anti-splitting and add `riscv64` ABI support ([b17ee37](https://github.com/MorpheApp/morphe-manager/commit/b17ee377895c75ff24a7d3892f3423a1dc2f1f30))
* Preserve localized step name when applying patches ([9fefff1](https://github.com/MorpheApp/morphe-manager/commit/9fefff168921c63f0eb47e8477ac16d151a5788f))
* Remove horizontal clip boundary from app card swipe area ([7536d76](https://github.com/MorpheApp/morphe-manager/commit/7536d760e77a13ee1bb11fe82f575c869b3cf3fd))

## [1.17.1-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.17.0...v1.17.1-dev.1) (2026-05-11)


### Bug Fixes

* Preserve localized step name when applying patches ([9fefff1](https://github.com/MorpheApp/morphe-manager/commit/9fefff168921c63f0eb47e8477ac16d151a5788f))
* Remove horizontal clip boundary from app card swipe area ([7536d76](https://github.com/MorpheApp/morphe-manager/commit/7536d760e77a13ee1bb11fe82f575c869b3cf3fd))

# [1.17.0](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0...v1.17.0) (2026-05-10)


### Bug Fixes

* Changelog sometimes missing in update dialog ([aca670c](https://github.com/MorpheApp/morphe-manager/commit/aca670c4293b5cae2d923ab1ea8c0789954249e1))
* Detect `Shizuku` in stealth mode via permission lookup ([67d3ef4](https://github.com/MorpheApp/morphe-manager/commit/67d3ef4bb63a4382826187a35345a13302875783))
* Refresh app version, name and icon on home screen after patching ([5e1b1f5](https://github.com/MorpheApp/morphe-manager/commit/5e1b1f59ec5be5f3acdad3c4e66096ff315768db))
* Remap bundle UIDs on import to restore saved patch selections on fresh install ([22e8961](https://github.com/MorpheApp/morphe-manager/commit/22e8961ea9df29ecf5816604e397a079204cfa62))
* Rename deep link param 'gitlabs' → 'gitlab' ([a892b9a](https://github.com/MorpheApp/morphe-manager/commit/a892b9a68dacaefffb05407c4a55459cd85b3eeb))
* Resolve file picker and path display issues for Downloads and SAF ([#531](https://github.com/MorpheApp/morphe-manager/issues/531)) ([5cfddd2](https://github.com/MorpheApp/morphe-manager/commit/5cfddd2ceca0ac744d535d232cabe7584ae1981e))
* Show patch option descriptions in options dialog ([25a9916](https://github.com/MorpheApp/morphe-manager/commit/25a991690531e80c5ab8076edd8624bd2301ede4))
* Skip import for unknown bundle UIDs to prevent FK constraint crash ([806582f](https://github.com/MorpheApp/morphe-manager/commit/806582f3503a59a08ede290e1bd6de10ac0de098))
* Support GitLab avatar in deep link confirmation dialog ([1610104](https://github.com/MorpheApp/morphe-manager/commit/1610104688ac5dfd27413a4e745e6e83c76cd8b9))
* Use plural for patch count label ([955b269](https://github.com/MorpheApp/morphe-manager/commit/955b269a28d12b9ee18ba7e815aa86674b75c117))


### Features

* Add `GitLab` bundle support ([33b276d](https://github.com/MorpheApp/morphe-manager/commit/33b276d91b45d4b09d5d231f7f6000881d82e93c))
* Block patching when bundle requires newer patcher ([e136ec6](https://github.com/MorpheApp/morphe-manager/commit/e136ec663821ba660d9c184b007b29f3c7f5a946))
* Improve patch bundle error handling and UI feedback ([262a2c6](https://github.com/MorpheApp/morphe-manager/commit/262a2c6fc5f2a596427200eb9c15c091e178f49e))
* Preload bundle avatars on startup to eliminate first-open delay ([3ce8e36](https://github.com/MorpheApp/morphe-manager/commit/3ce8e362abcf3950dcbad73f59cc8d61272f9817))
* Redesign `Add source` dialog and improve bundle error handling ([8196578](https://github.com/MorpheApp/morphe-manager/commit/81965788109ecde8806e9113f1febac3deb3f040))

# [1.17.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.17.0-dev.6...v1.17.0-dev.7) (2026-05-09)


### Bug Fixes

* Remap bundle UIDs on import to restore saved patch selections on fresh install ([22e8961](https://github.com/MorpheApp/morphe-manager/commit/22e8961ea9df29ecf5816604e397a079204cfa62))
* Resolve file picker and path display issues for Downloads and SAF ([#531](https://github.com/MorpheApp/morphe-manager/issues/531)) ([5cfddd2](https://github.com/MorpheApp/morphe-manager/commit/5cfddd2ceca0ac744d535d232cabe7584ae1981e))
* Skip import for unknown bundle UIDs to prevent FK constraint crash ([806582f](https://github.com/MorpheApp/morphe-manager/commit/806582f3503a59a08ede290e1bd6de10ac0de098))

# [1.17.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.17.0-dev.5...v1.17.0-dev.6) (2026-05-09)


### Bug Fixes

* Refresh app version, name and icon on home screen after patching ([5e1b1f5](https://github.com/MorpheApp/morphe-manager/commit/5e1b1f59ec5be5f3acdad3c4e66096ff315768db))


### Features

* Preload bundle avatars on startup to eliminate first-open delay ([3ce8e36](https://github.com/MorpheApp/morphe-manager/commit/3ce8e362abcf3950dcbad73f59cc8d61272f9817))

# [1.17.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.17.0-dev.4...v1.17.0-dev.5) (2026-05-09)


### Bug Fixes

* Rename deep link param 'gitlabs' → 'gitlab' ([a892b9a](https://github.com/MorpheApp/morphe-manager/commit/a892b9a68dacaefffb05407c4a55459cd85b3eeb))
* Support GitLab avatar in deep link confirmation dialog ([1610104](https://github.com/MorpheApp/morphe-manager/commit/1610104688ac5dfd27413a4e745e6e83c76cd8b9))

# [1.17.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.17.0-dev.3...v1.17.0-dev.4) (2026-05-09)


### Features

* Add `GitLab` bundle support ([33b276d](https://github.com/MorpheApp/morphe-manager/commit/33b276d91b45d4b09d5d231f7f6000881d82e93c))

# [1.17.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.17.0-dev.2...v1.17.0-dev.3) (2026-05-08)


### Features

* Improve patch bundle error handling and UI feedback ([262a2c6](https://github.com/MorpheApp/morphe-manager/commit/262a2c6fc5f2a596427200eb9c15c091e178f49e))
* Redesign `Add source` dialog and improve bundle error handling ([8196578](https://github.com/MorpheApp/morphe-manager/commit/81965788109ecde8806e9113f1febac3deb3f040))

# [1.17.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.17.0-dev.1...v1.17.0-dev.2) (2026-05-07)


### Bug Fixes

* Detect `Shizuku` in stealth mode via permission lookup ([67d3ef4](https://github.com/MorpheApp/morphe-manager/commit/67d3ef4bb63a4382826187a35345a13302875783))

# [1.17.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.16.1-dev.1...v1.17.0-dev.1) (2026-05-05)


### Bug Fixes

* Changelog sometimes missing in update dialog ([aca670c](https://github.com/MorpheApp/morphe-manager/commit/aca670c4293b5cae2d923ab1ea8c0789954249e1))


### Features

* Block patching when bundle requires newer patcher ([e136ec6](https://github.com/MorpheApp/morphe-manager/commit/e136ec663821ba660d9c184b007b29f3c7f5a946))

## [1.16.1-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0...v1.16.1-dev.1) (2026-05-05)


### Bug Fixes

* Show patch option descriptions in options dialog ([25a9916](https://github.com/MorpheApp/morphe-manager/commit/25a991690531e80c5ab8076edd8624bd2301ede4))

# [1.16.0](https://github.com/MorpheApp/morphe-manager/compare/v1.15.0...v1.16.0) (2026-05-04)


### Bug Fixes

* "SessionBasedInstallConfirmationActivity was finished by user" install error on some devices ([3e74857](https://github.com/MorpheApp/morphe-manager/commit/3e74857bdb6fab5815f04c52b137024188a60dc3))
* `Session is dead` error on Pixels devices when installing apps ([#458](https://github.com/MorpheApp/morphe-manager/issues/458)) ([ce1ce6e](https://github.com/MorpheApp/morphe-manager/commit/ce1ce6e195a4138c7ca18f5a9dd018db8591fddc))
* Adapt accent color contrast for extreme black/white values in app info dialog ([c4f883f](https://github.com/MorpheApp/morphe-manager/commit/c4f883ffbdbb1bd3e7b2085939cb050fa747a11b))
* Add logging and fix stale installer cache ([78b5aee](https://github.com/MorpheApp/morphe-manager/commit/78b5aee8dca5f1f51fb6bb46c9d9cab8bd1f7c0a))
* Always respect manager prerelease preference for update channel ([090ee0c](https://github.com/MorpheApp/morphe-manager/commit/090ee0ca1ed8d076b58dd3abe4ffa8f8f2062203))
* Apply locale via context wrap on Android < 13 ([ce193ee](https://github.com/MorpheApp/morphe-manager/commit/ce193ee7a4d9307aec876d1c6fae1c830fbf7612))
* Check primary ABI only in `isArmV7` to avoid false positives on `ArmV8` devices ([14729c2](https://github.com/MorpheApp/morphe-manager/commit/14729c28a73c0294152c3df1aabffdfb79974215))
* Eliminate background flash on `InstalledAppInfo` → patch flow transition ([b246a4b](https://github.com/MorpheApp/morphe-manager/commit/b246a4bf0322537f22add689c4f7054f612d37d4))
* Fall back to `Downloads` export on devices without `DocumentsUI` (Android TV) ([1e21c39](https://github.com/MorpheApp/morphe-manager/commit/1e21c3957e37351d1498b9894d2e4c3ee8154608))
* File picker and export for Android TV ([#491](https://github.com/MorpheApp/morphe-manager/issues/491)) ([7c1cfba](https://github.com/MorpheApp/morphe-manager/commit/7c1cfba98ff4b2eb39aa0e8a6f14e028978f1f60))
* Handle `InstallFailure` result when installing manager update ([a4d1eb8](https://github.com/MorpheApp/morphe-manager/commit/a4d1eb8a99dc53d59f23df3d7bb8ad3725edd1c4))
* Hoist install state reads to prevent recomposition on install ([9b82048](https://github.com/MorpheApp/morphe-manager/commit/9b82048bbb86ba4db954161dbb4df047e5485cad))
* Merge 'Filter split APKs' and `Remove unused native libraries` into 'Optimize for device architecture' setting ([2edb15f](https://github.com/MorpheApp/morphe-manager/commit/2edb15fcfb0fd018ffcafc0f7203930a203ab4d4))
* Patch bundles do not load on Android 8.0 devices ([3116619](https://github.com/MorpheApp/morphe-manager/commit/3116619cee63697dd71d1c22b95be11cec78384e))
* Prevent `InstalledAppInfoViewModel` collision on dialog reopen ([15ae79a](https://github.com/MorpheApp/morphe-manager/commit/15ae79a18614ea0287a2fd0c467cf14f7a7401e1))
* Replace `Ackpine` with native `SessionInstaller` ([#508](https://github.com/MorpheApp/morphe-manager/issues/508)) ([cf0f4db](https://github.com/MorpheApp/morphe-manager/commit/cf0f4dbe69a2c71114b828875bcfbe2ef6efd739))
* Replace `isLoaded` flag with `BundleState` sealed class and simplify `homeAppState` ([72976d3](https://github.com/MorpheApp/morphe-manager/commit/72976d33aea02e3aad6639fe7ff1ec1f4d330a0c))
* Resolve app icon from saved APK when app is not installed ([fe3ef6c](https://github.com/MorpheApp/morphe-manager/commit/fe3ef6cfaea6bb68c11bd89a127a1122d3cdb943))
* Resolve display name from bundle metadata over patched APK label ([6c6e065](https://github.com/MorpheApp/morphe-manager/commit/6c6e0658d4d4e2a415db1342764de45bd5595c04))
* Scope `InstalledAppInfoViewModel` to dialog instance via dialog token ([0cf2f6a](https://github.com/MorpheApp/morphe-manager/commit/0cf2f6a7c451fe1bd79538684babc3e1376b7f41))
* Shizuku installer couldn't update an already installed app ([#454](https://github.com/MorpheApp/morphe-manager/issues/454)) ([d4e74e3](https://github.com/MorpheApp/morphe-manager/commit/d4e74e3a84ca34529f8d20005c19b2b0e7c9136f))
* Show reinstall button and installer dialog for deleted apps ([472d046](https://github.com/MorpheApp/morphe-manager/commit/472d0462959235a90c0033129f87dd22a7af621d))
* Show SDK-incompatible versions as disabled, block patching when no versions are compatible with device SDK ([f90d5ba](https://github.com/MorpheApp/morphe-manager/commit/f90d5bacc7198f2c0a3c7cf48c7745415d89e141))
* Show swipe gesture hint on every custom bundle addition ([0c66503](https://github.com/MorpheApp/morphe-manager/commit/0c665038ed00234ed13b5df32b180382ecfa2f12))
* System installer couldn't update an already installed app ([#455](https://github.com/MorpheApp/morphe-manager/issues/455)) ([adc93e4](https://github.com/MorpheApp/morphe-manager/commit/adc93e4bab2d9b153b2aecd9f0671b393e42d309))
* Update home screen cards immediately after install/uninstall ([8f671bd](https://github.com/MorpheApp/morphe-manager/commit/8f671bdc350e1daa576640e13bbd778d78382f73))
* Use `SharedPreferences` as locale side-channel on Android < 13 ([a5f91bd](https://github.com/MorpheApp/morphe-manager/commit/a5f91bd02e5d26780a3119b7f73c762b27b02403))
* When greeting message is disabled, show a small top spacer so the app cards don't sit flush against the top of the screen ([6900cc2](https://github.com/MorpheApp/morphe-manager/commit/6900cc226e189b97c747d6d53f5fba98ade6ccf0))


### Features

* Adaptive two-column layout for `InstalledAppInfoDialog` on tablets ([40a29a9](https://github.com/MorpheApp/morphe-manager/commit/40a29a96967ef06eca27cb5f62db8d821f93c4aa))
* Add `BundleAppMetadata` as a data source for `AppDataResolver` ([3bdc1f5](https://github.com/MorpheApp/morphe-manager/commit/3bdc1f5299b7429444c7266559806aa1ad6aa8d1))
* Add fast bytecode mode setting to expert mode ([#403](https://github.com/MorpheApp/morphe-manager/issues/403)) ([e73c63c](https://github.com/MorpheApp/morphe-manager/commit/e73c63c7fcce14d5d38ce8b8cde26feed4a6e5e4))
* Add manual `JKS` parser for keystore import without BC provider dependency ([#494](https://github.com/MorpheApp/morphe-manager/issues/494)) ([ccc99a2](https://github.com/MorpheApp/morphe-manager/commit/ccc99a24b410dff2f04d8c74b8d0916b64d7d762))
* Add random background mode with rotation interval ([2d12fbb](https://github.com/MorpheApp/morphe-manager/commit/2d12fbbb052c3fe1fae979db7ad1d13b83918da9))
* Add swipe gestures and multi-select to app buttons on main screen ([#446](https://github.com/MorpheApp/morphe-manager/issues/446)) ([0330699](https://github.com/MorpheApp/morphe-manager/commit/033069989d24c437798bb5a7bf248afe9f30ae89))
* Add swipe gestures to hidden apps dialog and search results ([8cf1f13](https://github.com/MorpheApp/morphe-manager/commit/8cf1f137e8cf346a5628c80098e04aecf86f2441))
* Add toggle to disable home screen patching phrases ([#443](https://github.com/MorpheApp/morphe-manager/issues/443)) ([f53ad64](https://github.com/MorpheApp/morphe-manager/commit/f53ad646c90700af17b3d58b0a2651cdfb87aab9))
* Import keystore from `PKCS12`, `BKS` and `JKS` formats ([3f38387](https://github.com/MorpheApp/morphe-manager/commit/3f38387f515c351b5c4c248a9f682b46af03de85))
* Improve patch visibility in bundle and app patch dialogs ([#457](https://github.com/MorpheApp/morphe-manager/issues/457)) ([1881991](https://github.com/MorpheApp/morphe-manager/commit/188199176dba9fe41115e2376fe7ade3138c8068))
* Live patching progress in foreground notification ([c25af8f](https://github.com/MorpheApp/morphe-manager/commit/c25af8f28e7f33d90f8db62b74f5eb1128680e95))
* Migrate to `Ackpine` for package installation/uninstallation ([#444](https://github.com/MorpheApp/morphe-manager/issues/444)) ([aa7207d](https://github.com/MorpheApp/morphe-manager/commit/aa7207d486427753eb56c18ddd29d481f1a3605e))
* Open `.mpp` patch sources directly from file manager ([#483](https://github.com/MorpheApp/morphe-manager/issues/483)) ([f46a11f](https://github.com/MorpheApp/morphe-manager/commit/f46a11f91e88d948ecbab8e71200cec543ce48ec))
* Open patches dialog on hidden app tap in search ([1898e74](https://github.com/MorpheApp/morphe-manager/commit/1898e74fe8240961ddda118c2f7fac7f48bacb76))
* Prompt bundle selection before APK selection in simple mode ([#511](https://github.com/MorpheApp/morphe-manager/issues/511)) ([8161d9b](https://github.com/MorpheApp/morphe-manager/commit/8161d9b305aaab82285eccffaf89d42c786ea5f6))
* Sort universal patches to bottom of each bundle in patches dialog ([eac672e](https://github.com/MorpheApp/morphe-manager/commit/eac672e51f1fe3007bba46af6321d833ac20fb4b))
* Store merged APK from split archives as original for repatching ([#438](https://github.com/MorpheApp/morphe-manager/issues/438)) ([be0b868](https://github.com/MorpheApp/morphe-manager/commit/be0b86866f5e9f5e8aad4b596158084d03189fa3))

# [1.16.0-dev.20](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.19...v1.16.0-dev.20) (2026-05-03)


### Bug Fixes

* Use `SharedPreferences` as locale side-channel on Android < 13 ([a5f91bd](https://github.com/MorpheApp/morphe-manager/commit/a5f91bd02e5d26780a3119b7f73c762b27b02403))

# [1.16.0-dev.19](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.18...v1.16.0-dev.19) (2026-05-03)


### Bug Fixes

* Apply locale via context wrap on Android < 13 ([ce193ee](https://github.com/MorpheApp/morphe-manager/commit/ce193ee7a4d9307aec876d1c6fae1c830fbf7612))
* Hoist install state reads to prevent recomposition on install ([9b82048](https://github.com/MorpheApp/morphe-manager/commit/9b82048bbb86ba4db954161dbb4df047e5485cad))

# [1.16.0-dev.18](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.17...v1.16.0-dev.18) (2026-05-03)


### Bug Fixes

* Replace `Ackpine` with native `SessionInstaller` ([#508](https://github.com/MorpheApp/morphe-manager/issues/508)) ([cf0f4db](https://github.com/MorpheApp/morphe-manager/commit/cf0f4dbe69a2c71114b828875bcfbe2ef6efd739))


### Features

* Prompt bundle selection before APK selection in simple mode ([#511](https://github.com/MorpheApp/morphe-manager/issues/511)) ([8161d9b](https://github.com/MorpheApp/morphe-manager/commit/8161d9b305aaab82285eccffaf89d42c786ea5f6))

# [1.16.0-dev.17](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.16...v1.16.0-dev.17) (2026-05-01)


### Bug Fixes

* Eliminate background flash on `InstalledAppInfo` → patch flow transition ([b246a4b](https://github.com/MorpheApp/morphe-manager/commit/b246a4bf0322537f22add689c4f7054f612d37d4))
* Prevent `InstalledAppInfoViewModel` collision on dialog reopen ([15ae79a](https://github.com/MorpheApp/morphe-manager/commit/15ae79a18614ea0287a2fd0c467cf14f7a7401e1))

# [1.16.0-dev.16](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.15...v1.16.0-dev.16) (2026-04-30)


### Bug Fixes

* "SessionBasedInstallConfirmationActivity was finished by user" install error on some devices ([3e74857](https://github.com/MorpheApp/morphe-manager/commit/3e74857bdb6fab5815f04c52b137024188a60dc3))
* Scope `InstalledAppInfoViewModel` to dialog instance via dialog token ([0cf2f6a](https://github.com/MorpheApp/morphe-manager/commit/0cf2f6a7c451fe1bd79538684babc3e1376b7f41))

# [1.16.0-dev.15](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.14...v1.16.0-dev.15) (2026-04-29)


### Bug Fixes

* Show reinstall button and installer dialog for deleted apps ([472d046](https://github.com/MorpheApp/morphe-manager/commit/472d0462959235a90c0033129f87dd22a7af621d))
* Update home screen cards immediately after install/uninstall ([8f671bd](https://github.com/MorpheApp/morphe-manager/commit/8f671bdc350e1daa576640e13bbd778d78382f73))

# [1.16.0-dev.14](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.13...v1.16.0-dev.14) (2026-04-28)


### Bug Fixes

* File picker and export for Android TV ([#491](https://github.com/MorpheApp/morphe-manager/issues/491)) ([7c1cfba](https://github.com/MorpheApp/morphe-manager/commit/7c1cfba98ff4b2eb39aa0e8a6f14e028978f1f60))


### Features

* Add manual `JKS` parser for keystore import without BC provider dependency ([#494](https://github.com/MorpheApp/morphe-manager/issues/494)) ([ccc99a2](https://github.com/MorpheApp/morphe-manager/commit/ccc99a24b410dff2f04d8c74b8d0916b64d7d762))

# [1.16.0-dev.13](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.12...v1.16.0-dev.13) (2026-04-27)


### Bug Fixes

* Replace `isLoaded` flag with `BundleState` sealed class and simplify `homeAppState` ([72976d3](https://github.com/MorpheApp/morphe-manager/commit/72976d33aea02e3aad6639fe7ff1ec1f4d330a0c))


### Features

* Add random background mode with rotation interval ([2d12fbb](https://github.com/MorpheApp/morphe-manager/commit/2d12fbbb052c3fe1fae979db7ad1d13b83918da9))
* Import keystore from `PKCS12`, `BKS` and `JKS` formats ([3f38387](https://github.com/MorpheApp/morphe-manager/commit/3f38387f515c351b5c4c248a9f682b46af03de85))

# [1.16.0-dev.12](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.11...v1.16.0-dev.12) (2026-04-26)


### Bug Fixes

* Show SDK-incompatible versions as disabled, block patching when no versions are compatible with device SDK ([f90d5ba](https://github.com/MorpheApp/morphe-manager/commit/f90d5bacc7198f2c0a3c7cf48c7745415d89e141))


### Features

* Open `.mpp` patch sources directly from file manager ([#483](https://github.com/MorpheApp/morphe-manager/issues/483)) ([f46a11f](https://github.com/MorpheApp/morphe-manager/commit/f46a11f91e88d948ecbab8e71200cec543ce48ec))

# [1.16.0-dev.11](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.10...v1.16.0-dev.11) (2026-04-24)


### Bug Fixes

* Patch bundles do not load on Android 8.0 devices ([3116619](https://github.com/MorpheApp/morphe-manager/commit/3116619cee63697dd71d1c22b95be11cec78384e))
* Resolve display name from bundle metadata over patched APK label ([6c6e065](https://github.com/MorpheApp/morphe-manager/commit/6c6e0658d4d4e2a415db1342764de45bd5595c04))


### Features

* Live patching progress in foreground notification ([c25af8f](https://github.com/MorpheApp/morphe-manager/commit/c25af8f28e7f33d90f8db62b74f5eb1128680e95))

# [1.16.0-dev.10](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.9...v1.16.0-dev.10) (2026-04-24)


### Bug Fixes

* Resolve app icon from saved APK when app is not installed ([fe3ef6c](https://github.com/MorpheApp/morphe-manager/commit/fe3ef6cfaea6bb68c11bd89a127a1122d3cdb943))


### Features

* Add `BundleAppMetadata` as a data source for `AppDataResolver` ([3bdc1f5](https://github.com/MorpheApp/morphe-manager/commit/3bdc1f5299b7429444c7266559806aa1ad6aa8d1))
* Open patches dialog on hidden app tap in search ([1898e74](https://github.com/MorpheApp/morphe-manager/commit/1898e74fe8240961ddda118c2f7fac7f48bacb76))

# [1.16.0-dev.9](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.8...v1.16.0-dev.9) (2026-04-24)


### Bug Fixes

* Check primary ABI only in `isArmV7` to avoid false positives on `ArmV8` devices ([14729c2](https://github.com/MorpheApp/morphe-manager/commit/14729c28a73c0294152c3df1aabffdfb79974215))
* Fall back to `Downloads` export on devices without `DocumentsUI` (Android TV) ([1e21c39](https://github.com/MorpheApp/morphe-manager/commit/1e21c3957e37351d1498b9894d2e4c3ee8154608))

# [1.16.0-dev.8](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.7...v1.16.0-dev.8) (2026-04-24)


### Bug Fixes

* Adapt accent color contrast for extreme black/white values in app info dialog ([c4f883f](https://github.com/MorpheApp/morphe-manager/commit/c4f883ffbdbb1bd3e7b2085939cb050fa747a11b))
* Always respect manager prerelease preference for update channel ([090ee0c](https://github.com/MorpheApp/morphe-manager/commit/090ee0ca1ed8d076b58dd3abe4ffa8f8f2062203))
* Show swipe gesture hint on every custom bundle addition ([0c66503](https://github.com/MorpheApp/morphe-manager/commit/0c665038ed00234ed13b5df32b180382ecfa2f12))


### Features

* Adaptive two-column layout for `InstalledAppInfoDialog` on tablets ([40a29a9](https://github.com/MorpheApp/morphe-manager/commit/40a29a96967ef06eca27cb5f62db8d821f93c4aa))
* Add swipe gestures to hidden apps dialog and search results ([8cf1f13](https://github.com/MorpheApp/morphe-manager/commit/8cf1f137e8cf346a5628c80098e04aecf86f2441))

# [1.16.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.6...v1.16.0-dev.7) (2026-04-24)


### Bug Fixes

* `Session is dead` error on Pixels devices when installing apps ([#458](https://github.com/MorpheApp/morphe-manager/issues/458)) ([ce1ce6e](https://github.com/MorpheApp/morphe-manager/commit/ce1ce6e195a4138c7ca18f5a9dd018db8591fddc))


### Features

* Improve patch visibility in bundle and app patch dialogs ([#457](https://github.com/MorpheApp/morphe-manager/issues/457)) ([1881991](https://github.com/MorpheApp/morphe-manager/commit/188199176dba9fe41115e2376fe7ade3138c8068))

# [1.16.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.5...v1.16.0-dev.6) (2026-04-20)


### Bug Fixes

* System installer couldn't update an already installed app ([#455](https://github.com/MorpheApp/morphe-manager/issues/455)) ([adc93e4](https://github.com/MorpheApp/morphe-manager/commit/adc93e4bab2d9b153b2aecd9f0671b393e42d309))
* When greeting message is disabled, show a small top spacer so the app cards don't sit flush against the top of the screen ([6900cc2](https://github.com/MorpheApp/morphe-manager/commit/6900cc226e189b97c747d6d53f5fba98ade6ccf0))


### Features

* Sort universal patches to bottom of each bundle in patches dialog ([eac672e](https://github.com/MorpheApp/morphe-manager/commit/eac672e51f1fe3007bba46af6321d833ac20fb4b))

# [1.16.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.4...v1.16.0-dev.5) (2026-04-19)


### Bug Fixes

* Shizuku installer couldn't update an already installed app ([#454](https://github.com/MorpheApp/morphe-manager/issues/454)) ([d4e74e3](https://github.com/MorpheApp/morphe-manager/commit/d4e74e3a84ca34529f8d20005c19b2b0e7c9136f))

# [1.16.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.3...v1.16.0-dev.4) (2026-04-19)


### Bug Fixes

* Add logging and fix stale installer cache ([78b5aee](https://github.com/MorpheApp/morphe-manager/commit/78b5aee8dca5f1f51fb6bb46c9d9cab8bd1f7c0a))
* Merge 'Filter split APKs' and `Remove unused native libraries` into 'Optimize for device architecture' setting ([2edb15f](https://github.com/MorpheApp/morphe-manager/commit/2edb15fcfb0fd018ffcafc0f7203930a203ab4d4))


### Features

* Add swipe gestures and multi-select to app buttons on main screen ([#446](https://github.com/MorpheApp/morphe-manager/issues/446)) ([0330699](https://github.com/MorpheApp/morphe-manager/commit/033069989d24c437798bb5a7bf248afe9f30ae89))
* Add toggle to disable home screen patching phrases ([#443](https://github.com/MorpheApp/morphe-manager/issues/443)) ([f53ad64](https://github.com/MorpheApp/morphe-manager/commit/f53ad646c90700af17b3d58b0a2651cdfb87aab9))

# [1.16.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.2...v1.16.0-dev.3) (2026-04-18)


### Bug Fixes

* Handle `InstallFailure` result when installing manager update ([a4d1eb8](https://github.com/MorpheApp/morphe-manager/commit/a4d1eb8a99dc53d59f23df3d7bb8ad3725edd1c4))


### Features

* Migrate to `Ackpine` for package installation/uninstallation ([#444](https://github.com/MorpheApp/morphe-manager/issues/444)) ([aa7207d](https://github.com/MorpheApp/morphe-manager/commit/aa7207d486427753eb56c18ddd29d481f1a3605e))

# [1.16.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.16.0-dev.1...v1.16.0-dev.2) (2026-04-18)


### Features

* Add fast bytecode mode setting to expert mode ([#403](https://github.com/MorpheApp/morphe-manager/issues/403)) ([e73c63c](https://github.com/MorpheApp/morphe-manager/commit/e73c63c7fcce14d5d38ce8b8cde26feed4a6e5e4))

# [1.16.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.15.0...v1.16.0-dev.1) (2026-04-17)


### Features

* Store merged APK from split archives as original for repatching ([#438](https://github.com/MorpheApp/morphe-manager/issues/438)) ([be0b868](https://github.com/MorpheApp/morphe-manager/commit/be0b86866f5e9f5e8aad4b596158084d03189fa3))

# [1.15.0](https://github.com/MorpheApp/morphe-manager/compare/v1.14.0...v1.15.0) (2026-04-17)


### Bug Fixes

* Adjust wording ([482c1d1](https://github.com/MorpheApp/morphe-manager/commit/482c1d18945cfdc63bb54d7d112b0ec7ce4f58ba))
* Cancel patcher worker immediately on user cancellation ([4f0b312](https://github.com/MorpheApp/morphe-manager/commit/4f0b3124052a0975a94a38f0a519ab8e340ec318))
* Don't count empty patch selections in package badge ([e073ecf](https://github.com/MorpheApp/morphe-manager/commit/e073ecf279d5d605198a039b6325226f1d3feec2))
* Improve APK load error messages with distinct failure reasons ([3174f28](https://github.com/MorpheApp/morphe-manager/commit/3174f28480e1857ae689dee26806ed513ad980f9))
* Interrupt split APK merger immediately on cancellation ([0f7feca](https://github.com/MorpheApp/morphe-manager/commit/0f7fecabfd24ea588755c2ddfc2a4661c0783b83))
* Re-download bundle if version matches but createdAt differs ([2e77833](https://github.com/MorpheApp/morphe-manager/commit/2e77833cca08fb1c5c52bebe11dd032269099f9c))
* Refresh patch options only once on bundle load ([bf04846](https://github.com/MorpheApp/morphe-manager/commit/bf0484648ea76dfedbd778716fd75c65f1538f4f))
* Serialize `StringList` options based on patcher type ([8464f34](https://github.com/MorpheApp/morphe-manager/commit/8464f34f280b02beab4965dd62f3d9cfc3653979))
* Show failing bundle name in error toast and auto-disable bundles on fetch failure ([1c3a384](https://github.com/MorpheApp/morphe-manager/commit/1c3a3843f3989621764dfb8582e926170e686fa9))
* Show full patching log in error dialog when no specific error is captured ([f18d826](https://github.com/MorpheApp/morphe-manager/commit/f18d8267cc0b356139ce4a9299548d45097624d5))
* Show success toast after bundle import completes ([74d05cb](https://github.com/MorpheApp/morphe-manager/commit/74d05cb46d74ea489ad2c454706a9c0a4cf4a1c5))
* Skip disabled installed apps in AppDataResolver ([8eaa88b](https://github.com/MorpheApp/morphe-manager/commit/8eaa88bda6e4fe657924355eaed1b3fe87f045b1))
* Use `GetContent` instead of `OpenDocument` for APK/bundle pickers ([cb3551d](https://github.com/MorpheApp/morphe-manager/commit/cb3551d13ac490b2e74eb7ec111369e278e32efe))


### Features

* Add Android TV launcher support ([38f2703](https://github.com/MorpheApp/morphe-manager/commit/38f27030d4c80b1873af37c21454206fc86ec372))
* Add Expert badge to patch bundle viewer ([169ff75](https://github.com/MorpheApp/morphe-manager/commit/169ff751ba839b50aeebb03c07801688a8dd2cbe))
* Add import/export selection buttons in patch selection dialog ([c5b4ef6](https://github.com/MorpheApp/morphe-manager/commit/c5b4ef658e05a34198233ffe897e05499454ca18))
* Add saved selection button in expert mode dialog ([ee336d8](https://github.com/MorpheApp/morphe-manager/commit/ee336d865e71e4a597924a302326ef2d5c638805))
* Export/import third-party bundles with manager settings ([e5c826f](https://github.com/MorpheApp/morphe-manager/commit/e5c826fb81c725cb6ea3b614f2a04a924350f05a))
* Group compatible versions by bundle in APK availability dialog ([#432](https://github.com/MorpheApp/morphe-manager/issues/432)) ([362d097](https://github.com/MorpheApp/morphe-manager/commit/362d09744c51844774c1e9555580e0ed7fcdbfa1))
* Show bottom bar labels in main screen ([2d4fd8d](https://github.com/MorpheApp/morphe-manager/commit/2d4fd8d3c2180c9443e65c8d0a9c23bcb2586e13))
* Show update date for single default bundle in management sheet ([16e81bb](https://github.com/MorpheApp/morphe-manager/commit/16e81bbde5eff647742eee5414033a4bcce4c98d))

# [1.15.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.15.0-dev.4...v1.15.0-dev.5) (2026-04-16)


### Bug Fixes

* Re-download bundle if version matches but createdAt differs ([2e77833](https://github.com/MorpheApp/morphe-manager/commit/2e77833cca08fb1c5c52bebe11dd032269099f9c))
* Show failing bundle name in error toast and auto-disable bundles on fetch failure ([1c3a384](https://github.com/MorpheApp/morphe-manager/commit/1c3a3843f3989621764dfb8582e926170e686fa9))


### Features

* Export/import third-party bundles with manager settings ([e5c826f](https://github.com/MorpheApp/morphe-manager/commit/e5c826fb81c725cb6ea3b614f2a04a924350f05a))
* Group compatible versions by bundle in APK availability dialog ([#432](https://github.com/MorpheApp/morphe-manager/issues/432)) ([362d097](https://github.com/MorpheApp/morphe-manager/commit/362d09744c51844774c1e9555580e0ed7fcdbfa1))

# [1.15.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.15.0-dev.3...v1.15.0-dev.4) (2026-04-15)


### Bug Fixes

* Don't count empty patch selections in package badge ([e073ecf](https://github.com/MorpheApp/morphe-manager/commit/e073ecf279d5d605198a039b6325226f1d3feec2))
* Show success toast after bundle import completes ([74d05cb](https://github.com/MorpheApp/morphe-manager/commit/74d05cb46d74ea489ad2c454706a9c0a4cf4a1c5))
* Skip disabled installed apps in AppDataResolver ([8eaa88b](https://github.com/MorpheApp/morphe-manager/commit/8eaa88bda6e4fe657924355eaed1b3fe87f045b1))


### Features

* Add Android TV launcher support ([38f2703](https://github.com/MorpheApp/morphe-manager/commit/38f27030d4c80b1873af37c21454206fc86ec372))
* Show update date for single default bundle in management sheet ([16e81bb](https://github.com/MorpheApp/morphe-manager/commit/16e81bbde5eff647742eee5414033a4bcce4c98d))

# [1.15.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.15.0-dev.2...v1.15.0-dev.3) (2026-04-14)


### Bug Fixes

* Improve APK load error messages with distinct failure reasons ([3174f28](https://github.com/MorpheApp/morphe-manager/commit/3174f28480e1857ae689dee26806ed513ad980f9))
* Use `GetContent` instead of `OpenDocument` for APK/bundle pickers ([cb3551d](https://github.com/MorpheApp/morphe-manager/commit/cb3551d13ac490b2e74eb7ec111369e278e32efe))

# [1.15.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.15.0-dev.1...v1.15.0-dev.2) (2026-04-13)


### Bug Fixes

* Adjust wording ([482c1d1](https://github.com/MorpheApp/morphe-manager/commit/482c1d18945cfdc63bb54d7d112b0ec7ce4f58ba))
* Interrupt split APK merger immediately on cancellation ([0f7feca](https://github.com/MorpheApp/morphe-manager/commit/0f7fecabfd24ea588755c2ddfc2a4661c0783b83))


### Features

* Add saved selection button in expert mode dialog ([ee336d8](https://github.com/MorpheApp/morphe-manager/commit/ee336d865e71e4a597924a302326ef2d5c638805))

# [1.15.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.14.0...v1.15.0-dev.1) (2026-04-11)


### Bug Fixes

* Cancel patcher worker immediately on user cancellation ([4f0b312](https://github.com/MorpheApp/morphe-manager/commit/4f0b3124052a0975a94a38f0a519ab8e340ec318))
* Refresh patch options only once on bundle load ([bf04846](https://github.com/MorpheApp/morphe-manager/commit/bf0484648ea76dfedbd778716fd75c65f1538f4f))
* Show full patching log in error dialog when no specific error is captured ([f18d826](https://github.com/MorpheApp/morphe-manager/commit/f18d8267cc0b356139ce4a9299548d45097624d5))


### Features

* Add Expert badge to patch bundle viewer ([169ff75](https://github.com/MorpheApp/morphe-manager/commit/169ff751ba839b50aeebb03c07801688a8dd2cbe))
* Add import/export selection buttons in patch selection dialog ([c5b4ef6](https://github.com/MorpheApp/morphe-manager/commit/c5b4ef658e05a34198233ffe897e05499454ca18))

# [1.14.0](https://github.com/MorpheApp/morphe-manager/compare/v1.13.1...v1.14.0) (2026-04-08)


### Bug Fixes

* Add `stateDescription` to search button and Role.RadioButton to version list ([6daf288](https://github.com/MorpheApp/morphe-manager/commit/6daf2880453ee02545971b57176073877526dcd4))
* Add custom ModalBottomSheet ([ef7449c](https://github.com/MorpheApp/morphe-manager/commit/ef7449c28f4f9cead47dd8505f3b91aff0d9e693))
* Change the "default patches" icon in Expert dialog ([e929a14](https://github.com/MorpheApp/morphe-manager/commit/e929a1407c87241e96c5d81b73c24eb4deded451))
* Home screen buttons always showed shimmer when fresh install ([bbfaab8](https://github.com/MorpheApp/morphe-manager/commit/bbfaab8b5fb71bba403377436177f4df3dc26b74))
* Improve root mounting ([#381](https://github.com/MorpheApp/morphe-manager/issues/381)) ([257e433](https://github.com/MorpheApp/morphe-manager/commit/257e433a6a5a7aabebfa573081af17f6df7d9b7f))
* Increase delay before sending push notification ([6c393bc](https://github.com/MorpheApp/morphe-manager/commit/6c393bcc02bfd33f3bc34f8de535d2a6c3781d98))
* Move bundle update time to version line ([636e8cc](https://github.com/MorpheApp/morphe-manager/commit/636e8cceaf6cc05f337d0b36250c0609d610ee17))
* Parse comma-separated string options as editable lists ([b9a01d5](https://github.com/MorpheApp/morphe-manager/commit/b9a01d5f56220466b80dd5740a2a7a02930f19aa))
* Replace `UpdateBadge` overlay with inline chips in InstalledAppCard ([ce60eab](https://github.com/MorpheApp/morphe-manager/commit/ce60eab544b56c1ffa075a7e8c8d148d97b93daa))
* Show compatibility version description if available ([5524c86](https://github.com/MorpheApp/morphe-manager/commit/5524c863c16059e87332cb803197bc8699fc4bd0))
* Skip notification prompt in export if already requested ([59fca7e](https://github.com/MorpheApp/morphe-manager/commit/59fca7e19dac9ef79e8ad5f6d8f65901d62ae3ce))
* Use bundle metadata display name in patch dialog ([12d57fa](https://github.com/MorpheApp/morphe-manager/commit/12d57fa9e213f98bed0faf13805b7b09eee4ba58))
* Use card background as color preview, add transparency checkerboard ([#393](https://github.com/MorpheApp/morphe-manager/issues/393)) ([bb76510](https://github.com/MorpheApp/morphe-manager/commit/bb76510e9ad056e216ca5256659db72d4ea46cad))
* Use safe temp dir for APK, preserve input for root mount, and clean up temp files ([9353d65](https://github.com/MorpheApp/morphe-manager/commit/9353d6573294a6677abd1915c8f5d4df3076cd9d))


### Features

* Add home app search functionality ([#385](https://github.com/MorpheApp/morphe-manager/issues/385)) ([74b10be](https://github.com/MorpheApp/morphe-manager/commit/74b10be8c157b6d3cde637680496cb6dc95d4fa5))
* Add open-source library licenses dialog ([#383](https://github.com/MorpheApp/morphe-manager/issues/383)) ([2341678](https://github.com/MorpheApp/morphe-manager/commit/2341678e28e1cfc24018ea2f75c435eef27c7f79))
* Add search and package filter chips to bundle patches dialog ([#392](https://github.com/MorpheApp/morphe-manager/issues/392)) ([14b08f9](https://github.com/MorpheApp/morphe-manager/commit/14b08f9638a48fcd947df10d1ce8d51bc6acfc0c))
* Add selectable download version in APK availability expert dialog ([#391](https://github.com/MorpheApp/morphe-manager/issues/391)) ([9dc26c0](https://github.com/MorpheApp/morphe-manager/commit/9dc26c0346df68adee530a345a922a33fe3e6f74))
* Highlight new patches after bundle update in expert mode ([#394](https://github.com/MorpheApp/morphe-manager/issues/394)) ([90a315a](https://github.com/MorpheApp/morphe-manager/commit/90a315a914c4aed3e3713816a5cb8323782b063e))

# [1.14.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.14.0-dev.6...v1.14.0-dev.7) (2026-04-07)


### Bug Fixes

* Use safe temp dir for APK, preserve input for root mount, and clean up temp files ([9353d65](https://github.com/MorpheApp/morphe-manager/commit/9353d6573294a6677abd1915c8f5d4df3076cd9d))

# [1.14.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.14.0-dev.5...v1.14.0-dev.6) (2026-04-07)


### Bug Fixes

* Add `stateDescription` to search button and Role.RadioButton to version list ([6daf288](https://github.com/MorpheApp/morphe-manager/commit/6daf2880453ee02545971b57176073877526dcd4))
* Add custom ModalBottomSheet ([ef7449c](https://github.com/MorpheApp/morphe-manager/commit/ef7449c28f4f9cead47dd8505f3b91aff0d9e693))
* Change the "default patches" icon in Expert dialog ([e929a14](https://github.com/MorpheApp/morphe-manager/commit/e929a1407c87241e96c5d81b73c24eb4deded451))
* Move bundle update time to version line ([636e8cc](https://github.com/MorpheApp/morphe-manager/commit/636e8cceaf6cc05f337d0b36250c0609d610ee17))
* Replace `UpdateBadge` overlay with inline chips in InstalledAppCard ([ce60eab](https://github.com/MorpheApp/morphe-manager/commit/ce60eab544b56c1ffa075a7e8c8d148d97b93daa))
* Show compatibility version description if available ([5524c86](https://github.com/MorpheApp/morphe-manager/commit/5524c863c16059e87332cb803197bc8699fc4bd0))
* Skip notification prompt in export if already requested ([59fca7e](https://github.com/MorpheApp/morphe-manager/commit/59fca7e19dac9ef79e8ad5f6d8f65901d62ae3ce))
* Use bundle metadata display name in patch dialog ([12d57fa](https://github.com/MorpheApp/morphe-manager/commit/12d57fa9e213f98bed0faf13805b7b09eee4ba58))

# [1.14.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.14.0-dev.4...v1.14.0-dev.5) (2026-04-06)


### Features

* Highlight new patches after bundle update in expert mode ([#394](https://github.com/MorpheApp/morphe-manager/issues/394)) ([90a315a](https://github.com/MorpheApp/morphe-manager/commit/90a315a914c4aed3e3713816a5cb8323782b063e))

# [1.14.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.14.0-dev.3...v1.14.0-dev.4) (2026-04-03)


### Bug Fixes

* Use card background as color preview, add transparency checkerboard ([#393](https://github.com/MorpheApp/morphe-manager/issues/393)) ([bb76510](https://github.com/MorpheApp/morphe-manager/commit/bb76510e9ad056e216ca5256659db72d4ea46cad))


### Features

* Add search and package filter chips to bundle patches dialog ([#392](https://github.com/MorpheApp/morphe-manager/issues/392)) ([14b08f9](https://github.com/MorpheApp/morphe-manager/commit/14b08f9638a48fcd947df10d1ce8d51bc6acfc0c))
* Add selectable download version in APK availability expert dialog ([#391](https://github.com/MorpheApp/morphe-manager/issues/391)) ([9dc26c0](https://github.com/MorpheApp/morphe-manager/commit/9dc26c0346df68adee530a345a922a33fe3e6f74))

# [1.14.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.14.0-dev.2...v1.14.0-dev.3) (2026-04-02)


### Bug Fixes

* Improve root mounting ([#381](https://github.com/MorpheApp/morphe-manager/issues/381)) ([257e433](https://github.com/MorpheApp/morphe-manager/commit/257e433a6a5a7aabebfa573081af17f6df7d9b7f))

# [1.14.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.14.0-dev.1...v1.14.0-dev.2) (2026-04-01)


### Bug Fixes

* Home screen buttons always showed shimmer when fresh install ([bbfaab8](https://github.com/MorpheApp/morphe-manager/commit/bbfaab8b5fb71bba403377436177f4df3dc26b74))

# [1.14.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.13.1...v1.14.0-dev.1) (2026-04-01)


### Bug Fixes

* Increase delay before sending push notification ([6c393bc](https://github.com/MorpheApp/morphe-manager/commit/6c393bcc02bfd33f3bc34f8de535d2a6c3781d98))
* Parse comma-separated string options as editable lists ([b9a01d5](https://github.com/MorpheApp/morphe-manager/commit/b9a01d5f56220466b80dd5740a2a7a02930f19aa))


### Features

* Add home app search functionality ([#385](https://github.com/MorpheApp/morphe-manager/issues/385)) ([74b10be](https://github.com/MorpheApp/morphe-manager/commit/74b10be8c157b6d3cde637680496cb6dc95d4fa5))
* Add open-source library licenses dialog ([#383](https://github.com/MorpheApp/morphe-manager/issues/383)) ([2341678](https://github.com/MorpheApp/morphe-manager/commit/2341678e28e1cfc24018ea2f75c435eef27c7f79))

## app [1.13.1](https://github.com/MorpheApp/morphe-manager/compare/v1.13.0...v1.13.1) (2026-03-29)


### Bug Fixes

* Handle http redirects ([3026fb2](https://github.com/MorpheApp/morphe-manager/commit/3026fb2dd893d00034ce20074bdd93b848a11037))

## app [1.13.1-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.13.0...v1.13.1-dev.1) (2026-03-29)


### Bug Fixes

* Handle http redirects ([3026fb2](https://github.com/MorpheApp/morphe-manager/commit/3026fb2dd893d00034ce20074bdd93b848a11037))

# app [1.13.0](https://github.com/MorpheApp/morphe-manager/compare/v1.12.2...v1.13.0) (2026-03-28)


### Bug Fixes

* Correct download/install flow and state handling ([e2025ce](https://github.com/MorpheApp/morphe-manager/commit/e2025ce10691b0952ecd9fabe1339179ce89ff4e))
* Refactor `GitHubPullRequestBundle` to use our `HttpService`, allow using raw `.mpp` file from PR ([f50ae1d](https://github.com/MorpheApp/morphe-manager/commit/f50ae1d97c969ba20eee8ff2c539468f2f370820))
* Refactor `HttpService` and `MorpheApi` ([e105f60](https://github.com/MorpheApp/morphe-manager/commit/e105f6021f82061b15708722ff086357c59249c4))
* Replace `HttpURLConnection` with Ktor in `resolveRedirect` ([7470e6a](https://github.com/MorpheApp/morphe-manager/commit/7470e6a3d31792e63221f3ac7c0630d3344f3f5e))
* Skip APK signature verification for Android 10 and below ([6b2b591](https://github.com/MorpheApp/morphe-manager/commit/6b2b5913aa253cd9739aa2f8038b3ab70b57a0e8))


### Features

* Add notification icon creation ([#358](https://github.com/MorpheApp/morphe-manager/issues/358)) ([a096b85](https://github.com/MorpheApp/morphe-manager/commit/a096b85bd5ff7a6051f1cf0badaaaffddb95e572))
* Allow patching split APKs with a warning instead of blocking ([#353](https://github.com/MorpheApp/morphe-manager/issues/353)) ([2575368](https://github.com/MorpheApp/morphe-manager/commit/2575368e8054de9f63ce5e4bf0d005b4182e7a86))

# app [1.13.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.13.0-dev.2...v1.13.0-dev.3) (2026-03-24)


### Bug Fixes

* Skip APK signature verification for Android 10 and below ([6b2b591](https://github.com/MorpheApp/morphe-manager/commit/6b2b5913aa253cd9739aa2f8038b3ab70b57a0e8))

# app [1.13.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.13.0-dev.1...v1.13.0-dev.2) (2026-03-24)


### Features

* Add notification icon creation ([#358](https://github.com/MorpheApp/morphe-manager/issues/358)) ([a096b85](https://github.com/MorpheApp/morphe-manager/commit/a096b85bd5ff7a6051f1cf0badaaaffddb95e572))

# app [1.13.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.12.2...v1.13.0-dev.1) (2026-03-23)


### Features

* Allow patching split APKs with a warning instead of blocking ([#353](https://github.com/MorpheApp/morphe-manager/issues/353)) ([2575368](https://github.com/MorpheApp/morphe-manager/commit/2575368e8054de9f63ce5e4bf0d005b4182e7a86))

## app [1.12.2](https://github.com/MorpheApp/morphe-manager/compare/v1.12.1...v1.12.2) (2026-03-22)


### Bug Fixes

* Update to Patcher 1.3.2 ([4a17ab7](https://github.com/MorpheApp/morphe-manager/commit/4a17ab74231a497a015a692eac0e02bfc36b65bd))

## app [1.12.2-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.12.1...v1.12.2-dev.1) (2026-03-22)


### Bug Fixes

* Update to Patcher 1.3.2 ([4a17ab7](https://github.com/MorpheApp/morphe-manager/commit/4a17ab74231a497a015a692eac0e02bfc36b65bd))

## app [1.12.1](https://github.com/MorpheApp/morphe-manager/compare/v1.12.0...v1.12.1) (2026-03-22)


### Bug Fixes

* Update to Patcher 1.3.1 ([b517535](https://github.com/MorpheApp/morphe-manager/commit/b51753528e0b7ed4a5b11bb9b8df71ddbff0c8dd))

## app [1.12.1-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.12.0...v1.12.1-dev.1) (2026-03-22)


### Bug Fixes

* Update to Patcher 1.3.1 ([b517535](https://github.com/MorpheApp/morphe-manager/commit/b51753528e0b7ed4a5b11bb9b8df71ddbff0c8dd))

# app [1.12.0](https://github.com/MorpheApp/morphe-manager/compare/v1.11.0...v1.12.0) (2026-03-22)


### Bug Fixes

* Add list editor dialog for `List<String>` patch options ([#318](https://github.com/MorpheApp/morphe-manager/issues/318)) ([5b722d2](https://github.com/MorpheApp/morphe-manager/commit/5b722d27f979d32a18a04b0bbefb36b0add6e80d))
* Allow third-party universal patches in `Other Apps` flow ([#322](https://github.com/MorpheApp/morphe-manager/issues/322)) ([b888ff7](https://github.com/MorpheApp/morphe-manager/commit/b888ff77e58618dc9a5691ef0e96381092a227c5))
* Cache source avatars to prevent flicker on sheet reopen ([8e8a350](https://github.com/MorpheApp/morphe-manager/commit/8e8a35050d2da83736e0737e4d151e401f19696f))
* Prevent adding duplicate patch sources ([d616d7f](https://github.com/MorpheApp/morphe-manager/commit/d616d7f46f3f95424529992687152bd3f93740fa))
* Set default minimum process memory limit to 512MB ([c28aaae](https://github.com/MorpheApp/morphe-manager/commit/c28aaae9a63dbb4b0a084938ed4092cfdddb3f37))
* Use latest Morphe patcher ([cb53fbb](https://github.com/MorpheApp/morphe-manager/commit/cb53fbb5814d67730b7e53e940bcac49a9df671e))


### Features

* Group universal patches into separate section in ExpertModeDialog ([4c833da](https://github.com/MorpheApp/morphe-manager/commit/4c833da021d2e5a180cf39f9709b2d2f1ecce606))
* Parse CHANGELOG.md for changelogs ([84eb6ef](https://github.com/MorpheApp/morphe-manager/commit/84eb6efa5b78e1b0a881b29f311542f98ce1fd7d))
* Refine update badges using changelog scope matching ([#310](https://github.com/MorpheApp/morphe-manager/issues/310)) ([9b1cae7](https://github.com/MorpheApp/morphe-manager/commit/9b1cae7ddae4f8676b5e920a28ab1bcb4b424f34))
* Use interactive background animations ([#284](https://github.com/MorpheApp/morphe-manager/issues/284)) ([fca12bf](https://github.com/MorpheApp/morphe-manager/commit/fca12bf0e9b3e4614255de82bc60fd634e015f35))
* Use Morphe patcher 1.3.0 ([#329](https://github.com/MorpheApp/morphe-manager/issues/329)) ([344a06c](https://github.com/MorpheApp/morphe-manager/commit/344a06c43d46f7e6be1ca9292aea639a5677d542))

# app [1.12.0-dev.8](https://github.com/MorpheApp/morphe-manager/compare/v1.12.0-dev.7...v1.12.0-dev.8) (2026-03-21)


### Features

* Refine update badges using changelog scope matching ([#310](https://github.com/MorpheApp/morphe-manager/issues/310)) ([9b1cae7](https://github.com/MorpheApp/morphe-manager/commit/9b1cae7ddae4f8676b5e920a28ab1bcb4b424f34))

# app [1.12.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.12.0-dev.6...v1.12.0-dev.7) (2026-03-21)


### Features

* Use Morphe patcher 1.3.0 ([#329](https://github.com/MorpheApp/morphe-manager/issues/329)) ([344a06c](https://github.com/MorpheApp/morphe-manager/commit/344a06c43d46f7e6be1ca9292aea639a5677d542))

# app [1.12.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.12.0-dev.5...v1.12.0-dev.6) (2026-03-19)


### Bug Fixes

* Use latest Morphe patcher ([cb53fbb](https://github.com/MorpheApp/morphe-manager/commit/cb53fbb5814d67730b7e53e940bcac49a9df671e))

# app [1.12.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.12.0-dev.4...v1.12.0-dev.5) (2026-03-16)


### Bug Fixes

* Set default minimum process memory limit to 512MB ([c28aaae](https://github.com/MorpheApp/morphe-manager/commit/c28aaae9a63dbb4b0a084938ed4092cfdddb3f37))

# app [1.12.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.12.0-dev.3...v1.12.0-dev.4) (2026-03-13)


### Bug Fixes

* Allow third-party universal patches in `Other Apps` flow ([#322](https://github.com/MorpheApp/morphe-manager/issues/322)) ([b888ff7](https://github.com/MorpheApp/morphe-manager/commit/b888ff77e58618dc9a5691ef0e96381092a227c5))

# app [1.12.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.12.0-dev.2...v1.12.0-dev.3) (2026-03-13)


### Bug Fixes

* Add list editor dialog for `List<String>` patch options ([#318](https://github.com/MorpheApp/morphe-manager/issues/318)) ([5b722d2](https://github.com/MorpheApp/morphe-manager/commit/5b722d27f979d32a18a04b0bbefb36b0add6e80d))

# app [1.12.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.12.0-dev.1...v1.12.0-dev.2) (2026-03-08)


### Features

* Use interactive background animations ([#284](https://github.com/MorpheApp/morphe-manager/issues/284)) ([fca12bf](https://github.com/MorpheApp/morphe-manager/commit/fca12bf0e9b3e4614255de82bc60fd634e015f35))

# app [1.12.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.11.0...v1.12.0-dev.1) (2026-03-08)


### Bug Fixes

* Cache source avatars to prevent flicker on sheet reopen ([8e8a350](https://github.com/MorpheApp/morphe-manager/commit/8e8a35050d2da83736e0737e4d151e401f19696f))
* Prevent adding duplicate patch sources ([d616d7f](https://github.com/MorpheApp/morphe-manager/commit/d616d7f46f3f95424529992687152bd3f93740fa))


### Features

* Group universal patches into separate section in ExpertModeDialog ([4c833da](https://github.com/MorpheApp/morphe-manager/commit/4c833da021d2e5a180cf39f9709b2d2f1ecce606))
* Parse CHANGELOG.md for changelogs ([84eb6ef](https://github.com/MorpheApp/morphe-manager/commit/84eb6efa5b78e1b0a881b29f311542f98ce1fd7d))

# app [1.11.0](https://github.com/MorpheApp/morphe-manager/compare/v1.10.2...v1.11.0) (2026-03-07)


### Bug Fixes

* Root installation fails if module path does not exist ([#282](https://github.com/MorpheApp/morphe-manager/issues/282)) ([3405802](https://github.com/MorpheApp/morphe-manager/commit/3405802d37247596d0747f00e6a98f5a10cc9c9a))
* The language selection list is empty ([db69dad](https://github.com/MorpheApp/morphe-manager/commit/db69dadbca9fe2c396719a93914600192b8affae))


### Features

* Add deep link support ([#290](https://github.com/MorpheApp/morphe-manager/issues/290)) ([3b57efb](https://github.com/MorpheApp/morphe-manager/commit/3b57efb170e56eea1821dcbf2c49dcc2b795763a))
* add Kurmanji (kmr-TR) language support ([516c200](https://github.com/MorpheApp/morphe-manager/commit/516c2001ebac4b6530d7560bf2797270a0d7942c))
* Improve information in exported manager logs ([#279](https://github.com/MorpheApp/morphe-manager/issues/279)) ([2c0c344](https://github.com/MorpheApp/morphe-manager/commit/2c0c3447d647292ec17f9c9c55145811a732a78e))
* Use Morphe patcher 1.2.0 ([#231](https://github.com/MorpheApp/morphe-manager/issues/231)) ([944a3ab](https://github.com/MorpheApp/morphe-manager/commit/944a3ab7fab2d689b81f5d8e6bf5224660ce11ef))

# app [1.11.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.11.0-dev.5...v1.11.0-dev.6) (2026-03-07)


### Features

* Add deep link support ([#290](https://github.com/MorpheApp/morphe-manager/issues/290)) ([3b57efb](https://github.com/MorpheApp/morphe-manager/commit/3b57efb170e56eea1821dcbf2c49dcc2b795763a))

# app [1.11.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.11.0-dev.4...v1.11.0-dev.5) (2026-03-07)


### Features

* add Kurmanji (kmr-TR) language support ([516c200](https://github.com/MorpheApp/morphe-manager/commit/516c2001ebac4b6530d7560bf2797270a0d7942c))

# app [1.11.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.11.0-dev.3...v1.11.0-dev.4) (2026-03-05)


### Bug Fixes

* The language selection list is empty ([db69dad](https://github.com/MorpheApp/morphe-manager/commit/db69dadbca9fe2c396719a93914600192b8affae))

# app [1.11.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.11.0-dev.2...v1.11.0-dev.3) (2026-03-05)


### Bug Fixes

* Root installation fails if module path does not exist ([#282](https://github.com/MorpheApp/morphe-manager/issues/282)) ([3405802](https://github.com/MorpheApp/morphe-manager/commit/3405802d37247596d0747f00e6a98f5a10cc9c9a))

# app [1.11.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.11.0-dev.1...v1.11.0-dev.2) (2026-03-04)


### Features

* Improve information in exported manager logs ([#279](https://github.com/MorpheApp/morphe-manager/issues/279)) ([2c0c344](https://github.com/MorpheApp/morphe-manager/commit/2c0c3447d647292ec17f9c9c55145811a732a78e))

# app [1.11.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.10.2...v1.11.0-dev.1) (2026-03-03)


### Features

* Use Morphe patcher 1.2.0 ([#231](https://github.com/MorpheApp/morphe-manager/issues/231)) ([944a3ab](https://github.com/MorpheApp/morphe-manager/commit/944a3ab7fab2d689b81f5d8e6bf5224660ce11ef))

## app [1.10.2](https://github.com/MorpheApp/morphe-manager/compare/v1.10.1...v1.10.2) (2026-03-02)


### Bug Fixes

* Manager does not show updates if available ([#270](https://github.com/MorpheApp/morphe-manager/issues/270)) ([4e6f2af](https://github.com/MorpheApp/morphe-manager/commit/4e6f2afee34894c271903b6ea18a2b1a2cfe5ee1))

## app [1.10.2-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.10.1...v1.10.2-dev.1) (2026-03-02)


### Bug Fixes

* Manager does not show updates if available ([#270](https://github.com/MorpheApp/morphe-manager/issues/270)) ([4e6f2af](https://github.com/MorpheApp/morphe-manager/commit/4e6f2afee34894c271903b6ea18a2b1a2cfe5ee1))

## app [1.10.1](https://github.com/MorpheApp/morphe-manager/compare/v1.10.0...v1.10.1) (2026-03-02)


### Bug Fixes

* Custom header does not apply to Youtube Music in simple mode ([88ed0d1](https://github.com/MorpheApp/morphe-manager/commit/88ed0d1c891c01cb5d6b81e2d4f1509f3d1cb6a2))

## app [1.10.1-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.10.0...v1.10.1-dev.1) (2026-03-02)


### Bug Fixes

* Custom header does not apply to Youtube Music in simple mode ([88ed0d1](https://github.com/MorpheApp/morphe-manager/commit/88ed0d1c891c01cb5d6b81e2d4f1509f3d1cb6a2))

# app [1.10.0](https://github.com/MorpheApp/morphe-manager/compare/v1.9.0...v1.10.0) (2026-03-02)


### Features

* Support YT Music change header option ([#264](https://github.com/MorpheApp/morphe-manager/issues/264)) ([3d11e21](https://github.com/MorpheApp/morphe-manager/commit/3d11e21e37ee225750d43204b8876d598d764f63))

# app [1.10.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.9.0...v1.10.0-dev.1) (2026-03-01)


### Features

* Support YT Music change header option ([#264](https://github.com/MorpheApp/morphe-manager/issues/264)) ([3d11e21](https://github.com/MorpheApp/morphe-manager/commit/3d11e21e37ee225750d43204b8876d598d764f63))

# app [1.9.0](https://github.com/MorpheApp/morphe-manager/compare/v1.8.0...v1.9.0) (2026-03-01)


### Bug Fixes

* Add missing permission to app manifest ([c439f71](https://github.com/MorpheApp/morphe-manager/commit/c439f7177576dbdebae770a74b963c4e590b9b68))
* Pre-release toggle is enabled if user adds link to dev branch ([28417d0](https://github.com/MorpheApp/morphe-manager/commit/28417d06d78035c86bf1ac53367ef68a594c6f63))
* Remove UI stuttering during APK write when patching in-process ([#258](https://github.com/MorpheApp/morphe-manager/issues/258)) ([99f1a62](https://github.com/MorpheApp/morphe-manager/commit/99f1a6268f26f66a56afdf181a1fe9c4d0af05c1))


### Features

* Add Expert mode patching screen ([#250](https://github.com/MorpheApp/morphe-manager/issues/250)) ([5efa637](https://github.com/MorpheApp/morphe-manager/commit/5efa6374cb135e4585f0b4aab10ca5dd7039ebd6))
* Enhance patch update management and mobile data controls ([#247](https://github.com/MorpheApp/morphe-manager/issues/247)) ([5ddfa22](https://github.com/MorpheApp/morphe-manager/commit/5ddfa224e9c9288b35a2aa8ec739a6a72234fef0))

# app [1.9.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.9.0-dev.3...v1.9.0-dev.4) (2026-02-28)


### Features

* Add Expert mode patching screen ([#250](https://github.com/MorpheApp/morphe-manager/issues/250)) ([5efa637](https://github.com/MorpheApp/morphe-manager/commit/5efa6374cb135e4585f0b4aab10ca5dd7039ebd6))

# app [1.9.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.9.0-dev.2...v1.9.0-dev.3) (2026-02-28)


### Bug Fixes

* Remove UI stuttering during APK write when patching in-process ([#258](https://github.com/MorpheApp/morphe-manager/issues/258)) ([99f1a62](https://github.com/MorpheApp/morphe-manager/commit/99f1a6268f26f66a56afdf181a1fe9c4d0af05c1))

# app [1.9.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.9.0-dev.1...v1.9.0-dev.2) (2026-02-27)


### Bug Fixes

* Pre-release toggle is enabled if user adds link to dev branch ([28417d0](https://github.com/MorpheApp/morphe-manager/commit/28417d06d78035c86bf1ac53367ef68a594c6f63))

# app [1.9.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.8.0...v1.9.0-dev.1) (2026-02-27)


### Bug Fixes

* Add missing permission to app manifest ([c439f71](https://github.com/MorpheApp/morphe-manager/commit/c439f7177576dbdebae770a74b963c4e590b9b68))


### Features

* Enhance patch update management and mobile data controls ([#247](https://github.com/MorpheApp/morphe-manager/issues/247)) ([5ddfa22](https://github.com/MorpheApp/morphe-manager/commit/5ddfa224e9c9288b35a2aa8ec739a6a72234fef0))

# app [1.8.0](https://github.com/MorpheApp/morphe-manager/compare/v1.7.1...v1.8.0) (2026-02-25)


### Bug Fixes

* Change "help me find apk" dialog "yes" button to open web search ([#237](https://github.com/MorpheApp/morphe-manager/issues/237)) ([b61daee](https://github.com/MorpheApp/morphe-manager/commit/b61daee7b16dbbf5e5c5b7703fb18d555210c2f7))
* Manager crashes if the storage path cannot be accessed ([#225](https://github.com/MorpheApp/morphe-manager/issues/225)) ([896a598](https://github.com/MorpheApp/morphe-manager/commit/896a5989ffac134e1ed86df7c333a639330c2c86))
* When source is disabled allow button presses but change the card background to red ([9b3a498](https://github.com/MorpheApp/morphe-manager/commit/9b3a49831241cf19c4e1d21474f33b6a4060b4af))


### Features

* Add tab layout in Expert mode dialog ([#241](https://github.com/MorpheApp/morphe-manager/issues/241)) ([2671a5e](https://github.com/MorpheApp/morphe-manager/commit/2671a5e9b73c9458bdb76cd73c432afba40a7bfe))
* Show all patched apps on homescreen ([#232](https://github.com/MorpheApp/morphe-manager/issues/232)) ([a265801](https://github.com/MorpheApp/morphe-manager/commit/a2658012c2e994b1b587bbb2494c7c097056079a))
* Show Android notifications when patch and manager updates are available ([#217](https://github.com/MorpheApp/morphe-manager/issues/217)) ([dced36b](https://github.com/MorpheApp/morphe-manager/commit/dced36be9357ba012b1bb128c3c94e8528570f83))
* Show changelog button in all changelog dialogs ([f925de0](https://github.com/MorpheApp/morphe-manager/commit/f925de0351609248465dd4dd1bafe2f1c7a35794))
* Update translations from Crowdin ([a873061](https://github.com/MorpheApp/morphe-manager/commit/a873061859bb22944228c82d01b7e824917353e6))

# app [1.8.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.8.0-dev.6...v1.8.0-dev.7) (2026-02-25)


### Features

* Add tab layout in Expert mode dialog ([#241](https://github.com/MorpheApp/morphe-manager/issues/241)) ([2671a5e](https://github.com/MorpheApp/morphe-manager/commit/2671a5e9b73c9458bdb76cd73c432afba40a7bfe))

# app [1.8.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.8.0-dev.5...v1.8.0-dev.6) (2026-02-23)


### Features

* Update translations from Crowdin ([a873061](https://github.com/MorpheApp/morphe-manager/commit/a873061859bb22944228c82d01b7e824917353e6))

# app [1.8.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.8.0-dev.4...v1.8.0-dev.5) (2026-02-23)


### Bug Fixes

* Change "help me find apk" dialog "yes" button to open web search ([#237](https://github.com/MorpheApp/morphe-manager/issues/237)) ([b61daee](https://github.com/MorpheApp/morphe-manager/commit/b61daee7b16dbbf5e5c5b7703fb18d555210c2f7))

# app [1.8.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.8.0-dev.3...v1.8.0-dev.4) (2026-02-23)


### Features

* Show all patched apps on homescreen ([#232](https://github.com/MorpheApp/morphe-manager/issues/232)) ([a265801](https://github.com/MorpheApp/morphe-manager/commit/a2658012c2e994b1b587bbb2494c7c097056079a))

# app [1.8.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.8.0-dev.2...v1.8.0-dev.3) (2026-02-20)


### Bug Fixes

* Manager crashes if the storage path cannot be accessed ([#225](https://github.com/MorpheApp/morphe-manager/issues/225)) ([896a598](https://github.com/MorpheApp/morphe-manager/commit/896a5989ffac134e1ed86df7c333a639330c2c86))

# app [1.8.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.8.0-dev.1...v1.8.0-dev.2) (2026-02-19)


### Features

* Show Android notifications when patch and manager updates are available ([#217](https://github.com/MorpheApp/morphe-manager/issues/217)) ([dced36b](https://github.com/MorpheApp/morphe-manager/commit/dced36be9357ba012b1bb128c3c94e8528570f83))

# app [1.8.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.7.1...v1.8.0-dev.1) (2026-02-16)


### Bug Fixes

* When source is disabled allow button presses but change the card background to red ([9b3a498](https://github.com/MorpheApp/morphe-manager/commit/9b3a49831241cf19c4e1d21474f33b6a4060b4af))


### Features

* Show changelog button in all changelog dialogs ([f925de0](https://github.com/MorpheApp/morphe-manager/commit/f925de0351609248465dd4dd1bafe2f1c7a35794))

## app [1.7.1](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0...v1.7.1) (2026-02-16)


### Bug Fixes

* Update translations from Crowdin ([e0d7db9](https://github.com/MorpheApp/morphe-manager/commit/e0d7db9e95cac5b7feb0d26a22a9eb898a31791a))

# app [1.7.0](https://github.com/MorpheApp/morphe-manager/compare/v1.6.0...v1.7.0) (2026-02-16)


### Bug Fixes

* Get patches release info from static JSON file ([a33ba20](https://github.com/MorpheApp/morphe-manager/commit/a33ba2053a75826eec6c106611ba9e5f8276ed0c))
* Improve patch dialog logic and fix app info display issues ([#182](https://github.com/MorpheApp/morphe-manager/issues/182)) ([a3153e9](https://github.com/MorpheApp/morphe-manager/commit/a3153e91a6c609a71dbc7850e5edc57f1394f915))
* Incorrect content color for badge style ([f4ad9aa](https://github.com/MorpheApp/morphe-manager/commit/f4ad9aaa70d827ab5d511ed63c0eb7bebb999f07))
* Increase default process memory ([557ff78](https://github.com/MorpheApp/morphe-manager/commit/557ff784d8ac223de35ed52ddf20dc9aa62125d5))
* Prefer IPv4 connections over IPv6 ([e665e59](https://github.com/MorpheApp/morphe-manager/commit/e665e595ac50fb7925fd6814ce89beebcd4fe453))
* Refactor changelog component and add shimmer effect ([44d0318](https://github.com/MorpheApp/morphe-manager/commit/44d03189c2e0f1ee1a6971b7cd05f8d5b98a7651))
* Some patch options fields are not available for input ([5853168](https://github.com/MorpheApp/morphe-manager/commit/585316830a235a3d27781fe191a692c5d28ea6dc))
* Update app-release.json after semantic release finishes ([77db06d](https://github.com/MorpheApp/morphe-manager/commit/77db06d0169eb7cece281d6223b2e472113d1631))


### Features

* Get manager release info from static JSON file ([#186](https://github.com/MorpheApp/morphe-manager/issues/186)) ([c75569d](https://github.com/MorpheApp/morphe-manager/commit/c75569df4f5b6d85acf0ad6e4385f6320fc7b0a8))
* New patch selections dialog ([#197](https://github.com/MorpheApp/morphe-manager/issues/197)) ([9f363ff](https://github.com/MorpheApp/morphe-manager/commit/9f363ff85aad65a8c6bcbb4d8ea20b2c2ba34374))
* Show Expert mode confirmation dialog ([db64938](https://github.com/MorpheApp/morphe-manager/commit/db64938470193e0c5a3b615ab09694f028a39236))
* Use APKEditor for APKM merging ([#137](https://github.com/MorpheApp/morphe-manager/issues/137)) ([9ed8f5b](https://github.com/MorpheApp/morphe-manager/commit/9ed8f5b0145cfed48662eb4da385525fe29cfe2a))

# app [1.7.0-dev.18](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.17...v1.7.0-dev.18) (2026-02-16)


### Features

* Use APKEditor for APKM merging ([#137](https://github.com/MorpheApp/morphe-manager/issues/137)) ([9ed8f5b](https://github.com/MorpheApp/morphe-manager/commit/9ed8f5b0145cfed48662eb4da385525fe29cfe2a))

# app [1.7.0-dev.17](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.16...v1.7.0-dev.17) (2026-02-15)


### Bug Fixes

* Some patch options fields are not available for input ([5853168](https://github.com/MorpheApp/morphe-manager/commit/585316830a235a3d27781fe191a692c5d28ea6dc))

# app [1.7.0-dev.16](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.15...v1.7.0-dev.16) (2026-02-15)


### Bug Fixes

* Resolve incorrect string formatters for some translations ([13d0b8c](https://github.com/MorpheApp/morphe-manager/commit/13d0b8ce8fefe8ee1d6cfda3c6a57ba1e8aa4376))

# app [1.7.0-dev.15](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.14...v1.7.0-dev.15) (2026-02-15)


### Features

* Show Expert mode confirmation dialog ([db64938](https://github.com/MorpheApp/morphe-manager/commit/db64938470193e0c5a3b615ab09694f028a39236))

# app [1.7.0-dev.14](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.13...v1.7.0-dev.14) (2026-02-15)


### Bug Fixes

* Increase default process memory ([557ff78](https://github.com/MorpheApp/morphe-manager/commit/557ff784d8ac223de35ed52ddf20dc9aa62125d5))

# app [1.7.0-dev.13](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.12...v1.7.0-dev.13) (2026-02-15)


### Bug Fixes

* Resolve Morphe showing new release is available but cannot download ([c567195](https://github.com/MorpheApp/morphe-manager/commit/c567195dbae13ca1a9931edb1d472c77ecca9942))

# app [1.7.0-dev.12](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.11...v1.7.0-dev.12) (2026-02-15)


### Bug Fixes

* Better handle opening APKMirror links on weirdo home routers ([627f075](https://github.com/MorpheApp/morphe-manager/commit/627f07571646ea5bda5b60f7198b9f77ed4be27b))

# app [1.7.0-dev.11](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.10...v1.7.0-dev.11) (2026-02-15)


### Bug Fixes

* Update release build ([0d563e6](https://github.com/MorpheApp/morphe-manager/commit/0d563e65e6c15d121588da190d4b9c7dd8d891cd))

# app [1.7.0-dev.10](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.9...v1.7.0-dev.10) (2026-02-15)


### Bug Fixes

* Update app-release.json after semantic release finishes ([77db06d](https://github.com/MorpheApp/morphe-manager/commit/77db06d0169eb7cece281d6223b2e472113d1631))

# app [1.7.0-dev.9](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.8...v1.7.0-dev.9) (2026-02-14)


### Bug Fixes

* Get patches release info from static JSON file ([a33ba20](https://github.com/MorpheApp/morphe-manager/commit/a33ba2053a75826eec6c106611ba9e5f8276ed0c))

# app [1.7.0-dev.8](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.7...v1.7.0-dev.8) (2026-02-14)


### Bug Fixes

* Prefer IPv4 connections over IPv6 ([e665e59](https://github.com/MorpheApp/morphe-manager/commit/e665e595ac50fb7925fd6814ce89beebcd4fe453))

# app [1.7.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.6...v1.7.0-dev.7) (2026-02-14)


### Bug Fixes

* Change to old GitHub release logic ([94c2fb7](https://github.com/MorpheApp/morphe-manager/commit/94c2fb70f389daff493dbc2deb02c0c906940407))

# app [1.7.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.5...v1.7.0-dev.6) (2026-02-14)


### Features

* New patch selections dialog ([#197](https://github.com/MorpheApp/morphe-manager/issues/197)) ([9f363ff](https://github.com/MorpheApp/morphe-manager/commit/9f363ff85aad65a8c6bcbb4d8ea20b2c2ba34374))

# app [1.7.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.4...v1.7.0-dev.5) (2026-02-13)


### Bug Fixes

* Commit app-release.json after semantic release ([4f89c8c](https://github.com/MorpheApp/morphe-manager/commit/4f89c8c5e02c375f04f0540b94cb4b1a817c120f))

# app [1.7.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.3...v1.7.0-dev.4) (2026-02-13)


### Bug Fixes

* Publish release before updating app-release.json ([f1c556a](https://github.com/MorpheApp/morphe-manager/commit/f1c556aa7b9d163297152c53b348f87a6960fe1a))

# app [1.7.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.2...v1.7.0-dev.3) (2026-02-11)


### Bug Fixes

* Refactor changelog component and add shimmer effect ([44d0318](https://github.com/MorpheApp/morphe-manager/commit/44d03189c2e0f1ee1a6971b7cd05f8d5b98a7651))

# app [1.7.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.7.0-dev.1...v1.7.0-dev.2) (2026-02-11)


### Bug Fixes

* Incorrect content color for badge style ([f4ad9aa](https://github.com/MorpheApp/morphe-manager/commit/f4ad9aaa70d827ab5d511ed63c0eb7bebb999f07))

# app [1.7.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.6.0...v1.7.0-dev.1) (2026-02-11)


### Bug Fixes

* Improve patch dialog logic and fix app info display issues ([#182](https://github.com/MorpheApp/morphe-manager/issues/182)) ([a3153e9](https://github.com/MorpheApp/morphe-manager/commit/a3153e91a6c609a71dbc7850e5edc57f1394f915))


### Features

* Get manager release info from static JSON file ([#186](https://github.com/MorpheApp/morphe-manager/issues/186)) ([c75569d](https://github.com/MorpheApp/morphe-manager/commit/c75569df4f5b6d85acf0ad6e4385f6320fc7b0a8))

# app [1.6.0](https://github.com/MorpheApp/morphe-manager/compare/v1.5.0...v1.6.0) (2026-02-08)


### Bug Fixes

* Add validation of patch selections when repatching ([#174](https://github.com/MorpheApp/morphe-manager/issues/174)) ([2dba355](https://github.com/MorpheApp/morphe-manager/commit/2dba3556c8c4b6f6db2eb596c2c1df53afb2b8ef))
* Create adaptive icons for all resolutions ([#171](https://github.com/MorpheApp/morphe-manager/issues/171)) ([f22aa3f](https://github.com/MorpheApp/morphe-manager/commit/f22aa3f343cc34270030dc52f347518e40bde322))
* File system access request appeared where it wasn't needed ([#179](https://github.com/MorpheApp/morphe-manager/issues/179)) ([79fcec2](https://github.com/MorpheApp/morphe-manager/commit/79fcec271e9e3633cdb0ff4cbfc3689ec925db6f))
* Show banner when patched app was has been uninstalled from the device ([562f23e](https://github.com/MorpheApp/morphe-manager/commit/562f23e845c18c5a6e4d34f631bec6ad095c77b8))


### Features

* Add an extended list of supported versions ([#155](https://github.com/MorpheApp/morphe-manager/issues/155)) ([25dafb6](https://github.com/MorpheApp/morphe-manager/commit/25dafb685b5dbc28ae6d8c64553ac9a2d5a7527f))
* Add GitHub repo as a patch bundle source ([#157](https://github.com/MorpheApp/morphe-manager/issues/157)) ([ec0f741](https://github.com/MorpheApp/morphe-manager/commit/ec0f7415964d6982925d7c097535d9032af1330a))
* Add installer prompt on patcher screen ([#170](https://github.com/MorpheApp/morphe-manager/issues/170)) ([b93719b](https://github.com/MorpheApp/morphe-manager/commit/b93719b9181f44760648fc1a788e4dd9ca3a8580))
* Add optional parallax effect to animated backgrounds ([#169](https://github.com/MorpheApp/morphe-manager/issues/169)) ([bc28fa9](https://github.com/MorpheApp/morphe-manager/commit/bc28fa9f5ea90cf2db3f6a485279e65cddff5438))
* Add stored patch selection dialog ([#167](https://github.com/MorpheApp/morphe-manager/issues/167)) ([c36b424](https://github.com/MorpheApp/morphe-manager/commit/c36b42436ddde47b6bcea8c8f3b5a8eea50137ac))

# app [1.6.0-dev.8](https://github.com/MorpheApp/morphe-manager/compare/v1.6.0-dev.7...v1.6.0-dev.8) (2026-02-08)


### Bug Fixes

* File system access request appeared where it wasn't needed ([#179](https://github.com/MorpheApp/morphe-manager/issues/179)) ([79fcec2](https://github.com/MorpheApp/morphe-manager/commit/79fcec271e9e3633cdb0ff4cbfc3689ec925db6f))

# app [1.6.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.6.0-dev.6...v1.6.0-dev.7) (2026-02-07)


### Bug Fixes

* Show banner when patched app was has been uninstalled from the device ([562f23e](https://github.com/MorpheApp/morphe-manager/commit/562f23e845c18c5a6e4d34f631bec6ad095c77b8))

# app [1.6.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.6.0-dev.5...v1.6.0-dev.6) (2026-02-06)


### Bug Fixes

* Add validation of patch selections when repatching ([#174](https://github.com/MorpheApp/morphe-manager/issues/174)) ([2dba355](https://github.com/MorpheApp/morphe-manager/commit/2dba3556c8c4b6f6db2eb596c2c1df53afb2b8ef))

# app [1.6.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.6.0-dev.4...v1.6.0-dev.5) (2026-02-06)


### Bug Fixes

* Create adaptive icons for all resolutions ([#171](https://github.com/MorpheApp/morphe-manager/issues/171)) ([f22aa3f](https://github.com/MorpheApp/morphe-manager/commit/f22aa3f343cc34270030dc52f347518e40bde322))

# app [1.6.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.6.0-dev.3...v1.6.0-dev.4) (2026-02-06)


### Features

* Add installer prompt on patcher screen ([#170](https://github.com/MorpheApp/morphe-manager/issues/170)) ([b93719b](https://github.com/MorpheApp/morphe-manager/commit/b93719b9181f44760648fc1a788e4dd9ca3a8580))

# app [1.6.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.6.0-dev.2...v1.6.0-dev.3) (2026-02-06)


### Features

* Add optional parallax effect to animated backgrounds ([#169](https://github.com/MorpheApp/morphe-manager/issues/169)) ([bc28fa9](https://github.com/MorpheApp/morphe-manager/commit/bc28fa9f5ea90cf2db3f6a485279e65cddff5438))

# app [1.6.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.6.0-dev.1...v1.6.0-dev.2) (2026-02-05)


### Features

* Add GitHub repo as a patch bundle source ([#157](https://github.com/MorpheApp/morphe-manager/issues/157)) ([ec0f741](https://github.com/MorpheApp/morphe-manager/commit/ec0f7415964d6982925d7c097535d9032af1330a))
* Add stored patch selection dialog ([#167](https://github.com/MorpheApp/morphe-manager/issues/167)) ([c36b424](https://github.com/MorpheApp/morphe-manager/commit/c36b42436ddde47b6bcea8c8f3b5a8eea50137ac))

# app [1.6.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.5.0...v1.6.0-dev.1) (2026-02-04)


### Features

* Add an extended list of supported versions ([#155](https://github.com/MorpheApp/morphe-manager/issues/155)) ([25dafb6](https://github.com/MorpheApp/morphe-manager/commit/25dafb685b5dbc28ae6d8c64553ac9a2d5a7527f))

# app [1.5.0](https://github.com/MorpheApp/morphe-manager/compare/v1.4.1...v1.5.0) (2026-02-04)


### Bug Fixes

* Refactor app installation code ([#149](https://github.com/MorpheApp/morphe-manager/issues/149)) ([119d112](https://github.com/MorpheApp/morphe-manager/commit/119d11258148fab6e91ef7d6c2df6edda8d03754))
* Resolve "No Activity found" crash ([#148](https://github.com/MorpheApp/morphe-manager/issues/148)) ([12ec590](https://github.com/MorpheApp/morphe-manager/commit/12ec590cc147d2742e224221b5282e082edf3053))
* UX improvements ([#147](https://github.com/MorpheApp/morphe-manager/issues/147)) ([0029e51](https://github.com/MorpheApp/morphe-manager/commit/0029e51ff931b74278e6120b8249371e9e0a5056))


### Features

* Add pull-to-refresh gesture ([#143](https://github.com/MorpheApp/morphe-manager/issues/143)) ([50525f0](https://github.com/MorpheApp/morphe-manager/commit/50525f0183440b4a4798d0520f5f415b9e569900))
* Add updating sources progress bar ([#152](https://github.com/MorpheApp/morphe-manager/issues/152)) ([8fd353f](https://github.com/MorpheApp/morphe-manager/commit/8fd353f3ef53da81f1151132318e832713156628))
* **Custom branding:** Allow Manager to process custom icon/headers into the correct formats/names/sizes ([#138](https://github.com/MorpheApp/morphe-manager/issues/138)) ([b5e6c82](https://github.com/MorpheApp/morphe-manager/commit/b5e6c82745c5441e17272850ed0b47cd525b514b))
* Show homescreen app update badges ([#132](https://github.com/MorpheApp/morphe-manager/issues/132)) ([b8adadf](https://github.com/MorpheApp/morphe-manager/commit/b8adadf782e49f55ffc1323dcc15dd6a461abd81))

# app [1.5.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.5.0-dev.6...v1.5.0-dev.7) (2026-02-03)


### Bug Fixes

* Resolve "No Activity found" crash ([#148](https://github.com/MorpheApp/morphe-manager/issues/148)) ([12ec590](https://github.com/MorpheApp/morphe-manager/commit/12ec590cc147d2742e224221b5282e082edf3053))

# app [1.5.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.5.0-dev.5...v1.5.0-dev.6) (2026-02-03)


### Bug Fixes

* Refactor app installation code ([#149](https://github.com/MorpheApp/morphe-manager/issues/149)) ([119d112](https://github.com/MorpheApp/morphe-manager/commit/119d11258148fab6e91ef7d6c2df6edda8d03754))

# app [1.5.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.5.0-dev.4...v1.5.0-dev.5) (2026-02-02)


### Features

* Add updating sources progress bar ([#152](https://github.com/MorpheApp/morphe-manager/issues/152)) ([8fd353f](https://github.com/MorpheApp/morphe-manager/commit/8fd353f3ef53da81f1151132318e832713156628))

# app [1.5.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.5.0-dev.3...v1.5.0-dev.4) (2026-02-02)


### Features

* **Custom branding:** Allow Manager to process custom icon/headers into the correct formats/names/sizes ([#138](https://github.com/MorpheApp/morphe-manager/issues/138)) ([b5e6c82](https://github.com/MorpheApp/morphe-manager/commit/b5e6c82745c5441e17272850ed0b47cd525b514b))

# app [1.5.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.5.0-dev.2...v1.5.0-dev.3) (2026-02-02)


### Bug Fixes

* UX improvements ([#147](https://github.com/MorpheApp/morphe-manager/issues/147)) ([0029e51](https://github.com/MorpheApp/morphe-manager/commit/0029e51ff931b74278e6120b8249371e9e0a5056))

# app [1.5.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.5.0-dev.1...v1.5.0-dev.2) (2026-02-01)


### Features

* Add pull-to-refresh gesture ([#143](https://github.com/MorpheApp/morphe-manager/issues/143)) ([50525f0](https://github.com/MorpheApp/morphe-manager/commit/50525f0183440b4a4798d0520f5f415b9e569900))

# app [1.5.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.4.1...v1.5.0-dev.1) (2026-02-01)


### Features

* Show homescreen app update badges ([#132](https://github.com/MorpheApp/morphe-manager/issues/132)) ([b8adadf](https://github.com/MorpheApp/morphe-manager/commit/b8adadf782e49f55ffc1323dcc15dd6a461abd81))

## app [1.4.1](https://github.com/MorpheApp/morphe-manager/compare/v1.4.0...v1.4.1) (2026-02-01)


### Bug Fixes

* Use new Expert mode for users with old user data ([57e658c](https://github.com/MorpheApp/morphe-manager/commit/57e658c26e3c2f7822ac0d4a906a6c2fd4a35210))

## app [1.4.1-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.4.0...v1.4.1-dev.1) (2026-01-31)


### Bug Fixes

* Use new Expert mode for users with old user data ([57e658c](https://github.com/MorpheApp/morphe-manager/commit/57e658c26e3c2f7822ac0d4a906a6c2fd4a35210))

# app [1.4.0](https://github.com/MorpheApp/morphe-manager/compare/v1.3.2...v1.4.0) (2026-01-31)


### Bug Fixes

* 'GmsCore support' patch not excluded in root mode ([#134](https://github.com/MorpheApp/morphe-manager/issues/134)) ([a2eb5b0](https://github.com/MorpheApp/morphe-manager/commit/a2eb5b0106c53ddb352b90496d51805a4d70a6a9))
* Resolve libaapt.so patching errors ([#133](https://github.com/MorpheApp/morphe-manager/issues/133)) ([7a443e7](https://github.com/MorpheApp/morphe-manager/commit/7a443e7215eaa9a2ddb26670518694661f57551d))


### Features

* Add Expert mode ([#107](https://github.com/MorpheApp/morphe-manager/issues/107)) ([9273b41](https://github.com/MorpheApp/morphe-manager/commit/9273b415546c4561520ccb73b4cc48a73c449a4e))

# app [1.4.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.4.0-dev.2...v1.4.0-dev.3) (2026-01-30)


### Bug Fixes

* 'GmsCore support' patch not excluded in root mode ([#134](https://github.com/MorpheApp/morphe-manager/issues/134)) ([a2eb5b0](https://github.com/MorpheApp/morphe-manager/commit/a2eb5b0106c53ddb352b90496d51805a4d70a6a9))

# app [1.4.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.4.0-dev.1...v1.4.0-dev.2) (2026-01-30)


### Bug Fixes

* Resolve libaapt.so patching errors ([#133](https://github.com/MorpheApp/morphe-manager/issues/133)) ([7a443e7](https://github.com/MorpheApp/morphe-manager/commit/7a443e7215eaa9a2ddb26670518694661f57551d))

# app [1.4.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.3.2...v1.4.0-dev.1) (2026-01-27)


### Features

* Add Expert mode ([#107](https://github.com/MorpheApp/morphe-manager/issues/107)) ([9273b41](https://github.com/MorpheApp/morphe-manager/commit/9273b415546c4561520ccb73b4cc48a73c449a4e))

## app [1.3.2](https://github.com/MorpheApp/morphe-manager/compare/v1.3.1...v1.3.2) (2026-01-23)


### Bug Fixes

* Handle remounting of patched app after rebooting ([b28cc9e](https://github.com/MorpheApp/morphe-manager/commit/b28cc9e77cc06f564be4bb39b26663cc5ac4a7da))
* Reduce default patcher process memory to 500mb to solve patching errors for budget devices ([0a4cea3](https://github.com/MorpheApp/morphe-manager/commit/0a4cea36edb4904266d8e314cbdd5eb785606a28))
* Update root mounting script directory ([3c3b7a7](https://github.com/MorpheApp/morphe-manager/commit/3c3b7a7fc2bf3d4b77a0c86d70b4e137ab91d917))

## app [1.3.2-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.3.2-dev.2...v1.3.2-dev.3) (2026-01-23)


### Bug Fixes

* Update root mounting script directory ([3c3b7a7](https://github.com/MorpheApp/morphe-manager/commit/3c3b7a7fc2bf3d4b77a0c86d70b4e137ab91d917))

## app [1.3.2-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.3.2-dev.1...v1.3.2-dev.2) (2026-01-23)


### Bug Fixes

* Reduce default patcher process memory to 500mb to solve patching errors for budget devices ([0a4cea3](https://github.com/MorpheApp/morphe-manager/commit/0a4cea36edb4904266d8e314cbdd5eb785606a28))

## app [1.3.2-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.3.1...v1.3.2-dev.1) (2026-01-23)


### Bug Fixes

* Handle remounting of patched app after rebooting ([b28cc9e](https://github.com/MorpheApp/morphe-manager/commit/b28cc9e77cc06f564be4bb39b26663cc5ac4a7da))

## app [1.3.1](https://github.com/MorpheApp/morphe-manager/compare/v1.3.0...v1.3.1) (2026-01-22)


### Bug Fixes

* Handle multiple versionName entries in root mount script ([#118](https://github.com/MorpheApp/morphe-manager/issues/118)) ([4515e8b](https://github.com/MorpheApp/morphe-manager/commit/4515e8b2586f0667a682d1b4b2e6301c2811c2ce))

## app [1.3.1-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.3.0...v1.3.1-dev.1) (2026-01-21)


### Bug Fixes

* Handle multiple versionName entries in root mount script ([#118](https://github.com/MorpheApp/morphe-manager/issues/118)) ([4515e8b](https://github.com/MorpheApp/morphe-manager/commit/4515e8b2586f0667a682d1b4b2e6301c2811c2ce))

# app [1.3.0](https://github.com/MorpheApp/morphe-manager/compare/v1.2.1...v1.3.0) (2026-01-15)


### Bug Fixes

* Set initial page to Advanced tab ([a94e971](https://github.com/MorpheApp/morphe-manager/commit/a94e971464b4aa055dacf41f86ff4e2fb33d746b))


### Features

* Add additional app icons ([#95](https://github.com/MorpheApp/morphe-manager/issues/95)) ([1e3c058](https://github.com/MorpheApp/morphe-manager/commit/1e3c0581431af08c9de6075855c5554c6b716649))
* Refactor to tab settings ([#101](https://github.com/MorpheApp/morphe-manager/issues/101)) ([d76ee03](https://github.com/MorpheApp/morphe-manager/commit/d76ee03fcdb3beccf95ebc91f257ca6feb2a162c))

# app [1.3.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.3.0-dev.2...v1.3.0-dev.3) (2026-01-15)


### Bug Fixes

* Set initial page to Advanced tab ([a94e971](https://github.com/MorpheApp/morphe-manager/commit/a94e971464b4aa055dacf41f86ff4e2fb33d746b))

# app [1.3.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.3.0-dev.1...v1.3.0-dev.2) (2026-01-12)


### Features

* Refactor to tab settings ([#101](https://github.com/MorpheApp/morphe-manager/issues/101)) ([d76ee03](https://github.com/MorpheApp/morphe-manager/commit/d76ee03fcdb3beccf95ebc91f257ca6feb2a162c))

# app [1.3.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.2.1...v1.3.0-dev.1) (2026-01-12)


### Features

* Add additional app icons ([#95](https://github.com/MorpheApp/morphe-manager/issues/95)) ([1e3c058](https://github.com/MorpheApp/morphe-manager/commit/1e3c0581431af08c9de6075855c5554c6b716649))

## app [1.2.1](https://github.com/MorpheApp/morphe-manager/compare/v1.2.0...v1.2.1) (2026-01-11)


### Bug Fixes

* Do not use patcher process for armv7 devices ([9bc999c](https://github.com/MorpheApp/morphe-manager/commit/9bc999c45b82bfc3debd8c260bfa8a73a5476632))

## app [1.2.1-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.2.0...v1.2.1-dev.1) (2026-01-10)


### Bug Fixes

* Do not use patcher process for armv7 devices ([9bc999c](https://github.com/MorpheApp/morphe-manager/commit/9bc999c45b82bfc3debd8c260bfa8a73a5476632))

# app [1.2.0](https://github.com/MorpheApp/morphe-manager/compare/v1.1.1...v1.2.0) (2026-01-10)


### Bug Fixes

* Allow disabling built-in bundle ([#87](https://github.com/MorpheApp/morphe-manager/issues/87)) ([8673d14](https://github.com/MorpheApp/morphe-manager/commit/8673d14081d770f9cc53ccc0dc2d93da7903f581))
* Change to time based version code to resolve pre-release Manager unable to update to latest stable release ([97ec26e](https://github.com/MorpheApp/morphe-manager/commit/97ec26e3b11e0133873b5a8cae3dcb4a0a45c239))
* Completely isolate patch options in Morphe and Expert modes ([#90](https://github.com/MorpheApp/morphe-manager/issues/90)) ([c96fcd9](https://github.com/MorpheApp/morphe-manager/commit/c96fcd9c1c24cbe2afc835b41737c351fb226f58))
* Some apk files are not selectable in expert mode ([aa8c3ce](https://github.com/MorpheApp/morphe-manager/commit/aa8c3cea4a9e331d2dae09c65e3c1cd76bc2c618))


### Features

* Add language picker ([#93](https://github.com/MorpheApp/morphe-manager/issues/93)) ([dc2058a](https://github.com/MorpheApp/morphe-manager/commit/dc2058aad729689814e54302d14a55ae7f849754))

# app [1.2.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.2.0-dev.1...v1.2.0-dev.2) (2026-01-10)


### Bug Fixes

* Completely isolate patch options in Morphe and Expert modes ([#90](https://github.com/MorpheApp/morphe-manager/issues/90)) ([c96fcd9](https://github.com/MorpheApp/morphe-manager/commit/c96fcd9c1c24cbe2afc835b41737c351fb226f58))

# app [1.2.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.1.2-dev.2...v1.2.0-dev.1) (2026-01-10)


### Bug Fixes

* Allow disabling built-in bundle ([#87](https://github.com/MorpheApp/morphe-manager/issues/87)) ([8673d14](https://github.com/MorpheApp/morphe-manager/commit/8673d14081d770f9cc53ccc0dc2d93da7903f581))


### Features

* Add language picker ([#93](https://github.com/MorpheApp/morphe-manager/issues/93)) ([dc2058a](https://github.com/MorpheApp/morphe-manager/commit/dc2058aad729689814e54302d14a55ae7f849754))

## app [1.1.2-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.1.2-dev.1...v1.1.2-dev.2) (2026-01-07)


### Bug Fixes

* Change to time based version code to resolve pre-release Manager unable to update to latest stable release ([97ec26e](https://github.com/MorpheApp/morphe-manager/commit/97ec26e3b11e0133873b5a8cae3dcb4a0a45c239))

## app [1.1.2-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.1.1...v1.1.2-dev.1) (2026-01-07)


### Bug Fixes

* Some apk files are not selectable in expert mode ([aa8c3ce](https://github.com/MorpheApp/morphe-manager/commit/aa8c3cea4a9e331d2dae09c65e3c1cd76bc2c618))

## app [1.1.1](https://github.com/MorpheApp/morphe-manager/compare/v1.1.0...v1.1.1) (2026-01-05)


### Bug Fixes

* Fix crash on Android 10 when selecting APK in Expert mode ([#64](https://github.com/MorpheApp/morphe-manager/issues/64)) ([#64](https://github.com/MorpheApp/morphe-manager/issues/64)) ([faa5290](https://github.com/MorpheApp/morphe-manager/commit/faa5290abe3409f7208eeac46d0c3dbc5deb6d9a))
* Import keystore using default password ([1c0c1f6](https://github.com/MorpheApp/morphe-manager/commit/1c0c1f6799762946574d3eb54b0ea433145fe469))

## app [1.1.1-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.1.1-dev.1...v1.1.1-dev.2) (2026-01-04)


### Bug Fixes

* Fix crash on Android 10 when selecting APK in Expert mode ([#64](https://github.com/MorpheApp/morphe-manager/issues/64)) ([#64](https://github.com/MorpheApp/morphe-manager/issues/64)) ([faa5290](https://github.com/MorpheApp/morphe-manager/commit/faa5290abe3409f7208eeac46d0c3dbc5deb6d9a))

## app [1.1.1-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.1.0...v1.1.1-dev.1) (2026-01-04)


### Bug Fixes

* Import keystore using default password ([1c0c1f6](https://github.com/MorpheApp/morphe-manager/commit/1c0c1f6799762946574d3eb54b0ea433145fe469))

# app [1.1.0](https://github.com/MorpheApp/morphe-manager/compare/v1.0.0...v1.1.0) (2026-01-04)


### Bug Fixes

* Always use a vertical button layout ([e6f69c8](https://github.com/MorpheApp/morphe-manager/commit/e6f69c82bf0dcc6a832166523e15401886c26e1c))
* Change process runtime memory limit ([e17ac20](https://github.com/MorpheApp/morphe-manager/commit/e17ac200ac5fe0407be7f78b1d64a144402438cf))


### Features

* Add localization to patch options ([#48](https://github.com/MorpheApp/morphe-manager/issues/48)) ([0e7a203](https://github.com/MorpheApp/morphe-manager/commit/0e7a203819e629f231293896396d5154585dc402))
* Change home screen pre-release setting to include Manager updates ([#53](https://github.com/MorpheApp/morphe-manager/issues/53)) ([f2397da](https://github.com/MorpheApp/morphe-manager/commit/f2397da0eb6c12dc8c00d50b58aa7f46e648e191))

# app [1.1.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.1.0-dev.1...v1.1.0-dev.2) (2026-01-04)


### Bug Fixes

* Change process runtime memory limit ([e17ac20](https://github.com/MorpheApp/morphe-manager/commit/e17ac200ac5fe0407be7f78b1d64a144402438cf))


### Features

* Change home screen pre-release setting to include Manager updates ([#53](https://github.com/MorpheApp/morphe-manager/issues/53)) ([f2397da](https://github.com/MorpheApp/morphe-manager/commit/f2397da0eb6c12dc8c00d50b58aa7f46e648e191))

# app [1.1.0-dev.1](https://github.com/MorpheApp/morphe-manager/compare/v1.0.0...v1.1.0-dev.1) (2026-01-03)


### Bug Fixes

* Always use a vertical button layout ([e6f69c8](https://github.com/MorpheApp/morphe-manager/commit/e6f69c82bf0dcc6a832166523e15401886c26e1c))


### Features

* Add localization to patch options ([#48](https://github.com/MorpheApp/morphe-manager/issues/48)) ([0e7a203](https://github.com/MorpheApp/morphe-manager/commit/0e7a203819e629f231293896396d5154585dc402))

# app 1.0.0 (2026-01-01)


### Bug Fixes

* After changing pre-release, show snackbar and block starting patching until bundles are updated ([1fe7aa1](https://github.com/MorpheApp/morphe-manager/commit/1fe7aa1e69513e11b544d835e8239d5088f2f0ec))
* Allow all files as .mpp files are not available in file picker ([80ad1ec](https://github.com/MorpheApp/morphe-manager/commit/80ad1ec22b4ad42879267eb3f18d0e0caaffe248))
* Change first greeting message shown to be more traditional and easier to understand what to do ([c8f797f](https://github.com/MorpheApp/morphe-manager/commit/c8f797fcb0c1e4d400f5dbf8de5b46721a6d05de))
* Correct rounding errors of progress value ([eb31b73](https://github.com/MorpheApp/morphe-manager/commit/eb31b73cb4e94f2754883656cb8642e913a741cc))
* Do not show empty space above the About section ([e2eaf7d](https://github.com/MorpheApp/morphe-manager/commit/e2eaf7d8dd4e5f4bc354bde82848ed183b880322))
* Do not show patches update snackbar unless user is manually refreshing ([8d35d28](https://github.com/MorpheApp/morphe-manager/commit/8d35d28d695ffb7a5d9145a5d3d4d1d9ea39efbe))
* Fix `isBundleUpdating` state after merge upstream changes ([7598e6d](https://github.com/MorpheApp/morphe-manager/commit/7598e6d49a570b7bfcfaecedf771ac79c3163a85))
* Fix `lateinit property eventHandler has not been initialized` ([#25](https://github.com/MorpheApp/morphe-manager/issues/25)) ([d074a14](https://github.com/MorpheApp/morphe-manager/commit/d074a14e8aa22dcaa3e4b5408f2c7623c33e770b))
* Fix apk picker ([ae813d6](https://github.com/MorpheApp/morphe-manager/commit/ae813d6861879e0f4ef286360f095658394e94b8))
* Fix app console warning of provider not found ([d2c76b7](https://github.com/MorpheApp/morphe-manager/commit/d2c76b7b51137fd88064e57f39e0abef948f109f))
* Fix build ([fe5250b](https://github.com/MorpheApp/morphe-manager/commit/fe5250b75f6b93a89cecb10f8f95d5bc6092ab57))
* Fix the dack theme color picker ([24d4c5f](https://github.com/MorpheApp/morphe-manager/commit/24d4c5f0bb7f2b5f0911578eb86e0c898c4eecff))
* Hide update on metered connection from advanced settings menu ([91ae275](https://github.com/MorpheApp/morphe-manager/commit/91ae2756b5f772c158156d0a22478eb43e34cfed))
* Increase installer timeout ([934305b](https://github.com/MorpheApp/morphe-manager/commit/934305bd6bfbda17d225e6e314e0ee4cd17147cc))
* Increase installer timeout wait time ([648f22b](https://github.com/MorpheApp/morphe-manager/commit/648f22b808cf359d276ccae5bc7b4c639d3e0db6))
* Remove blinking when opening dialog, use gradient instead of blur (works on A12+ but not well) ([b851913](https://github.com/MorpheApp/morphe-manager/commit/b85191354175f0690800854993a97f612696860f))
* Resolve bundle fetching from upstream merge ([#11](https://github.com/MorpheApp/morphe-manager/issues/11)) ([d9166b8](https://github.com/MorpheApp/morphe-manager/commit/d9166b80f8838d026067eed0b2ea16aa4d5eb347))
* Restore only delete button ([bf7c045](https://github.com/MorpheApp/morphe-manager/commit/bf7c045758f7c40d19b9e87680cbe13aa87035ee))
* Show "Patches are loading" on fresh install ([170d17f](https://github.com/MorpheApp/morphe-manager/commit/170d17f91b0cbf35c14fcfd388376083fc60eecb))
* Show "Patches are loading" toast if bundles are downloading ([3d59980](https://github.com/MorpheApp/morphe-manager/commit/3d599803c452f5a6e34b7bf0d9dd363374a0d812))
* Show patches update UI when using advanced mode ([309f7db](https://github.com/MorpheApp/morphe-manager/commit/309f7db68218956c3e9ac5f934466fd783931208))
* Skip if patch doesn't exist in this bundle ([6482987](https://github.com/MorpheApp/morphe-manager/commit/6482987fe6b578ef4bef8f73071194a3507afee0))
* Update changelog after bundle update ([ed9c173](https://github.com/MorpheApp/morphe-manager/commit/ed9c1730305161693bab124af9ea41e791ed1332))
* Use Morphe patches API ([#12](https://github.com/MorpheApp/morphe-manager/issues/12)) ([01cdffc](https://github.com/MorpheApp/morphe-manager/commit/01cdffcf6b885780d9116dd2dbefa526c64053dd))
* Use the appropriate string ([7661989](https://github.com/MorpheApp/morphe-manager/commit/7661989b0fd0b9096c1330dfbed45afb78b3901d))


### Features

* Add a delay at 100% before showing success screen ([abb5ab5](https://github.com/MorpheApp/morphe-manager/commit/abb5ab523e30779d0c4b0ae25b977bd719d33962))
* Add adaptive landscape mode ([#8](https://github.com/MorpheApp/morphe-manager/issues/8)) ([3bbc62e](https://github.com/MorpheApp/morphe-manager/commit/3bbc62ebb5e4c264fa2ea4864f4b0a25fd52b50f))
* Add haptic feedback to About setting item ([d3f8e33](https://github.com/MorpheApp/morphe-manager/commit/d3f8e3341e8d74a4be184b476b198fd1cc7f0728))
* Add in-app patches options ([#27](https://github.com/MorpheApp/morphe-manager/issues/27)) ([2ce57a7](https://github.com/MorpheApp/morphe-manager/commit/2ce57a7e772608a7faa406ea6c45061a7c7566ca))
* Add link to the Crowdin ([b0b94cb](https://github.com/MorpheApp/morphe-manager/commit/b0b94cb23e62deda3bfc6bfcd316cc191e839108))
* Add more haptic feedback ([967f9ca](https://github.com/MorpheApp/morphe-manager/commit/967f9ca7ef8bbd2471a0ee4633f3d96f52146afd))
* Adjust layout of patches list and change log in modal patches bundle ([#13](https://github.com/MorpheApp/morphe-manager/issues/13)) ([0dcf5b9](https://github.com/MorpheApp/morphe-manager/commit/0dcf5b9a1f6b354cb6f1921259d1540468db2800))
* Change buttons priority ([85fdd86](https://github.com/MorpheApp/morphe-manager/commit/85fdd8680d2fe0a85dfc01ed5b7d123055614325))
* Change Particles background to Space ([9575446](https://github.com/MorpheApp/morphe-manager/commit/95754462b8b0086752ff1858a3fe254c53b4a185))
* Custom Morphe home screen ([515d08c](https://github.com/MorpheApp/morphe-manager/commit/515d08ce741752d06cbabb7be57bac9fe692d8a6))
* Morphe homepage root installation ([#10](https://github.com/MorpheApp/morphe-manager/issues/10)) ([8ed769f](https://github.com/MorpheApp/morphe-manager/commit/8ed769fe1a86a7a15fa4c46ccebdbf1c59e90786))
* Refactor color row elements ([86b11b6](https://github.com/MorpheApp/morphe-manager/commit/86b11b66d234113a22b94e128d25acc7e699410e))
* UI & UX Improvements ([#17](https://github.com/MorpheApp/morphe-manager/issues/17)) ([9e72b08](https://github.com/MorpheApp/morphe-manager/commit/9e72b0853d1a2fadd92fca7239668d1b33e904a6))
* Use fullscreen dialog for manager update ([91bb4d1](https://github.com/MorpheApp/morphe-manager/commit/91bb4d18e7338a0f313d397eaaa05eafa7df298f))

# app [1.0.0-dev.7](https://github.com/MorpheApp/morphe-manager/compare/v1.0.0-dev.6...v1.0.0-dev.7) (2026-01-01)


### Bug Fixes

* Increase installer timeout wait time ([648f22b](https://github.com/MorpheApp/morphe-manager/commit/648f22b808cf359d276ccae5bc7b4c639d3e0db6))

# app [1.0.0-dev.6](https://github.com/MorpheApp/morphe-manager/compare/v1.0.0-dev.5...v1.0.0-dev.6) (2026-01-01)


### Bug Fixes

* Increase installer timeout ([934305b](https://github.com/MorpheApp/morphe-manager/commit/934305bd6bfbda17d225e6e314e0ee4cd17147cc))


### Features

* Refactor color row elements ([86b11b6](https://github.com/MorpheApp/morphe-manager/commit/86b11b66d234113a22b94e128d25acc7e699410e))

# app [1.0.0-dev.5](https://github.com/MorpheApp/morphe-manager/compare/v1.0.0-dev.4...v1.0.0-dev.5) (2025-12-30)


### Bug Fixes

* Fix `isBundleUpdating` state after merge upstream changes ([7598e6d](https://github.com/MorpheApp/morphe-manager/commit/7598e6d49a570b7bfcfaecedf771ac79c3163a85))
* Fix apk picker ([ae813d6](https://github.com/MorpheApp/morphe-manager/commit/ae813d6861879e0f4ef286360f095658394e94b8))
* Restore only delete button ([bf7c045](https://github.com/MorpheApp/morphe-manager/commit/bf7c045758f7c40d19b9e87680cbe13aa87035ee))
* Skip if patch doesn't exist in this bundle ([6482987](https://github.com/MorpheApp/morphe-manager/commit/6482987fe6b578ef4bef8f73071194a3507afee0))


### Features

* Add in-app patches options ([#27](https://github.com/MorpheApp/morphe-manager/issues/27)) ([2ce57a7](https://github.com/MorpheApp/morphe-manager/commit/2ce57a7e772608a7faa406ea6c45061a7c7566ca))

# app [1.0.0-dev.4](https://github.com/MorpheApp/morphe-manager/compare/v1.0.0-dev.3...v1.0.0-dev.4) (2025-12-25)


### Bug Fixes

* Fix `lateinit property eventHandler has not been initialized` ([#25](https://github.com/MorpheApp/morphe-manager/issues/25)) ([d074a14](https://github.com/MorpheApp/morphe-manager/commit/d074a14e8aa22dcaa3e4b5408f2c7623c33e770b))

# app [1.0.0-dev.3](https://github.com/MorpheApp/morphe-manager/compare/v1.0.0-dev.2...v1.0.0-dev.3) (2025-12-24)


### Bug Fixes

* Allow all files as .mpp files are not available in file picker ([80ad1ec](https://github.com/MorpheApp/morphe-manager/commit/80ad1ec22b4ad42879267eb3f18d0e0caaffe248))
* Remove blinking when opening dialog, use gradient instead of blur (works on A12+ but not well) ([b851913](https://github.com/MorpheApp/morphe-manager/commit/b85191354175f0690800854993a97f612696860f))
* Show "Patches are loading" on fresh install ([170d17f](https://github.com/MorpheApp/morphe-manager/commit/170d17f91b0cbf35c14fcfd388376083fc60eecb))
* Update changelog after bundle update ([ed9c173](https://github.com/MorpheApp/morphe-manager/commit/ed9c1730305161693bab124af9ea41e791ed1332))
* Use the appropriate string ([7661989](https://github.com/MorpheApp/morphe-manager/commit/7661989b0fd0b9096c1330dfbed45afb78b3901d))


### Features

* Add haptic feedback to About setting item ([d3f8e33](https://github.com/MorpheApp/morphe-manager/commit/d3f8e3341e8d74a4be184b476b198fd1cc7f0728))
* Add link to the Crowdin ([b0b94cb](https://github.com/MorpheApp/morphe-manager/commit/b0b94cb23e62deda3bfc6bfcd316cc191e839108))
* Add more haptic feedback ([967f9ca](https://github.com/MorpheApp/morphe-manager/commit/967f9ca7ef8bbd2471a0ee4633f3d96f52146afd))
* Change buttons priority ([85fdd86](https://github.com/MorpheApp/morphe-manager/commit/85fdd8680d2fe0a85dfc01ed5b7d123055614325))
* Change Particles background to Space ([9575446](https://github.com/MorpheApp/morphe-manager/commit/95754462b8b0086752ff1858a3fe254c53b4a185))
* UI & UX Improvements ([#17](https://github.com/MorpheApp/morphe-manager/issues/17)) ([9e72b08](https://github.com/MorpheApp/morphe-manager/commit/9e72b0853d1a2fadd92fca7239668d1b33e904a6))
* Use fullscreen dialog for manager update ([91bb4d1](https://github.com/MorpheApp/morphe-manager/commit/91bb4d18e7338a0f313d397eaaa05eafa7df298f))

# app [1.0.0-dev.2](https://github.com/MorpheApp/morphe-manager/compare/v1.0.0-dev.1...v1.0.0-dev.2) (2025-12-15)


### Bug Fixes

* After changing pre-release, show snackbar and block starting patching until bundles are updated ([1fe7aa1](https://github.com/MorpheApp/morphe-manager/commit/1fe7aa1e69513e11b544d835e8239d5088f2f0ec))
* Change first greeting message shown to be more traditional and easier to understand what to do ([c8f797f](https://github.com/MorpheApp/morphe-manager/commit/c8f797fcb0c1e4d400f5dbf8de5b46721a6d05de))
* Correct rounding errors of progress value ([eb31b73](https://github.com/MorpheApp/morphe-manager/commit/eb31b73cb4e94f2754883656cb8642e913a741cc))
* Do not show patches update snackbar unless user is manually refreshing ([8d35d28](https://github.com/MorpheApp/morphe-manager/commit/8d35d28d695ffb7a5d9145a5d3d4d1d9ea39efbe))
* Fix app console warning of provider not found ([d2c76b7](https://github.com/MorpheApp/morphe-manager/commit/d2c76b7b51137fd88064e57f39e0abef948f109f))
* Hide update on metered connection from advanced settings menu ([91ae275](https://github.com/MorpheApp/morphe-manager/commit/91ae2756b5f772c158156d0a22478eb43e34cfed))
* Show patches update UI when using advanced mode ([309f7db](https://github.com/MorpheApp/morphe-manager/commit/309f7db68218956c3e9ac5f934466fd783931208))
* Use Morphe patches API ([#12](https://github.com/MorpheApp/morphe-manager/issues/12)) ([01cdffc](https://github.com/MorpheApp/morphe-manager/commit/01cdffcf6b885780d9116dd2dbefa526c64053dd))


### Features

* Add a delay at 100% before showing success screen ([abb5ab5](https://github.com/MorpheApp/morphe-manager/commit/abb5ab523e30779d0c4b0ae25b977bd719d33962))
* Adjust layout of patches list and change log in modal patches bundle ([#13](https://github.com/MorpheApp/morphe-manager/issues/13)) ([0dcf5b9](https://github.com/MorpheApp/morphe-manager/commit/0dcf5b9a1f6b354cb6f1921259d1540468db2800))
* Morphe homepage root installation ([#10](https://github.com/MorpheApp/morphe-manager/issues/10)) ([8ed769f](https://github.com/MorpheApp/morphe-manager/commit/8ed769fe1a86a7a15fa4c46ccebdbf1c59e90786))

# app 1.0.0-dev.1 (2025-12-12)


### Bug Fixes

* Do not show empty space above the About section ([e2eaf7d](https://github.com/MorpheApp/morphe-manager/commit/e2eaf7d8dd4e5f4bc354bde82848ed183b880322))
* Fix build ([fe5250b](https://github.com/MorpheApp/morphe-manager/commit/fe5250b75f6b93a89cecb10f8f95d5bc6092ab57))
* Fix the dack theme color picker ([24d4c5f](https://github.com/MorpheApp/morphe-manager/commit/24d4c5f0bb7f2b5f0911578eb86e0c898c4eecff))
* Resolve bundle fetching from upstream merge ([#11](https://github.com/MorpheApp/morphe-manager/issues/11)) ([d9166b8](https://github.com/MorpheApp/morphe-manager/commit/d9166b80f8838d026067eed0b2ea16aa4d5eb347))
* Show "Patches are loading" toast if bundles are downloading ([3d59980](https://github.com/MorpheApp/morphe-manager/commit/3d599803c452f5a6e34b7bf0d9dd363374a0d812))


### Features

* Add adaptive landscape mode ([#8](https://github.com/MorpheApp/morphe-manager/issues/8)) ([3bbc62e](https://github.com/MorpheApp/morphe-manager/commit/3bbc62ebb5e4c264fa2ea4864f4b0a25fd52b50f))
* Custom Morphe home screen ([515d08c](https://github.com/MorpheApp/morphe-manager/commit/515d08ce741752d06cbabb7be57bac9fe692d8a6))

# app 1.0.0-dev.1 (2025-12-11)


### Bug Fixes

* Do not show empty space above the About section ([e2eaf7d](https://github.com/MorpheApp/morphe-manager/commit/e2eaf7d8dd4e5f4bc354bde82848ed183b880322))
* Fix the dack theme color picker ([24d4c5f](https://github.com/MorpheApp/morphe-manager/commit/24d4c5f0bb7f2b5f0911578eb86e0c898c4eecff))


### Features

* Custom Morphe home screen ([515d08c](https://github.com/MorpheApp/morphe-manager/commit/515d08ce741752d06cbabb7be57bac9fe692d8a6))
