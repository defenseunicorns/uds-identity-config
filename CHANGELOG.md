# Changelog

## [0.4.3](https://github.com/defenseunicorns/uds-identity-config/compare/v0.4.2...v0.4.3) (2024-05-09)


### Features

* google saml sso ([#79](https://github.com/defenseunicorns/uds-identity-config/issues/79)) ([be8024d](https://github.com/defenseunicorns/uds-identity-config/commit/be8024d2c390e26fe4305b9cb5b9d44a7e3bb7ca))


### Bug Fixes

* **deps:** update all dependencies to v24.0.4 ([#78](https://github.com/defenseunicorns/uds-identity-config/issues/78)) ([575b77e](https://github.com/defenseunicorns/uds-identity-config/commit/575b77e92b74d674e41f7e9333312fe65fa24b26))
* plugin unit test cleanup and jacoco reporting ([#62](https://github.com/defenseunicorns/uds-identity-config/issues/62)) ([c6c3e57](https://github.com/defenseunicorns/uds-identity-config/commit/c6c3e570c6125e328020858ee8db7a760c05b867))


### Miscellaneous

* add additional saml scopes and mappers for gitlab ([#77](https://github.com/defenseunicorns/uds-identity-config/issues/77)) ([6dcc557](https://github.com/defenseunicorns/uds-identity-config/commit/6dcc557c12c9c40a903527689082fd14babe8392))
* **deps:** update actions/checkout action to v4.1.5 ([#80](https://github.com/defenseunicorns/uds-identity-config/issues/80)) ([b37630d](https://github.com/defenseunicorns/uds-identity-config/commit/b37630df6863398efcf043aebbcdc44306aeabf3))
* **deps:** update all dependencies ([#72](https://github.com/defenseunicorns/uds-identity-config/issues/72)) ([6b31373](https://github.com/defenseunicorns/uds-identity-config/commit/6b313730568d6b7d76f37211d555c68d13dee6ec))
* **deps:** update defenseunicorns/uds-common action to v0.4.1 ([#71](https://github.com/defenseunicorns/uds-identity-config/issues/71)) ([0a16a39](https://github.com/defenseunicorns/uds-identity-config/commit/0a16a39da4a4da77730472651de75338b7aaedfa))
* **deps:** update defenseunicorns/uds-common action to v0.4.2 ([#74](https://github.com/defenseunicorns/uds-identity-config/issues/74)) ([ee7bf69](https://github.com/defenseunicorns/uds-identity-config/commit/ee7bf6998ffd67fb19f49e728d1687febee40e52))
* **deps:** update dependency defenseunicorns/uds-core to v0.21.0 ([#11](https://github.com/defenseunicorns/uds-identity-config/issues/11)) ([c921359](https://github.com/defenseunicorns/uds-identity-config/commit/c9213590bb543ec78bbe91cb07af1a088f8291dc))
* **deps:** update dependency defenseunicorns/uds-core to v0.21.1 ([#75](https://github.com/defenseunicorns/uds-identity-config/issues/75)) ([d99f4ff](https://github.com/defenseunicorns/uds-identity-config/commit/d99f4ff052b75c2a0d2c40a78104757ed55f9aab))

## [0.4.2](https://github.com/defenseunicorns/uds-identity-config/compare/v0.4.1...v0.4.2) (2024-04-30)


### Bug Fixes

* custom attribute plugin ([#65](https://github.com/defenseunicorns/uds-identity-config/issues/65)) ([9c6c974](https://github.com/defenseunicorns/uds-identity-config/commit/9c6c9743ed8d4a97dac311b5f09e935d40d6af77))


### Miscellaneous

* **deps:** update actions/checkout action to v4.1.3 ([#25](https://github.com/defenseunicorns/uds-identity-config/issues/25)) ([e26f541](https://github.com/defenseunicorns/uds-identity-config/commit/e26f5413c2ffd749a17e612299722b6a6fa64b8c))
* **deps:** update actions/checkout action to v4.1.4 ([#66](https://github.com/defenseunicorns/uds-identity-config/issues/66)) ([267641d](https://github.com/defenseunicorns/uds-identity-config/commit/267641d3acf28ec9a4958a1479d0c82bda623fdd))
* **deps:** update dependency @commitlint/cli to v19.3.0 ([#64](https://github.com/defenseunicorns/uds-identity-config/issues/64)) ([66ae563](https://github.com/defenseunicorns/uds-identity-config/commit/66ae5635c099c50cf9b6da91f31f9e46311e8acb))
* **deps:** update docker image ghcr.io/defenseunicorns/packages/init to v0.33.1 ([#67](https://github.com/defenseunicorns/uds-identity-config/issues/67)) ([1e7f108](https://github.com/defenseunicorns/uds-identity-config/commit/1e7f10852e9c5cede497ad66ee1a29d4c0f1330d))
* renovate fixes ([#47](https://github.com/defenseunicorns/uds-identity-config/issues/47)) ([b6ab9e0](https://github.com/defenseunicorns/uds-identity-config/commit/b6ab9e05b7629ab525c091bd482179b57ef25460))

## [0.4.1](https://github.com/defenseunicorns/uds-identity-config/compare/v0.4.0...v0.4.1) (2024-04-19)


### Features

* added mappers to realm ([53f2660](https://github.com/defenseunicorns/uds-identity-config/commit/53f2660bc2a9a5b920dbe0e20cd90bf9ed07eb1d))
* optional idp configuration ([#58](https://github.com/defenseunicorns/uds-identity-config/issues/58)) ([70324df](https://github.com/defenseunicorns/uds-identity-config/commit/70324df2f26c456669c30da56b8b65c02da22a02))


### Miscellaneous

* update to integration test against main uds-core ([#59](https://github.com/defenseunicorns/uds-identity-config/issues/59)) ([9244b6b](https://github.com/defenseunicorns/uds-identity-config/commit/9244b6bffcf9856f445cdeccab30f1ac4d7151b9))

## [0.4.0](https://github.com/defenseunicorns/uds-identity-config/compare/v0.3.6...v0.4.0) (2024-04-16)


### ⚠ BREAKING CHANGES

* depend on keycloak 24

### Features

* cypress integration tests ([#42](https://github.com/defenseunicorns/uds-identity-config/issues/42)) ([12ea0cc](https://github.com/defenseunicorns/uds-identity-config/commit/12ea0ccb0e410f6cc35a2b158bf01dd60f39fa96))
* depend on keycloak 24 ([ed91427](https://github.com/defenseunicorns/uds-identity-config/commit/ed914270395bdce2cd0320a9abacb0501d871309))
* plugin unit tests ([#33](https://github.com/defenseunicorns/uds-identity-config/issues/33)) ([47651d9](https://github.com/defenseunicorns/uds-identity-config/commit/47651d9192cfbaff88a024a6598f1e1bcc989fcd))
* zarf file ([#39](https://github.com/defenseunicorns/uds-identity-config/issues/39)) ([3cc2ee7](https://github.com/defenseunicorns/uds-identity-config/commit/3cc2ee7c065c4acbd6447d0b5ea506300e85fbf3))


### Bug Fixes

* create default openid client scope ([#43](https://github.com/defenseunicorns/uds-identity-config/issues/43)) ([c604d15](https://github.com/defenseunicorns/uds-identity-config/commit/c604d156abb2ddb24bca40747607cb65c3bf5d6e))


### Miscellaneous

* please release config ([#54](https://github.com/defenseunicorns/uds-identity-config/issues/54)) ([b0cab96](https://github.com/defenseunicorns/uds-identity-config/commit/b0cab962a77de56eab6593c21fa526d0248ae63c))
* update codeowners ([#46](https://github.com/defenseunicorns/uds-identity-config/issues/46)) ([bade26d](https://github.com/defenseunicorns/uds-identity-config/commit/bade26dab6f626e10ff6f994c64bab8344ccc534))

## [0.3.6](https://github.com/defenseunicorns/uds-identity-config/compare/v0.3.5...v0.3.6) (2024-03-18)


### Bug Fixes

* register form id robot check ([#34](https://github.com/defenseunicorns/uds-identity-config/issues/34)) ([0007ec1](https://github.com/defenseunicorns/uds-identity-config/commit/0007ec15fb177f99703e5d728bdcd2eb37c0fc5d))

## [0.3.5](https://github.com/defenseunicorns/uds-identity-config/compare/v0.3.4...v0.3.5) (2024-03-14)


### Bug Fixes

* fail if ocsp response fails, get ocsp from cert ([#31](https://github.com/defenseunicorns/uds-identity-config/issues/31)) ([90bc2b1](https://github.com/defenseunicorns/uds-identity-config/commit/90bc2b1e5ed998d71de01be1018b7cade8fcb0d1))


### Miscellaneous

* cleanup misc baby-yoda references ([#28](https://github.com/defenseunicorns/uds-identity-config/issues/28)) ([898505b](https://github.com/defenseunicorns/uds-identity-config/commit/898505b76a80734488edf09a3769b1ac42470072))
* document processes ([#26](https://github.com/defenseunicorns/uds-identity-config/issues/26)) ([33b848e](https://github.com/defenseunicorns/uds-identity-config/commit/33b848e135ff30b27c484abc8462377dcd795b1f))

## [0.3.4](https://github.com/defenseunicorns/uds-identity-config/compare/v0.3.3...v0.3.4) (2024-03-04)


### Bug Fixes

* set -e on sync ([#22](https://github.com/defenseunicorns/uds-identity-config/issues/22)) ([38f3526](https://github.com/defenseunicorns/uds-identity-config/commit/38f352687b31dc6ab76bb32fcc562da8f9cce11f))

## [0.3.3](https://github.com/defenseunicorns/uds-identity-config/compare/v0.3.2...v0.3.3) (2024-03-04)


### Bug Fixes

* account header username ([#19](https://github.com/defenseunicorns/uds-identity-config/issues/19)) ([a05fe97](https://github.com/defenseunicorns/uds-identity-config/commit/a05fe974d7ff8e68dc945f3885e236560de45f85))
* update link ([#20](https://github.com/defenseunicorns/uds-identity-config/issues/20)) ([24f5a01](https://github.com/defenseunicorns/uds-identity-config/commit/24f5a01fa509a522986c69a167c4387c8a2a350c))


### Miscellaneous

* **deps:** update defenseunicorns/uds-common digest to fc12e3a ([#17](https://github.com/defenseunicorns/uds-identity-config/issues/17)) ([ceb5cd1](https://github.com/defenseunicorns/uds-identity-config/commit/ceb5cd1ebe9eb5b01cd9f85321c1f393912245e9))

## [0.3.2](https://github.com/defenseunicorns/uds-identity-config/compare/v0.3.1...v0.3.2) (2024-02-29)


### Bug Fixes

* add default ocsp for CACs ([#15](https://github.com/defenseunicorns/uds-identity-config/issues/15)) ([a9bd62a](https://github.com/defenseunicorns/uds-identity-config/commit/a9bd62a72ed45a62f5ded4a88df80eafb20195dd))

## [0.3.1](https://github.com/defenseunicorns/uds-identity-config/compare/v0.3.0...v0.3.1) (2024-02-29)


### Miscellaneous

* **deps:** update gha-deps ([#12](https://github.com/defenseunicorns/uds-identity-config/issues/12)) ([ba59e30](https://github.com/defenseunicorns/uds-identity-config/commit/ba59e30693aaeb7ea2d053a6c8f01d2e5af92bd5))

## [0.3.0](https://github.com/defenseunicorns/uds-identity-config/compare/v0.2.1...v0.3.0) (2024-02-28)


### Features

* add truststore to config image ([#5](https://github.com/defenseunicorns/uds-identity-config/issues/5)) ([0f94c4e](https://github.com/defenseunicorns/uds-identity-config/commit/0f94c4e084fb9c45da4910d9c6669a6cb2779991))
* generalize plugin & truststore customization ([#7](https://github.com/defenseunicorns/uds-identity-config/issues/7)) ([5993ee2](https://github.com/defenseunicorns/uds-identity-config/commit/5993ee25cc584aa15aed0d8206418cab94ab4928))

## [0.2.1](https://github.com/defenseunicorns/uds-identity-config/compare/v0.2.0...v0.2.1) (2024-02-22)


### Miscellaneous

* add buildx action ([1e7146e](https://github.com/defenseunicorns/uds-identity-config/commit/1e7146e51e5c028ce260c3a973f51f98a12c1a9b))

## [0.2.0](https://github.com/defenseunicorns/uds-identity-config/compare/v0.1.0...v0.2.0) (2024-02-22)


### Features

* initial commit ([e24a266](https://github.com/defenseunicorns/uds-identity-config/commit/e24a266ec2b50274b5205d51c2fde1b2c130bf18))
* **main:** initial release 0.1.0 ([#7](https://github.com/defenseunicorns/uds-identity-config/issues/7)) ([4e5f710](https://github.com/defenseunicorns/uds-identity-config/commit/4e5f710ddb08865e230d922a1c4443b2422f08e4))


### Miscellaneous

* update codeowners ([83d0c92](https://github.com/defenseunicorns/uds-identity-config/commit/83d0c929f448965032fe2656e1274c65bf09637d))

## 0.1.0 (2024-02-22)


### Features

* initial commit ([e24a266](https://github.com/defenseunicorns/uds-identity-config/commit/e24a266ec2b50274b5205d51c2fde1b2c130bf18))
