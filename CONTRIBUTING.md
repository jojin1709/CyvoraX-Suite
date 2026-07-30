# Contributing to CyvoraX Suite

Thank you for your interest in contributing to **CyvoraX Suite**! We welcome bug reports, documentation updates, native polyglot engine enhancements, and new feature contributions.

---

## Code of Conduct

Please treat all community members and maintainers with respect. All contributions must adhere to ethical security research principles.

---

## How to Contribute

### 1. Reporting Bugs & Vulnerabilities

- **Bug Reports**: Open an issue on GitHub describing the unexpected behavior, steps to reproduce, and system environment.
- **Security Vulnerabilities**: If you discover a security vulnerability in CyvoraX Suite itself, please report it responsibly via GitHub Security Advisories or by contacting the maintainer directly before public disclosure.

### 2. Suggesting Features

- Open a feature request issue detailing the proposed capability, UI design, and security research benefits.

### 3. Development & Pull Requests

1. **Fork the Repository**: Create a fork of [CyvoraX-Suite](https://github.com/jojin1709/CyvoraX-Suite).
2. **Clone & Branch**:
   ```bash
   git clone https://github.com/your-username/CyvoraX-Suite.git
   cd CyvoraX-Suite
   git checkout -b feature/my-new-feature
   ```
3. **Build & Verify**:
   - Ensure the project builds cleanly:
     ```powershell
     .\tools\apache-maven-3.9.14\bin\mvn.cmd test-compile
     ```
   - Verify UI changes by launching:
     ```powershell
     .\run.ps1
     ```
4. **Commit Changes**: Follow clean, descriptive commit messages.
5. **Open a Pull Request**: Submit your PR targeting the `main` branch.

---

## Rules of Engagement

- All native polyglot scanning modules (**C, C++, C#, Go, Rust, Python, JavaScript**) integrated into CyvoraX Suite must strictly operate within authorized security research parameters.
- Do not commit malicious payloads or destructive exploits meant for unauthorized targets.

---

## License

By contributing to CyvoraX Suite, you agree that your contributions will be licensed under the project's [Apache License 2.0](LICENSE).
