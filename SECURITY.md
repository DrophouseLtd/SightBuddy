# Security policy

## Reporting a vulnerability

Please **don't** open a public issue for a security problem.

Report it privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability**. Only the maintainers can see the report.

Please include:

- what the problem is and what an attacker could do with it,
- the steps to reproduce it, with the app version (Settings shows it) and the Android version,
- any proof of concept, logs or screenshots, with personal data removed.

We aim to acknowledge a report within 7 days and to agree a timeline for a fix and its disclosure with you. There is no bug bounty, but we are glad to credit you in the release notes if you want.

## Supported versions

Security fixes go into the latest release only. Please check that the problem exists in the latest version on Google Play or on the [Releases](../../releases) page.

## What is in scope

- The Sight Buddy app and the code in this repository, for example how the OpenAI API key is stored (`core/ApiKeyStore.kt`), how downloaded models are verified, and anything that could send user data off the phone without the user asking.

Problems in third-party components (ML Kit, LiteRT-LM, ONNX Runtime, sherpa-onnx and others) should be reported to their projects. If one of them affects Sight Buddy, tell us too, so we can update.
