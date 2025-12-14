# ArgusOmni-CLI

**Universal YAML-Based Test Orchestration Framework**

ArgusOmni-CLI is a powerful, flexible test automation framework that allows you to define and execute complex test scenarios using simple YAML configuration files. It supports REST APIs, gRPC services, file system operations, bash commands, and more.

## 📑 Table of Contents

- [Quick Start](#quick-start)
- [Core Concepts](#core-concepts)
- [Step Types](#step-types)
- [Advanced Features](#advanced-features)
- [Assertions](#assertions)
- [Configuration](#configuration)
- [Examples](#examples)
- [Best Practices](#best-practices)

---

## 🚀 Quick Start

### Installation

```bash
./gradlew :ArgusOmni-CLI:build
```

### Your First Test

```yaml
# hello-world.yml
tests:
  - name: "Hello World API Test"
    type: REST
    rest:
      url: "https://httpbin.org/get"
      method: GET
    expect:
      status: 200
```

### Run Test

```bash
java -jar ArgusOmni-CLI/build/libs/ArgusOmni-CLI-1.0.0.jar run hello-world.yml
```

---

## 🎯 Core Concepts

### Test Suite Structure

```yaml
# Environment variables
env:
  baseUrl: "https://api.example.com"
  apiKey: "your-api-key"

# Global variables
variables:
  userId: 123
  userName: "john_doe"

# Execution configuration
execution:
  parallel:
    enabled: true
    threads: 4
    timeout: 30000
    failFast: false

# Test steps
tests:
  - name: "Test Step 1"
    type: REST
    # ... step configuration
```

### Variable System

Variables can be:
- Defined in `env` or `variables` sections
- Extracted from responses
- Set dynamically during execution
- Referenced using `{{variableName}}` syntax

```yaml
env:
  baseUrl: "https://api.example.com"

tests:
  - name: "Get User"
    type: REST
    rest:
      url: "{{baseUrl}}/users/123"
      method: GET
    extract:
      userId: "$.id"
      userName: "$.name"

  - name: "Use Extracted Data"
    type: REST
    rest:
      url: "{{baseUrl}}/users/{{userId}}/posts"
      method: GET
```

---

## 📦 Step Types

### 1. REST - HTTP API Testing

**Use Case:** Testing RESTful APIs, webhooks, web services

**Basic Example:**
```yaml
- name: "GET Request"
  type: REST
  rest:
    url: "https://api.example.com/users"
    method: GET
    headers:
      Authorization: "Bearer {{token}}"
    queryParams:
      page: 1
      limit: 10
  expect:
    status: 200
    body:
      jsonPath:
        $.users:
          arrayNotEmpty: true
```

**POST with JSON Body:**
```yaml
- name: "Create User"
  type: REST
  rest:
    url: "{{baseUrl}}/users"
    method: POST
    headers:
      Content-Type: "application/json"
    body: |
      {
        "name": "John Doe",
        "email": "john@example.com",
        "age": 30
      }
  extract:
    newUserId: "$.id"
  expect:
    status: 201
```

**File Upload (Multipart):**
```yaml
- name: "Upload File"
  type: REST
  rest:
    url: "{{baseUrl}}/upload"
    method: POST
    multipart:
      file:
        path: "/path/to/file.pdf"
        filename: "document.pdf"
        contentType: "application/pdf"

      # Multiple files as array
      photos:
        - path: "/path/photo1.jpg"
        - path: "/path/photo2.jpg"

      # Form fields
      description: "My documents"
      category: "personal"
```

**Array Upload Formats:**
```yaml
# Format 1: Brackets (tags[]=value1&tags[]=value2)
multipart:
  tags:
    - "tag1"
    - "tag2"
  arrayFormat: "brackets"

# Format 2: Indexed (tags[0]=value1&tags[1]=value2)
multipart:
  tags:
    - "tag1"
    - "tag2"
  arrayFormat: "indexed"

# Format 3: Same name (tags=value1&tags=value2)
multipart:
  tags:
    - "tag1"
    - "tag2"
  arrayFormat: "same"
```

---

### 2. GRPC - gRPC Service Testing

**Use Case:** Testing gRPC microservices

```yaml
- name: "Call gRPC Service"
  type: GRPC
  grpc:
    host: "localhost"
    port: 50051
    service: "UserService"
    method: "GetUser"
    protoPath: "protos/user.proto"
    request:
      userId: 123
  expect:
    json:
      name: "John Doe"
```

---

### 3. FS - File System Operations

**Use Case:** File/directory validation, file content testing

```yaml
- name: "Check File Exists"
  type: FS
  fs:
    operation: "read"
    path: "/path/to/file.txt"
  expect:
    fsExists: "/path/to/file.txt"
    fsSize: "/path/to/file.txt:1024"
```

---

### 4. BASH - Execute Shell Commands

**Use Case:** System operations, CLI testing, integration with external tools

```yaml
- name: "Run Shell Command"
  type: BASH
  bash:
    command: "ls -la /tmp"
  extract:
    output: "$"
```

**Complex Command:**
```yaml
- name: "Database Backup"
  type: BASH
  bash:
    command: |
      pg_dump -h localhost -U postgres mydb > /tmp/backup.sql
      echo "Backup completed"
```

---

### 5. SET - Set Variables

**Use Case:** Data preparation, test state management

```yaml
- name: "Setup Test Data"
  type: SET
  set:
    variables:
      testUser: "john_doe"
      testEmail: "john@example.com"
      testAge: 25
      testRoles: ["user", "admin"]
      testConfig:
        enabled: true
        maxRetries: 3
```

---

### 6. TRANSFORM - Data Transformation

**Use Case:** Data manipulation, format conversion

```yaml
- name: "Transform Data"
  type: TRANSFORM
  transform:
    operation: "jsonPath"
    input: "{{responseData}}"
    query: "$.users[?(@.age > 18)]"
    outputVariable: "adultUsers"
```

---

### 7. ASSERT - Validation

**Use Case:** Intermediate validation, custom assertions

```yaml
- name: "Verify Conditions"
  type: ASSERT
  expect:
    equals:
      status: "active"
      count: 5
```

---

### 8. WAIT - Delays and Polling

**Use Case:** Waiting for async operations, polling for state changes

**Simple Delay:**
```yaml
- name: "Wait 5 Seconds"
  type: WAIT
  wait:
    duration: 5000  # milliseconds
```

**Conditional Polling:**
```yaml
- name: "Wait for Job Completion"
  type: WAIT
  wait:
    condition:
      variable: "jobStatus"
      equals: "completed"
    maxRetries: 10
    retryInterval: 2000
    timeout: 30000
```

**Condition Types:**
- `equals` - Variable equals value
- `exists` - Variable exists
- `expression` - Simple expression (e.g., "count > 5")
- `jsonPath` - JSONPath query on variable

---

### 9. LOOP - Data-Driven Testing

**Use Case:** Running same test with different data, batch operations

**Loop Over Array:**
```yaml
- name: "Test Multiple Users"
  type: LOOP
  loop:
    items:
      - {name: "Alice", age: 25}
      - {name: "Bob", age: 30}
      - {name: "Charlie", age: 35}
    variable: "user"
    indexVariable: "index"
    steps:
      - name: "Create User {{user.name}}"
        type: REST
        rest:
          url: "{{baseUrl}}/users"
          method: POST
          body: |
            {
              "name": "{{user.name}}",
              "age": {{user.age}}
            }
```

**Loop Over CSV File:**
```yaml
- name: "Process CSV Data"
  type: LOOP
  loop:
    dataSource:
      type: "CSV"
      file: "test-data/users.csv"
      headers: true
    variable: "row"
    steps:
      - name: "Create User from CSV"
        type: REST
        rest:
          url: "{{baseUrl}}/users"
          method: POST
          body: |
            {
              "name": "{{row.name}}",
              "email": "{{row.email}}"
            }
```

**Loop Over JSON File:**
```yaml
- name: "Process JSON Data"
  type: LOOP
  loop:
    dataSource:
      type: "JSON"
      file: "test-data/products.json"
      path: "$.products"
    variable: "product"
    steps:
      - name: "Validate Product {{product.id}}"
        type: ASSERT
        expect:
          equals:
            productId: "{{product.id}}"
```

**Loop Over Range:**
```yaml
- name: "Create 10 Records"
  type: LOOP
  loop:
    range:
      start: 1
      end: 10
      step: 1
    variable: "i"
    steps:
      - name: "Create Record {{i}}"
        type: REST
        rest:
          url: "{{baseUrl}}/records"
          method: POST
          body: |
            {"index": {{i}}}
```

**Loop Over Variable:**
```yaml
- name: "Process Existing Users"
  type: LOOP
  loop:
    itemsFrom: "userList"
    variable: "user"
    continueOnError: true
    steps:
      - name: "Update User {{user.id}}"
        type: REST
        rest:
          url: "{{baseUrl}}/users/{{user.id}}"
          method: PATCH
```

---

### 10. IF - Conditional Execution

**Use Case:** Branching logic, environment-specific tests, error handling

**Simple IF:**
```yaml
- name: "Conditional Test"
  type: IF
  ifConfig:
    condition: "environment == 'production'"
    then:
      - name: "Production-Only Check"
        type: REST
        rest:
          url: "{{baseUrl}}/health"
          method: GET
```

**IF-ELSE:**
```yaml
- name: "Environment-Specific Setup"
  type: IF
  ifConfig:
    condition: "environment == 'dev'"
    then:
      - name: "Use Dev Database"
        type: SET
        set:
          variables:
            dbUrl: "dev.database.com"
    elseSteps:
      - name: "Use Prod Database"
        type: SET
        set:
          variables:
            dbUrl: "prod.database.com"
```

**IF-ELSEIF-ELSE:**
```yaml
- name: "Multi-Tier Logic"
  type: IF
  ifConfig:
    condition: "statusCode == 200"
    then:
      - name: "Success Path"
        type: SET
        set:
          variables:
            result: "success"

    elseIf:
      - condition: "statusCode == 404"
        then:
          - name: "Not Found Path"
            type: SET
            set:
              variables:
                result: "not_found"

      - condition: "statusCode >= 500"
        then:
          - name: "Server Error Path"
            type: SET
            set:
              variables:
                result: "server_error"

    elseSteps:
      - name: "Default Path"
        type: SET
        set:
          variables:
            result: "unknown"
```

**Supported Condition Operators:**
- `==`, `!=` - Equality
- `>`, `<`, `>=`, `<=` - Comparison
- `AND`, `OR`, `NOT` - Logical operators
- `contains`, `startsWith`, `endsWith` - String operations
- `exists`, `null` - Existence checks

**Condition Examples:**
```yaml
# Equality
condition: "status == 'active'"

# Comparison
condition: "age > 18"

# Logical AND
condition: "age > 18 AND status == 'active'"

# Logical OR
condition: "role == 'admin' OR role == 'superuser'"

# String contains
condition: "email contains '@gmail.com'"

# Existence
condition: "userId exists"
condition: "deletedAt == null"

# Complex
condition: "(age > 18 AND status == 'active') OR role == 'admin'"
```

---

### 11. MOCK - Mock Server Integration

**Use Case:** Testing your application's behavior with different API responses, offline testing, error scenario simulation

**Start Mock Server:**
```yaml
- name: "Start Mock API"
  type: MOCK
  mock:
    action: start
    port: 8089
    baseUrlVariable: "mockUrl"
    portVariable: "mockPort"
```

**Create Stub (Fake Endpoint):**
```yaml
- name: "Mock User API"
  type: MOCK
  mock:
    action: stub
    stub:
      request:
        method: GET
        urlPath: /api/users/123
      response:
        status: 200
        jsonBody:
          id: 123
          name: "John Doe"
          email: "john@example.com"
```

**Mock Error Response:**
```yaml
- name: "Mock 500 Error"
  type: MOCK
  mock:
    action: stub
    stub:
      request:
        method: POST
        urlPath: /api/payment
      response:
        status: 500
        jsonBody:
          error: "Payment gateway unavailable"
        fixedDelayMilliseconds: 2000  # Simulate slow response
```

**Verify Requests:**
```yaml
- name: "Verify Payment Called"
  type: MOCK
  mock:
    action: verify
    verify:
      request:
        method: POST
        urlPath: /api/payment
      count: 1
      countExpression: exactly  # or atLeast, atMost
```

**Reset Mock Server:**
```yaml
- name: "Clear All Stubs"
  type: MOCK
  mock:
    action: reset
```

**Stop Mock Server:**
```yaml
- name: "Stop Mock Server"
  type: MOCK
  mock:
    action: stop
```

**Real-World Example - Testing Your App:**
```yaml
tests:
  # Start mock payment gateway
  - name: "Start Mock Payment Gateway"
    type: MOCK
    mock:
      action: start
      port: 8090
      baseUrlVariable: "paymentGatewayUrl"

  # Mock successful payment
  - name: "Mock Success Payment"
    type: MOCK
    mock:
      action: stub
      stub:
        request:
          method: POST
          urlPath: /payment/process
        response:
          status: 200
          jsonBody:
            transactionId: "txn_123"
            status: "approved"

  # Test YOUR application (it will call the mock)
  - name: "Test App Payment Flow"
    type: REST
    rest:
      url: "http://localhost:3000/checkout"  # YOUR APP
      method: POST
      body: |
        {
          "amount": 99.99,
          "paymentGatewayUrl": "{{paymentGatewayUrl}}"
        }
    expect:
      status: 200

  # Verify your app called the payment gateway
  - name: "Verify Payment Gateway Called"
    type: MOCK
    mock:
      action: verify
      verify:
        request:
          method: POST
          urlPath: /payment/process
        count: 1
        countExpression: exactly
```

---

## 🔍 Assertions

ArgusOmni-CLI provides comprehensive assertion capabilities for validating responses, data, and system state.

### Status Code Assertions

```yaml
expect:
  status: 200
```

### JSON Assertions

**Exact Match:**
```yaml
expect:
  json:
    userId: 123
    name: "John Doe"
```

**JSON Contains:**
```yaml
expect:
  jsonContains:
    email: true
    profile: true
```

**JSON Not Contains:**
```yaml
expect:
  jsonNotContains:
    password: true
    creditCard: true
```

### JSONPath Assertions

JSONPath provides powerful query capabilities for complex JSON validation.

**Basic JSONPath:**
```yaml
expect:
  body:
    jsonPath:
      $.userId:
        equals: 123

      $.email:
        exists: true
```

**Type Validation:**
```yaml
expect:
  body:
    jsonPath:
      $.id:
        type: integer

      $.name:
        type: string

      $.verified:
        type: boolean

      $.tags:
        type: array

      $.metadata:
        type: object
```

**Numeric Comparisons:**
```yaml
expect:
  body:
    jsonPath:
      $.age:
        greaterThan: 18
        lessThan: 65

      $.price:
        greaterThanOrEqual: 10
        lessThanOrEqual: 1000

      $.score:
        between:
          min: 0
          max: 100
```

**String Operations:**
```yaml
expect:
  body:
    jsonPath:
      $.email:
        matches: "^[\\w.]+@[\\w.]+\\.[a-z]{2,}$"  # Regex
        contains: "@gmail.com"
        startsWith: "user"
        endsWith: ".com"
        minLength: 5
        maxLength: 100
```

**Array Operations:**
```yaml
expect:
  body:
    jsonPath:
      $.users:
        arrayNotEmpty: true
        arraySize: 10
        arrayMinSize: 1
        arrayMaxSize: 100
        arrayContains: "admin"

      $.scores:
        arrayAll:
          operator: greaterThan
          value: 0
```

**Count Assertions:**
```yaml
expect:
  body:
    jsonPath:
      $.users:
        count: 10          # Exact count
        minCount: 5        # At least 5
        maxCount: 20       # At most 20
```

**Existence Checks:**
```yaml
expect:
  body:
    jsonPath:
      $.userId:
        exists: true
        notNull: true

      $.deletedAt:
        isNull: true

      $.optionalField:
        isEmpty: false
```

**Not Equals:**
```yaml
expect:
  body:
    jsonPath:
      $.status:
        notEquals: "deleted"

      $.tags:
        notContains: "spam"
```

**Multiple Conditions (allOf/anyOf):**
```yaml
expect:
  body:
    jsonPath:
      # AND - All must pass
      $.age:
        allOf:
          - greaterThan: 18
          - lessThan: 65
          - notEquals: 25

      # OR - At least one must pass
      $.status:
        anyOf:
          - equals: "active"
          - equals: "pending"
          - equals: "processing"
```

### Performance Assertions

```yaml
expect:
  performance:
    maxDuration: 1000      # Max 1 second
    minDuration: 100       # At least 100ms (detect caching)
```

### JSON Schema Validation

**External Schema File:**
```yaml
expect:
  jsonSchema: "schemas/user-schema.json"
```

**Schema File Example (user-schema.json):**
```json
{
  "type": "object",
  "required": ["id", "name", "email"],
  "properties": {
    "id": {
      "type": "integer",
      "minimum": 1
    },
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 100
    },
    "email": {
      "type": "string",
      "format": "email"
    },
    "age": {
      "type": "integer",
      "minimum": 0,
      "maximum": 150
    }
  }
}
```

### Date Format Assertions

**Simple Pattern:**
```yaml
expect:
  dateFormats:
    createdAt: "yyyy-MM-dd'T'HH:mm:ss"
    birthDate: "dd-MM-yyyy"
```

**With Locale and Range:**
```yaml
expect:
  dateFormats:
    eventDate:
      pattern: "dd MMMM yyyy"
      locale: "en-US"
      min: "01 January 2020"
      max: "31 December 2025"
```

### Header Assertions

**Note:** Header assertions model is ready but requires response headers to be added to ExecutionResult.

```yaml
expect:
  headers:
    - name: "Content-Type"
      contains: "application/json"
      startsWith: "application"
      exists: true

    - name: "X-Rate-Limit-Remaining"
      greaterThan: 0
      exists: true

    - name: "X-Deprecated"
      notExists: true
```

### File System Assertions

```yaml
expect:
  fsExists: "/path/to/file.txt"
  fsSize: "/path/to/file.txt:1024"
```

### Variable Assertions

```yaml
expect:
  equals:
    userName: "john_doe"
    userAge: 25
    userActive: true
```

---

## ⚡ Advanced Features

### 1. Parallel Execution

**Use Case:** Speed up test execution, concurrent load testing

```yaml
execution:
  parallel:
    enabled: true
    threads: 4           # Number of concurrent threads
    timeout: 30000       # Timeout per test (ms)
    failFast: false      # Stop on first failure

tests:
  # Level 0: No dependencies - run in parallel
  - id: "test1"
    name: "Independent Test 1"
    type: REST
    rest:
      url: "{{baseUrl}}/endpoint1"
      method: GET

  - id: "test2"
    name: "Independent Test 2"
    type: REST
    rest:
      url: "{{baseUrl}}/endpoint2"
      method: GET

  # Level 1: Depends on test1
  - id: "test3"
    name: "Dependent Test"
    dependsOn: ["test1"]
    type: REST
    rest:
      url: "{{baseUrl}}/endpoint3"
      method: GET
```

**Dependency Graph:**
- Tests with no `dependsOn` run first (Level 0)
- Tests run only after all dependencies complete
- Tests at same level run in parallel
- Automatic topological sort and cycle detection

---

### 2. Retry Logic

**Use Case:** Handling flaky tests, eventual consistency, polling operations

**Step-Level Retry:**
```yaml
- name: "Flaky API Call"
  type: REST
  maxRetries: 3
  retryInterval: 1000
  rest:
    url: "{{baseUrl}}/flaky-endpoint"
    method: GET
  expect:
    status: 200
```

---

### 3. Continue on Error

**Use Case:** Collecting all failures, non-critical validations

```yaml
- name: "Optional Validation"
  type: ASSERT
  continueOnError: true
  expect:
    equals:
      optionalField: "value"
```

---

### 4. Data Extraction

**Use Case:** Chaining requests, passing data between steps

```yaml
- name: "Create User"
  type: REST
  rest:
    url: "{{baseUrl}}/users"
    method: POST
    body: |
      {"name": "John Doe"}
  extract:
    userId: "$.id"
    userName: "$.name"
    userEmail: "$.email"

- name: "Get User Details"
  type: REST
  rest:
    url: "{{baseUrl}}/users/{{userId}}"
    method: GET
```

---

## 📚 Examples

### Complete REST API Test Suite

```yaml
env:
  baseUrl: "https://api.example.com"
  apiKey: "your-api-key"

tests:
  # Authentication
  - name: "Login"
    type: REST
    rest:
      url: "{{baseUrl}}/auth/login"
      method: POST
      headers:
        Content-Type: "application/json"
      body: |
        {
          "username": "testuser",
          "password": "testpass"
        }
    extract:
      authToken: "$.token"
    expect:
      status: 200
      body:
        jsonPath:
          $.token:
            type: string
            minLength: 10

  # Create Resource
  - name: "Create User"
    type: REST
    rest:
      url: "{{baseUrl}}/users"
      method: POST
      headers:
        Authorization: "Bearer {{authToken}}"
        Content-Type: "application/json"
      body: |
        {
          "name": "John Doe",
          "email": "john@example.com",
          "age": 30
        }
    extract:
      userId: "$.id"
    expect:
      status: 201
      performance:
        maxDuration: 2000
      body:
        jsonPath:
          $.id:
            type: integer
            greaterThan: 0
          $.email:
            matches: "^[\\w.]+@[\\w.]+\\.[a-z]{2,}$"

  # Get Resource
  - name: "Get User"
    type: REST
    rest:
      url: "{{baseUrl}}/users/{{userId}}"
      method: GET
      headers:
        Authorization: "Bearer {{authToken}}"
    expect:
      status: 200
      body:
        jsonPath:
          $.name:
            equals: "John Doe"
          $.age:
            between:
              min: 18
              max: 100

  # Update Resource
  - name: "Update User"
    type: REST
    rest:
      url: "{{baseUrl}}/users/{{userId}}"
      method: PATCH
      headers:
        Authorization: "Bearer {{authToken}}"
        Content-Type: "application/json"
      body: |
        {
          "age": 31
        }
    expect:
      status: 200

  # Delete Resource
  - name: "Delete User"
    type: REST
    rest:
      url: "{{baseUrl}}/users/{{userId}}"
      method: DELETE
      headers:
        Authorization: "Bearer {{authToken}}"
    expect:
      status: 204
```

### Data-Driven Testing Example

```yaml
env:
  baseUrl: "https://api.example.com"

tests:
  - name: "Test Multiple Users"
    type: LOOP
    loop:
      dataSource:
        type: CSV
        file: "test-data/users.csv"
        headers: true
      variable: "user"
      steps:
        - name: "Create User {{user.name}}"
          type: REST
          rest:
            url: "{{baseUrl}}/users"
            method: POST
            body: |
              {
                "name": "{{user.name}}",
                "email": "{{user.email}}",
                "age": {{user.age}}
              }
          expect:
            status: 201
```

### Mock Server Testing Example

```yaml
tests:
  # Start mock payment gateway
  - name: "Start Payment Mock"
    type: MOCK
    mock:
      action: start
      port: 8090
      baseUrlVariable: "paymentUrl"

  # Mock successful payment
  - name: "Mock Success"
    type: MOCK
    mock:
      action: stub
      stub:
        request:
          method: POST
          urlPath: /payment
        response:
          status: 200
          jsonBody:
            transactionId: "txn_123"
            status: "approved"

  # Test app (calls mock)
  - name: "Test Checkout"
    type: REST
    rest:
      url: "http://localhost:3000/checkout"
      method: POST
      body: |
        {
          "amount": 99.99,
          "gateway": "{{paymentUrl}}"
        }
    expect:
      status: 200

  # Verify call was made
  - name: "Verify Payment Called"
    type: MOCK
    mock:
      action: verify
      verify:
        request:
          method: POST
          urlPath: /payment
        count: 1
        countExpression: exactly

  - name: "Stop Mock"
    type: MOCK
    mock:
      action: stop
```

---

## 🎯 Best Practices

### 1. Use Descriptive Names
```yaml
# Good
- name: "Create user and verify email format"

# Bad
- name: "Test 1"
```

### 2. Organize with Variables
```yaml
env:
  baseUrl: "https://api.example.com"
  apiVersion: "v1"
  timeout: 5000

tests:
  - name: "API Call"
    type: REST
    rest:
      url: "{{baseUrl}}/{{apiVersion}}/users"
```

### 3. Extract Reusable Data
```yaml
- name: "Login Once"
  type: REST
  rest:
    url: "{{baseUrl}}/auth/login"
    method: POST
  extract:
    token: "$.token"

# Reuse token in all subsequent requests
- name: "Protected Endpoint"
  type: REST
  rest:
    headers:
      Authorization: "Bearer {{token}}"
```

### 4. Use Parallel Execution for Independent Tests
```yaml
execution:
  parallel:
    enabled: true
    threads: 4

tests:
  - id: "health"
    name: "Health Check"
    type: REST
    # No dependencies - runs first

  - id: "users"
    name: "Get Users"
    dependsOn: ["health"]
    # Runs after health check
```

### 5. Implement Comprehensive Assertions
```yaml
expect:
  status: 200
  performance:
    maxDuration: 1000
  body:
    jsonPath:
      $.id:
        type: integer
        greaterThan: 0
      $.email:
        matches: "^[\\w.]+@[\\w.]+$"
      $.age:
        between:
          min: 18
          max: 150
```

### 6. Use Mock Servers for Offline Testing
```yaml
# Instead of hitting real payment gateway in tests
- name: "Start Mock Gateway"
  type: MOCK
  mock:
    action: start

# Configure different scenarios
- name: "Mock Failure"
  type: MOCK
  mock:
    action: stub
    stub:
      response:
        status: 500
```

### 7. Handle Errors Gracefully
```yaml
- name: "Optional Check"
  continueOnError: true
  type: ASSERT

- name: "Critical Check"
  type: REST
  maxRetries: 3
  retryInterval: 1000
```

---

## 📖 Additional Resources

- **Test Examples:** See `test-examples/` directory for comprehensive examples
- **Issue Tracking:** [GitHub Issues](https://github.com/your-repo/issues)
- **Contributing:** See CONTRIBUTING.md

---

## 🏆 Feature Summary

| Category | Features |
|----------|----------|
| **Step Types** | REST, gRPC, FS, BASH, SET, TRANSFORM, ASSERT, WAIT, LOOP, IF, MOCK |
| **Assertions** | JSONPath, JSON Schema, Type Validation, Numeric Comparisons, String Operations, Array Operations, Performance |
| **Control Flow** | IF/ELSE/ELSEIF, Loops (CSV, JSON, Array, Range), Parallel Execution, Dependencies |
| **Data** | Variables, Extraction, Transformation, CSV/JSON Loading |
| **Advanced** | Retry Logic, Mock Servers, File Upload, Multipart, Performance Metrics |

---

## 📝 License

[Your License Here]

---

**Built with ❤️ for Quality Assurance Engineers**
