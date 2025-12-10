# ArgusOmni - Universal Test Orchestrator

**Write complex integration tests in simple YAML - no code required!**

ArgusOmni lets you test REST APIs, gRPC services, file systems, and run shell commands all in one YAML file. Perfect for microservice architectures, CI/CD pipelines, and end-to-end testing.

---

## Why ArgusOmni?

### The Problem
Testing modern distributed systems is hard:
- Multiple tools for different protocols (REST, gRPC, file systems)
- Complex test scripts scattered across repositories
- Difficult to maintain and understand test flows
- Hard to run tests in CI/CD pipelines

### The Solution
One tool, one YAML file, all your tests:
```yaml
tests:
  - name: "Login to API"
    type: REST
    rest:
      url: "https://api.example.com/login"
      method: POST
      body: { email: "user@example.com" }
    extract:
      token: "$.accessToken"

  - name: "Save token to file"
    type: FS
    fs:
      write:
        path: "/tmp/token.txt"
        content: "{{token}}"
```

---

## Features

- ✅ **REST/HTTP Testing** - Test any HTTP API with automatic cookie management
- ✅ **gRPC Testing** - Dynamic gRPC calls without code generation
- ✅ **File System** - Create, read, validate files and directories
- ✅ **Shell Commands** - Run bash scripts for setup and cleanup
- ✅ **Variables** - Extract and reuse data between test steps
- ✅ **Beautiful Reports** - Colored console output + HTML + JSON reports
- ✅ **CI/CD Ready** - Clean exit codes for automation

---

## Installation

### Option 1: Build from source
```bash
git clone https://github.com/your-org/argusomni-cli.git
cd argusomni-cli
./gradlew build

# Create alias
alias argus='java -jar build/libs/ArgusOmni-CLI-1.0.0.jar'
```

### Option 2: Use directly
```bash
./gradlew bootRun --args="run your-test.yml"
```

---

## Quick Start

### 1. Create your first test file

**test.yml:**
```yaml
env:
  BASE_URL: "https://jsonplaceholder.typicode.com"

tests:
  - name: "Get user data"
    type: REST
    rest:
      url: "{{BASE_URL}}/users/1"
      method: GET
    extract:
      userName: "$.name"
      userEmail: "$.email"
    expect:
      status: 200
      jsonContains:
        id: 1
```

### 2. Run it
```bash
argus run test.yml
```

### 3. See results
```
✓ Get user data (234ms)
  Extracted: userName=Leanne Graham, userEmail=Sincere@april.biz

═══════════════════════════════════════
Total Tests: 1
Passed: 1
Failed: 0
Total Duration: 234ms

✓ All tests passed!
```

---

## What Can You Test?

### REST APIs
```yaml
- name: "Login and get token"
  type: REST
  rest:
    url: "{{API_URL}}/auth/login"
    method: POST
    headers:
      Content-Type: "application/json"
    body:
      email: "admin@example.com"
      password: "secret123"
  extract:
    accessToken: "$.accessToken"
  expect:
    status: 200
```

### File Operations
```yaml
- name: "Create config file"
  type: FS
  fs:
    write:
      path: "/tmp/config.json"
      content: '{"api_url": "{{API_URL}}"}'

- name: "Verify file exists"
  type: FS
  fs:
    exists: "/tmp/config.json"
```

### Shell Commands
```yaml
- name: "Setup test database"
  type: BASH
  bash:
    command: |
      docker run -d -p 5432:5432 \
        -e POSTGRES_PASSWORD=test \
        postgres:15
  expect:
    status: 0

- name: "Run migration script"
  type: BASH
  bash:
    script: "./scripts/migrate.sh"
    timeout: 30000
  expect:
    status: 0
```

### Complete Workflow
```yaml
env:
  API_URL: "http://localhost:8080"

tests:
  # 1. Setup
  - name: "Start test server"
    type: BASH
    bash:
      command: "docker-compose up -d"

  # 2. Test API
  - name: "Health check"
    type: REST
    rest:
      url: "{{API_URL}}/health"
      method: GET
    expect:
      status: 200

  # 3. Cleanup
  - name: "Stop server"
    type: BASH
    bash:
      command: "docker-compose down"
    continueOnError: true
```

---

## Test Step Types

