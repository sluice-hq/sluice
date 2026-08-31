# Third-Party Notices

Sluice uses the third-party software listed below. The Sluice source code is
licensed separately under the repository-root [Apache License 2.0](LICENSE).

This inventory covers every direct dependency declared in
`backend/build.gradle` and `frontend/package.json`. Versions are the resolved
versions from Gradle and `frontend/package-lock.json`, not version ranges.
Runtime dependencies are separated from build and test tooling. Transitive
dependencies are intentionally deferred to the automated software bill of
materials and license review in ticket L-08B.

## Backend runtime dependencies

| Component | Resolved version | License | Upstream source |
|---|---:|---|---|
| `org.springframework.boot:spring-boot-starter-actuator` | 4.1.0 | Apache-2.0 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) |
| `org.springframework.boot:spring-boot-starter-data-jpa` | 4.1.0 | Apache-2.0 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) |
| `org.springframework.boot:spring-boot-starter-flyway` | 4.1.0 | Apache-2.0 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) |
| `org.springframework.boot:spring-boot-starter-validation` | 4.1.0 | Apache-2.0 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) |
| `org.springframework.boot:spring-boot-starter-webmvc` | 4.1.0 | Apache-2.0 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) |
| `org.springframework.boot:spring-boot-starter-amqp` | 4.1.0 | Apache-2.0 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) |
| `org.springframework.boot:spring-boot-starter-security` | 4.1.0 | Apache-2.0 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) |
| `org.springdoc:springdoc-openapi-starter-webmvc-api` | 3.0.3 | Apache-2.0 | [springdoc/springdoc-openapi](https://github.com/springdoc/springdoc-openapi) |
| `io.jsonwebtoken:jjwt-api` | 0.12.5 | Apache-2.0 | [jwtk/jjwt](https://github.com/jwtk/jjwt) |
| `io.jsonwebtoken:jjwt-impl` | 0.12.5 | Apache-2.0 | [jwtk/jjwt](https://github.com/jwtk/jjwt) |
| `io.jsonwebtoken:jjwt-jackson` | 0.12.5 | Apache-2.0 | [jwtk/jjwt](https://github.com/jwtk/jjwt) |
| `org.flywaydb:flyway-database-postgresql` | 12.4.0 | Apache-2.0 | [flyway/flyway](https://github.com/flyway/flyway) |
| `com.azure:azure-storage-blob` | 12.25.1 | MIT | [Azure/azure-sdk-for-java](https://github.com/Azure/azure-sdk-for-java) |
| `com.networknt:json-schema-validator` | 1.5.9 | Apache-2.0 | [networknt/json-schema-validator](https://github.com/networknt/json-schema-validator) |
| `com.github.usefulness:webp-imageio` | 0.11.0 | Apache-2.0 | [usefulness/webp-imageio](https://github.com/usefulness/webp-imageio) |
| `io.micrometer:micrometer-registry-prometheus` | 1.17.0 | Apache-2.0 | [micrometer-metrics/micrometer](https://github.com/micrometer-metrics/micrometer) |
| `org.postgresql:postgresql` | 42.7.11 | BSD-2-Clause | [pgjdbc/pgjdbc](https://github.com/pgjdbc/pgjdbc) |

## Frontend runtime dependencies

| Component | Resolved version | License | Upstream source |
|---|---:|---|---|
| `@base-ui/react` | 1.7.0 | MIT | [mui/base-ui](https://github.com/mui/base-ui) |
| `@hookform/resolvers` | 5.7.1 | MIT | [react-hook-form/resolvers](https://github.com/react-hook-form/resolvers) |
| `@tanstack/react-query` | 5.101.4 | MIT | [TanStack/query](https://github.com/TanStack/query) |
| `class-variance-authority` | 0.7.1 | Apache-2.0 | [joe-bell/cva](https://github.com/joe-bell/cva) |
| `clsx` | 2.1.1 | MIT | [lukeed/clsx](https://github.com/lukeed/clsx) |
| `lodash` | 4.18.1 | MIT | [lodash/lodash](https://github.com/lodash/lodash) |
| `lucide-react` | 1.28.0 | ISC | [lucide-icons/lucide](https://github.com/lucide-icons/lucide) |
| `next` | 16.3.0 | MIT | [vercel/next.js](https://github.com/vercel/next.js) |
| `react` | 19.2.8 | MIT | [facebook/react](https://github.com/facebook/react) |
| `react-dom` | 19.2.8 | MIT | [facebook/react](https://github.com/facebook/react) |
| `react-hook-form` | 7.84.0 | MIT | [react-hook-form/react-hook-form](https://github.com/react-hook-form/react-hook-form) |
| `shadcn` | 4.16.1 | MIT | [shadcn-ui/ui](https://github.com/shadcn-ui/ui) |
| `tailwind-merge` | 3.6.0 | MIT | [dcastil/tailwind-merge](https://github.com/dcastil/tailwind-merge) |
| `tw-animate-css` | 1.4.0 | MIT | [Wombosvideo/tw-animate-css](https://github.com/Wombosvideo/tw-animate-css) |
| `zod` | 4.4.3 | MIT | [colinhacks/zod](https://github.com/colinhacks/zod) |

## Backend build and test tooling

These components are required to build or test Sluice but are not application
runtime dependencies.

| Component | Resolved version | License | Upstream source |
|---|---:|---|---|
| Gradle Wrapper | 9.5.1 | Apache-2.0 | [gradle/gradle](https://github.com/gradle/gradle) |
| Spring Boot Gradle plugin | 4.1.0 | Apache-2.0 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) |
| `io.spring.dependency-management` Gradle plugin | 1.1.7 | Apache-2.0 | [spring-gradle-plugins/dependency-management-plugin](https://github.com/spring-gradle-plugins/dependency-management-plugin) |
| `org.springframework.boot:spring-boot-starter-test` | 4.1.0 | Apache-2.0 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) |
| `org.springframework.boot:spring-boot-testcontainers` | 4.1.0 | Apache-2.0 | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot) |
| `org.springframework.security:spring-security-test` | 7.1.0 | Apache-2.0 | [spring-projects/spring-security](https://github.com/spring-projects/spring-security) |
| `org.testcontainers:testcontainers-junit-jupiter` | 2.0.5 | MIT | [testcontainers/testcontainers-java](https://github.com/testcontainers/testcontainers-java) |
| `org.testcontainers:testcontainers-postgresql` | 2.0.5 | MIT | [testcontainers/testcontainers-java](https://github.com/testcontainers/testcontainers-java) |
| `org.testcontainers:testcontainers-rabbitmq` | 2.0.5 | MIT | [testcontainers/testcontainers-java](https://github.com/testcontainers/testcontainers-java) |
| `org.junit.platform:junit-platform-launcher` | 6.0.3 | EPL-2.0 | [junit-team/junit5](https://github.com/junit-team/junit5) |

## Frontend build and test tooling

These packages are declared in `devDependencies` and are not application
runtime dependencies.

| Component | Resolved version | License | Upstream source |
|---|---:|---|---|
| `@playwright/test` | 1.62.1 | Apache-2.0 | [microsoft/playwright](https://github.com/microsoft/playwright) |
| `@tailwindcss/postcss` | 4.3.3 | MIT | [tailwindlabs/tailwindcss](https://github.com/tailwindlabs/tailwindcss) |
| `@types/lodash` | 4.17.25 | MIT | [DefinitelyTyped/DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) |
| `@types/node` | 20.19.43 | MIT | [DefinitelyTyped/DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) |
| `@types/react` | 19.2.18 | MIT | [DefinitelyTyped/DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) |
| `@types/react-dom` | 19.2.4 | MIT | [DefinitelyTyped/DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) |
| `eslint` | 9.39.5 | MIT | [eslint/eslint](https://github.com/eslint/eslint) |
| `eslint-config-next` | 16.3.0 | MIT | [vercel/next.js](https://github.com/vercel/next.js) |
| `tailwindcss` | 4.3.3 | MIT | [tailwindlabs/tailwindcss](https://github.com/tailwindlabs/tailwindcss) |
| `typescript` | 5.9.3 | Apache-2.0 | [microsoft/TypeScript](https://github.com/microsoft/TypeScript) |

## Upstream attribution notices

The following text is reproduced from NOTICE files included by direct
dependencies. It does not change the terms of their licenses.

### Spring Boot 4.1.0

> Spring Boot 4.1.0
> Copyright (c) 2012-2026 VMware, Inc.
> This product is licensed to you under the Apache License, Version 2.0
> (the "License"). You may not use this product except in compliance with
> the License.

### Playwright 1.62.1

> Playwright
> Copyright (c) Microsoft Corporation
> This software contains code derived from the Puppeteer project
> (https://github.com/puppeteer/puppeteer), available under the Apache 2.0
> license (https://github.com/puppeteer/puppeteer/blob/master/LICENSE).

No other inspected direct dependency artifact redistributed by Sluice contained
a `NOTICE` file. License files already present inside backend dependency JARs
remain inside the executable JAR, which also includes the repository Apache
license. The frontend build consolidates the original license and notice files
from every installed direct runtime package into `THIRD_PARTY_LICENSES.txt` and
fails if any package does not provide one. The SPDX identifiers above can be
looked up at [spdx.org/licenses](https://spdx.org/licenses/).

## Distribution

- Source distributions include this repository-root file.
- The Spring Boot executable JAR includes it as
  `META-INF/THIRD_PARTY_NOTICES.md` and includes the repository license as
  `META-INF/LICENSE`.
- The Next.js production build includes `.next/THIRD_PARTY_NOTICES.md` plus the
  exact direct-runtime package texts in `.next/THIRD_PARTY_LICENSES.txt`.
- Production container definitions added in L-08 must retain the notice from
  those application artifacts and expose it at a conventional location such as
  `/licenses/THIRD_PARTY_NOTICES.md`.

The separately pulled development-service images in `docker-compose.yml` are
not redistributed by this repository and are not part of this direct
application dependency inventory.
