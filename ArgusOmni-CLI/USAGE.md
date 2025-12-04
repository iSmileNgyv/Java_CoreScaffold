# ArgusOmni CLI Usage Guide

## Installation

```bash
cd ArgusOmni-CLI
./start.sh
```

This will:
1. Build the JAR using Gradle
2. Install `argus` command to `/usr/local/bin`
3. Make it globally accessible

## Usage

### Basic Commands

```bash
# Show help
argus --help

# Run a test suite
argus run tests/example-test.yml

# Run with verbose output
argus run tests/chronovcs-test.yml --verbose

# Override environment
argus run tests/example-test.yml --env=production
```

### Exit Codes

- `0` - All tests passed ✓
- `1` - Test failures ✗
- `2` - Invalid YAML or setup error

### Test File Structure

```yaml
env:
  base_url: "http://localhost:8080"

variables:
  username: "admin"

tests:
  - name: "Login test"
    rest:
      url: "{{base_url}}/auth/login"
      method: POST
      body:
        username: "{{username}}"
        password: "secret"
    extract:
      token: "$.token"
    expect:
      status: 200
```

### Supported Step Types

1. **REST** - HTTP/REST API calls
2. **gRPC** - Dynamic gRPC calls (no codegen)
3. **fs** - File system validation
4. **resolve_path** - Path resolution
5. **set** - Set variables
6. **transform** - Transform variables

### Variable Functions

- `{{variable_name}}` - Simple variable
- `{{file_hash:path}}` - File SHA256 hash
- `{{date:yyyy-MM-dd}}` - Current date
- `{{base64:text}}` - Base64 encode
- `{{uuid}}` - Generate UUID
- `{{url_encode:text}}` - URL encode

### Examples

#### REST API Test
```yaml
- name: "Get user"
  rest:
    url: "{{base_url}}/users/123"
    method: GET
    headers:
      Authorization: "Bearer {{token}}"
  expect:
    status: 200
    json:
      id: 123
```

#### gRPC Test
```yaml
- name: "gRPC call"
  grpc:
    proto: "./protos/service.proto"
    host: "localhost:50051"
    service: "UserService"
    method: "GetUser"
    request:
      id: 123
  extract:
    username: "$.name"
```

#### File System Test
```yaml
- name: "Check file"
  fs:
    exists: "/path/to/file.txt"
    size: "/path/to/file.txt"
```

## CI/CD Integration

```bash
#!/bin/bash
argus run tests/integration-tests.yml
exit_code=$?

if [ $exit_code -eq 0 ]; then
  echo "✓ Tests passed"
else
  echo "✗ Tests failed"
  exit 1
fi
```

## Troubleshooting

### Command not found

```bash
# Ensure /usr/local/bin is in PATH
echo 'export PATH="/usr/local/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### Build failed

```bash
# Clean and rebuild
./gradlew clean build
```

### Tests failing

```bash
# Run with verbose mode to see details
argus run test.yml --verbose
```