| Type | What It Does | When To Use |
|------|--------------|-------------|
| `REST` | HTTP/REST API calls | Testing web APIs, webhooks |
| `GRPC` | gRPC service calls | Microservice communication |
| `FS` | File system operations | Config files, test data |
| `BASH` | Shell commands | Setup, cleanup, scripts |
| `SET` | Variable management | Dynamic test data |
| `TRANSFORM` | Data transformation | Hashing, encoding |

---

## Variables and Extraction

### Extract data from responses
```yaml
- name: "Create user"
  type: REST
  rest:
    url: "{{API_URL}}/users"
    method: POST
    body: { name: "John Doe" }
  extract:
    userId: "$.id"          # Extract from JSON response
    createdAt: "$.timestamp"

- name: "Get user details"
  type: REST
  rest:
    url: "{{API_URL}}/users/{{userId}}"  # Reuse extracted value
    method: GET
```

### Built-in functions
```yaml
variables:
  timestamp: "{{date:yyyy-MM-dd}}"           # Current date
  uniqueId: "{{uuid}}"                       # Random UUID
  authHeader: "{{base64:user:password}}"     # Base64 encode
```

---

## CI/CD Integration

### Exit Codes
- `0` - All tests passed ✓
- `1` - Test failure ✗
- `2` - Invalid YAML or configuration error

### GitHub Actions Example
```yaml
name: Integration Tests
on: [push]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Run ArgusOmni tests
        run: |
          java -jar argusomni.jar run tests/api-tests.yml

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: test-reports/
```

### GitLab CI Example
```yaml
test:
  stage: test
  script:
    - java -jar argusomni.jar run tests/integration.yml
  artifacts:
    when: always
    paths:
      - test-reports/
      - test-logs/
```

---

## Advanced Features

### Conditional execution
```yaml
- name: "Optional cleanup"
  type: BASH
  bash:
    command: "rm -rf /tmp/test-data"
  continueOnError: true  # Don't fail if this fails
```

### Environment variables
```yaml
env:
  BASE_URL: "http://localhost:8080"
  API_KEY: "${API_KEY}"  # From environment

tests:
  - name: "Call API"
    type: REST
    rest:
      url: "{{BASE_URL}}/api/data"
      headers:
        X-API-Key: "{{API_KEY}}"
```

### JSONPath extraction
```yaml
extract:
  userId: "$.user.id"                    # Simple path
  firstEmail: "$.users[0].email"         # Array access
  allNames: "$.users[*].name"            # All items
  adminId: "$.users[?(@.role=='admin')].id"  # Filter
```

---

## Output and Reports

### Console Output
![Console Output](docs/console-output.png)

- ✅ Colored test results
- ⏱️ Execution time per test
- 📊 Summary statistics
- 🔍 Detailed error messages

### HTML Report
![HTML Report](docs/html-report.png)

- Complete test execution details
- Request/response bodies
- Variable values
- Timestamps

### JSON Log
```json
{
  "testName": "Login to API",
  "status": "SUCCESS",
  "duration": 234,
  "request": {
    "method": "POST",
    "url": "http://localhost:8080/login",
    "body": {"email": "user@example.com"}
  },
  "response": {
    "status": 200,
    "body": {"accessToken": "eyJ..."}
  },
  "extractedVariables": {
    "accessToken": "eyJ..."
  }
}
```

---

## FAQ

**Q: Do I need to write Java code?**
A: No! Everything is YAML. Just write your test steps.

**Q: Can I test gRPC without .proto files?**
A: Yes! ArgusOmni uses dynamic gRPC reflection.

**Q: How do I debug failed tests?**
A: Check the HTML report in `test-reports/` or JSON log in `test-logs/`.

**Q: Can I run tests in parallel?**
A: Tests run sequentially to maintain state between steps. For parallel execution, split into separate YAML files.

**Q: Does it work with Docker?**
A: Yes! Use BASH steps to control Docker containers.

---

## Examples

See the `examples/` directory for complete test suites:
- [REST API Testing](examples/rest-api-test.yml)
- [gRPC Testing](examples/grpc-test.yml)
- [File System Testing](examples/filesystem-test.yml)
- [Database Setup](examples/database-test.yml)
- [Complete E2E Flow](examples/e2e-test.yml)

---

## Support

- 📖 [Full Documentation](PROMPT.md)
- 🐛 [Report Issues](https://github.com/your-org/argusomni-cli/issues)
- 💬 [Discussions](https://github.com/your-org/argusomni-cli/discussions)

---

## License

MIT License - see [LICENSE](LICENSE) file for details.
