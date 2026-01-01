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
