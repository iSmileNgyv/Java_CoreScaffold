# ArgusOmni - Universal Test Orchestrator

## Overview

ArgusOmni is a declarative, command-line based test orchestrator designed to validate hybrid microservice systems. It supports REST, HTTP/2.0, gRPC, and File System validation within a single YAML file.

## Architecture Principles

This project strictly follows **SOLID** principles and **OOP** fundamentals:

### SOLID Principles

1. **Single Responsibility Principle (SRP)**
   - Each class has one clear responsibility
   - `TestParser` only parses, `TestRunner` only orchestrates, etc.

2. **Open/Closed Principle (OCP)**
   - System is open for extension, closed for modification
   - New executors can be added without changing existing code
   - Just implement `TestExecutor` interface

3. **Liskov Substitution Principle (LSP)**
   - All executors can be used interchangeably through `TestExecutor` interface
   - Polymorphic behavior is guaranteed

4. **Interface Segregation Principle (ISP)**
   - Small, focused interfaces: `TestExecutor`, `TestParser`, `Asserter`, etc.
   - No fat interfaces that force unnecessary dependencies

5. **Dependency Inversion Principle (DIP)**
   - High-level modules depend on abstractions, not implementations
   - `TestRunner` depends on `TestExecutor` interface, not concrete executors

### OOP Principles

1. **Encapsulation**
   - Private fields with controlled access
   - `VariableContext`, `ExecutionContext` encapsulate state

2. **Inheritance**
   - `AbstractExecutor` provides common behavior
   - Concrete executors extend base functionality

3. **Polymorphism**
   - Runtime executor selection based on step type
   - Strategy pattern for executor dispatch

4. **Abstraction**
   - Interfaces hide implementation details
   - Clean separation between contract and implementation

## Features

- ✅ REST/HTTP testing with HTTP/2 support
- ✅ Dynamic gRPC testing (no code generation)
- ✅ File system validation
- ✅ Variable resolution and transformation
- ✅ JSONPath-based extraction
- ✅ Colored console output
- ✅ CI/CD ready (deterministic exit codes)

## Usage

```bash
# Run a test suite
argus run test.yml

# Verbose mode
argus run test.yml --verbose

# Override environment
argus run test.yml --env=prod
```

## Exit Codes

- `0` - All tests passed
- `1` - Test failure
- `2` - Invalid YAML or setup error

## Extending the System

### Adding a New Executor

1. Create a new executor class implementing `TestExecutor`:

```java
@Component
public class MyCustomExecutor extends AbstractExecutor {
    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.MY_CUSTOM;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) {
        // Implementation
    }
}
```

2. Spring will automatically register it - no other changes needed!

### Adding a New Built-in Function

Add to `BuiltInFunctions.java`:

```java
case "my_function" -> myFunction(argument);
```

## Project Structure

```
src/main/java/com/ismile/argusomni/
├── model/          # Data models (Encapsulation)
├── executor/       # Executor implementations (OCP, Polymorphism)
├── parser/         # Test parsers (SRP)
├── variable/       # Variable resolution (SRP)
├── assertion/      # Assertion engine (SRP)
├── extractor/      # Response extractors (SRP)
├── runner/         # Test orchestration (DIP)
├── report/         # Reporting (SRP)
├── grpc/           # gRPC dynamic client (SRP)
└── cli/            # CLI commands
```

## License

MIT
