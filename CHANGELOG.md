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
<!--
## [Unreleased]

### Additions
- TBD

### Changes
- TBD

### Deprecated
- TBD

###	Removals
- TBD

### Fixes
- TBD

###	Security
- TBD
-->

## [Unreleased]

### Changes
- TBD


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

[Unreleased]: https://github.com/mavenplugins/unleash-scm-provider-svn/compare/v2.1.0..HEAD
[2.1.0]: https://github.com/mavenplugins/unleash-scm-provider-svn/releases/tag/v2.1.0
