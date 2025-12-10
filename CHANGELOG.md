# [3.1.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/3.0.0...3.1.0) (2025-12-10)


### Features

* secret support for fields ([4240108](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/42401083c6a3078eeaa38447408fafb97c6203e0))

# [3.0.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/2.0.0...3.0.0) (2025-06-03)


### chore

* **deps:** bump gravitee-apim to 4.8.0-SNAPSHOT ([60c8b1f](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/60c8b1f49b51ee825f9bfc6214504f300e44f43c))


### BREAKING CHANGES

* **deps:** require at least APIM 4.8

# [3.0.0-alpha.1](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/2.0.0...3.0.0-alpha.1) (2025-04-22)


### chore

* **deps:** bump gravitee-apim to 4.8.0-SNAPSHOT ([81b6a37](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/81b6a3750791b0cfbce8082d3c68160e31448a47))


### BREAKING CHANGES

* **deps:** require at least APIM 4.8

# [2.0.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.3.1...2.0.0) (2025-03-10)


### chore

* bump aws java sdk to 2.30.26 ([ad64c97](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/ad64c9790252157f9f16e40a4a97d3408086fde4))
* use gravitee-apim-bom in aws lambda policy ([c3ce44a](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/c3ce44a84a738d6c2e3528186d70361bf78bc706))


### Features

* add task file to build and copy the plugin easily on APIM ([83961f8](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/83961f89a4743c9de47f0e7dc8556077a888ff5f))
* hide scope in policy studio v4 ([c861d41](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/c861d41d525816a191efeff7217d2f984a80e46c))
* implement aws lambda policy for V4 APIs ([0ef1925](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/0ef1925201d0e18d0b361ed671ceb8201aa0e1bc))


### BREAKING CHANGES

* implementation has changed and use sdk v2 now

(cherry picked from commit 9783474d1b01007e931a9bc1cbf02b3943615420)
* Updating this sdk changes all the implementation of the policy

(cherry picked from commit 73435a3a69c7ca270c6076fcac5ab3e6f24d9ce1)
* require at least APIM 4.7

(cherry picked from commit ce8ce2e98d3894e5f6a64a7a3679a76a20043f40)

## [1.3.1](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.3.0...1.3.1) (2025-03-10)


### Bug Fixes

* revert pr 109 ([37078eb](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/37078ebfdf9c6206cb326ab9e564030bc8118ca2))

# [1.3.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.2.0...1.3.0) (2025-03-10)


### Features

* add task file to build and copy the plugin easily on APIM ([90ec926](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/90ec926b7941241ffd698a1405911ad35517cb9c))
* hide scope in policy studio v4 ([a4b704e](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/a4b704eb2319f2a68369c8a3abb1f9dc871a78fe))
* implement aws lambda policy for V4 APIs ([9783474](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/9783474d1b01007e931a9bc1cbf02b3943615420))

# [1.2.0](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.1.2...1.2.0) (2024-06-25)


### Bug Fixes

* **aws-lambda:** bump gravitee version ([cbb98ae](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/cbb98aee04673dad69f4e6092a95cb6cf2573999))


### Features

* **aws-lambda:** Add support for STS to manage dynamic authentication credentials ([eaf7eb1](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/eaf7eb11172de031bf202467ce26ec6c3a708da9))

## [1.1.2](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.1.1...1.1.2) (2023-07-21)


### Bug Fixes

* improve README ([7ce55a2](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/7ce55a2ac591adce63317f2cbfbc95e9224964dc))

## [1.1.1](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.1.0...1.1.1) (2023-07-20)


### Bug Fixes

* update policy description ([33ce709](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/33ce709bae20cdea3e5d87906f3cc007fd2940f2))

# [[secure]](https://github.com/gravitee-io/gravitee-policy-aws-lambda/compare/1.0.0...[secure]) (2022-01-21)


### Features

* **headers:** Internal rework and introduce HTTP Headers API ([e569a8d](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/e569a8da2713651d5d311aa0c6f206f64d1955d4)), closes [gravitee-io/issues#6772](https://github.com/gravitee-io/issues/issues/6772)
* **perf:** adapt policy for new classloader system ([7d96d3d](https://github.com/gravitee-io/gravitee-policy-aws-lambda/commit/7d96d3d5d55dff41eb9634efb2c1035fe8620478)), closes [gravitee-io/issues#6758](https://github.com/gravitee-io/issues/issues/6758)
