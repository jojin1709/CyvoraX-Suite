# CyvoraX Suite v1.5.0

## Highlights

- Added secure GitHub Releases updater authentication for private repositories.
- Added token source priority for updater auth: `CYVORAX_GITHUB_TOKEN`, then local profile settings.
- Added AI Providers support for Groq, OpenRouter, Cerebras, and Mistral.
- Added encrypted local AI provider key storage under the user profile.
- Added AI provider connection testing against provider model endpoints.
- Added Settings UI for GitHub updater credentials and AI providers.
- Added token masking for UI messages, logs, crash reports, diagnostics, and tests.
- Added diagnostics for updater and AI provider authentication status.

## Validation

- Maven clean, test, and package are required before release.
- Windows installer output: `CyvoraX-Setup-1.5.0.exe`.
