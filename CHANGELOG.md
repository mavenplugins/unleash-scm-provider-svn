# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

<!-- Format restrictions - see https://common-changelog.org and https://keepachangelog.com/ for details -->
<!-- Each Release must start with a line for the release version of exactly this format: ## [version] -->
<!-- The subsequent comment lines start with a space - not to irritate the release scripts parser!
 ## [major.minor.micro]
 <empty line> - optional sub sections may follow like:
 ### Added:
 - This feature was added
 <empty line>
 ### Changed:
 - This feature was changed
 <empty line>
 ### Removed:
 - This feature was removed
 <empty line>
 ### Fixed:
 - This issue was fixed
 <empty line>
 <empty line> - next line is the starting of the previous release
 ## [major.minor.micro]
 <empty line>
 <...>
 !!! In addition the compare URL links are to be maintained at the end of this CHANGELOG.md as follows.
     These links provide direct access to the GitHub compare vs. the previous release.
     The particular link of a released version will be copied to the release notes of a release accordingly.
     At the end of this file appropriate compare links have to be maintained for each release version in format:
 
  +-current release version
  |
  |                   +-URL to this repo                  previous release version tag-+       +-current release version tag
  |                   |                                                                |       |
 [major.minor.micro]: https://github.com/mavenplugins/unleash-scm-provider-svn/compare/vM.N.u..vM.N.u
-->
## [Unreleased]

### 🚨 Removed
- TBD

### 💥 Breaking
- TBD

### 📢 Deprecated
- TBD

### 🚀 New Features
- TBD

### 🐛 Fixes
- TBD

### ✨ Improvements
- TBD

### 🔧 Internal Changes
- TBD

### 🚦 Tests
- TBD

### 📦 Updates
- TBD

### 🔒 Security
- TBD

### 📝 Documentation Updates
- TBD
-->

## [Unreleased]

### Changes
- TBD


## [3.0.2]
<!-- !!! Align version in badge URLs as well !!! -->
[![3.0.2 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-svn?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=3.0.2)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-svn/3.0.2)

### Summary
- Remove explicit dependency to `com.google.guava:guava`
- No further functional or code change

### 💥 Breaking
- 👉 Use with `unleash-maven-plugin >= v3.2.1` only to comply with guava dependency provided through `unleash-scm-provider-api` by `cdi-plugin-utils`!

### 📦 Updates
- pom.xml:
  - Remove explicit dependency to `com.google.guava:guava`
  - Update `junit` to version `4.13.2`


## [3.0.1]
<!-- !!! Align version in badge URLs as well !!! -->
[![3.0.1 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-svn?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=3.0.1)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-svn/3.0.1)

### Summary
- Update dependency `unleash-scm-provider-api` to scope `provided`
- No further functional or code change

### Updates
- pom.xml:
  - update dependency `unleash-scm-provider-api` to scope `provided`


## [3.0.0]
<!-- !!! Align version in badge URLs as well !!! -->
[![3.0.0 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-svn?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=3.0.0)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-svn/3.0.0)

### Summary
- Update dependency `io.github.mavenplugins:cdi-plugin-utils:3.4.1` -> `io.github.mavenplugins:cdi-plugin-utils:4.0.0`
  - => work for Java 8, 11, 17 and 21 by CDI WELD 4.0.3.Final with Jakarta Inject API

### Compatibility
- 👉 This release requires to be used with `unleash-maven-plugin >= v3.0.0` only! It will NOT work with former versions of `unleash-maven-plugin`<br>
  Reason: CDI dependencies did have been changed from Javax to Jakarta EE

### Updates
- pom.xml:
  - refer to `unleash-scm-provider-api 3.0.0`


## [2.2.0]
<!-- !!! Align version in badge URLs as well !!! -->
[![2.2.0 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-svn?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=2.2.0)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-svn/2.2.0)

### Summary
- Fix file URI relativizing on Windows OS - according to [unleash-maven-plugin #8](https://github.com/mavenplugins/unleash-maven-plugin/issues/8)
- Update dependency to `unleash-scm-provider-api 2.11.0`

### Updates
- FileToWCRelativePath.java:
  - convert File URIs to lower case on Windows OS runtimes

- SVNUtil.java:
  - make use of FileToWCRelativePath.apply(File)
    for URI relativizing

- pom.xml:
  - refer to `unleash-scm-provider-api 2.11.0`


## [2.1.0]
<!-- !!! Align version in badge URLs as well !!! -->
[![2.1.0 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-svn?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=2.1.0)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-svn/2.1.0)

### Summary
- Initial release of this artifact with new groupId `io.github.mavenplugins`
- Codewise identical with `com.itemis.maven.plugins:unleash-scm-provider-svn:2.1.0`<br>No more features nor changes
- Update Java compile version to `1.8` to comply with dependency `io.github.mavenplugins:unleash-scm-provider-api:2.10.0`
- Update m2e launch config to Eclipse 2023-12
- Released to Maven Central

### Updates
- pom.xml:
  - update parent POM reference
  - update groupId to `io.github.mavenplugins`
  - update `version.java` `1.6` -> `1.8`
  - update URLs to fit with new repo location
  - remove obsolete profile disable-java8-doclint

- README.md:
  - add URLs for build badges


<!--
## []

### NeverReleased
- This is just a dummy placeholder to make the parser of GHCICD/release-notes-from-changelog@v1 happy!
-->

[Unreleased]: https://github.com/mavenplugins/unleash-scm-provider-svn/compare/v3.0.2..HEAD
[3.0.2]: https://github.com/mavenplugins/unleash-scm-provider-svn/compare/v3.0.1..v3.0.2
[3.0.1]: https://github.com/mavenplugins/unleash-scm-provider-svn/compare/v3.0.0..v3.0.1
[3.0.0]: https://github.com/mavenplugins/unleash-scm-provider-svn/compare/v2.2.0..v3.0.0
[2.2.0]: https://github.com/mavenplugins/unleash-scm-provider-svn/compare/v2.1.0..v2.2.0
[2.1.0]: https://github.com/mavenplugins/unleash-scm-provider-svn/releases/tag/v2.1.0
