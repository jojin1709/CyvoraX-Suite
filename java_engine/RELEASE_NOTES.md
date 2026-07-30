# CyvoraX Suite v1.6.0

## Highlights

- Added the Burp-style desktop UI refresh with grouped top navigation, cleaner workspace controls, status bar segments, and global table/editor polish.
- Reworked Proxy Intercept, HTTP History, Repeater, Scanner, Dashboard, Match & Replace, Intruder, and Workspace Launcher surfaces.
- Added Proxy Settings for listener host/port, upstream proxy, timeout, and Apply & Restart.
- Added active scanner task telemetry in the dashboard.
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
- Windows installer output: `CyvoraX-Setup-1.6.0.exe`.
