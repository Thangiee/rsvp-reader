# Move shared tests to JVM-only

## Motivation

Organizational clarity. Shared tests are pure domain logic with no JS-specific behavior to verify. Cross-compiling tests adds build time for no benefit. JVM-only placement also allows free use of JVM-only test deps.

## Changes

1. Move 9 test suites from `shared/src/test/scala/rsvpreader/` to `shared/.jvm/src/test/scala/rsvpreader/` (alongside existing `PersistenceSuite`)
2. Move `shared/src/test/resources/Douglass_essay.txt` to `shared/.jvm/src/test/resources/`
3. In `build.sbt`, move munit test deps from cross-compiled `.settings` to `.jvmSettings` (use `%%` instead of `%%%`)
