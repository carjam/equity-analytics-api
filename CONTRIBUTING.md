# Contributing to Equity Analytics API

This is a **proprietary portfolio project**. Source code is view-only under [LICENSE](LICENSE). External contributions are welcome for review and discussion, but merging changes requires prior agreement with the copyright holder. See [docs/NOTICE.md](docs/NOTICE.md).

## Development Setup

1. Clone repository:
   git clone https://github.com/carjam/equity-analytics-api.git
   cd equity-analytics-api

2. Install dependencies:
   - Java 17+
   - Gradle (or use wrapper)
   - Docker (optional)

3. Set environment variables:
   export ALPHA_VANTAGE_API_KEY=your_key
   # Or leave unset to use mock service

4. Run tests:
   ./gradlew test

5. Run locally:
   ./gradlew run

6. Access at: http://localhost:8080

## Code Standards

- Kotlin style guide: Follow Kotlin conventions
- Line length: 120 characters
- Formatting: Use IntelliJ IDEA defaults
- Testing: Minimum 80% coverage

## Pull Request Process

1. Create feature branch: `git checkout -b feature/your-feature`
2. Make changes with clear commit messages
3. Add tests for new functionality
4. Run tests: `./gradlew test`
5. Push and create PR
6. Wait for CI to pass
7. Request review
8. Address feedback
9. Merge when approved

## Commit Message Format

feat: Add new feature
fix: Fix bug
docs: Update documentation
test: Add tests
refactor: Code refactoring
chore: Build/config changes

## Testing

- Unit tests: `./gradlew test`
- Integration tests: Included in test task
- Smoke tests: `./tests/smoke/smoke-test.sh`

## Questions?

Open an issue or discussion on GitHub, or email jamescarson3rd@gmail.com for licensing inquiries.
