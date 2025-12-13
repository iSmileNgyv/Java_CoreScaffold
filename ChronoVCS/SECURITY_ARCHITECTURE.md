# ChronoVCS Security Architecture

## Multi-Layered Defense Strategy

ChronoVCS implements a **Defense in Depth** security strategy with multiple protection layers working together.

```
┌─────────────────────────────────────────────────────────────┐
│                    LAYER 1: CLOUDFLARE WAF                  │
│  • DNS Protection                                           │
│  • DDoS Mitigation                                          │
│  • Bot Detection                                            │
│  • Edge Firewall Rules                                      │
│  • SSL/TLS Encryption                                       │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              LAYER 2: APPLICATION FILTERS                   │
│  • Security Headers Filter (HIGHEST_PRECEDENCE)             │
│  • Request Sanitization Filter (HIGHEST_PRECEDENCE + 1)     │
│  • Rate Limit Interceptor                                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│            LAYER 3: SPRING SECURITY                         │
│  • JWT Authentication                                       │
│  • CORS Configuration                                       │
│  • CSRF Protection                                          │
│  • Role-Based Access Control                                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              LAYER 4: BUSINESS LOGIC                        │
│  • Input Validation (@Valid, Bean Validation)               │
│  • Permission Checks                                        │
│  • API Abuse Detection                                      │
│  • Encryption Service (AES-256-GCM)                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│               LAYER 5: DATA LAYER                           │
│  • Parametrized Queries (JPA/Hibernate)                     │
│  • Encrypted Storage (tokens, credentials)                  │
│  • Audit Logging                                            │
└─────────────────────────────────────────────────────────────┘
```

---

## Layer 1: Cloudflare WAF (Edge Protection)

**Cloudflare handles:**
- DDoS attacks and traffic spikes
- Known malicious IPs and bot networks
- Common OWASP attacks at edge
- SSL/TLS termination
- Geographic filtering
- Challenge pages for suspicious traffic

**Setup:**
1. Point DNS to Cloudflare
2. Enable "Under Attack" mode if needed
3. Configure firewall rules
4. Enable Bot Fight Mode
5. Set security level to "Medium" or "High"

**Recommended Rules:**
```
# Block common attack patterns
(http.request.uri.path contains "../" or
 http.request.uri.path contains "union select" or
 http.request.uri.path contains "<script")

# Rate limit per IP
(rate_limit: 100 requests per 10 seconds per IP)

# Block specific user agents
(http.user_agent contains "sqlmap" or
 http.user_agent contains "nikto")
```

---

## Layer 2: Application Filters

### 2.1 Security Headers Filter
**File:** `SecurityHeadersFilter.java`
**Priority:** HIGHEST_PRECEDENCE
**URL Pattern:** `/*`

**Headers Applied:**
```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), microphone=(), camera=()
Cache-Control: no-cache, no-store, max-age=0, must-revalidate (for sensitive endpoints)
```

**Protection Against:**
- Clickjacking (X-Frame-Options)
- MIME type sniffing attacks (X-Content-Type-Options)
- XSS attacks (CSP, X-XSS-Protection)
- Privacy leaks (Referrer-Policy)
- Unnecessary browser features (Permissions-Policy)

---

### 2.2 Request Sanitization Filter
**File:** `RequestSanitizationFilter.java`
**Priority:** HIGHEST_PRECEDENCE + 1
**URL Pattern:** `/api/*`

**Attack Patterns Detected:**

#### SQL Injection (9 patterns)
```regex
(?i).*(union.*select).*
(?i).*(or\s+['"]?1['"]?\s*=\s*['"]?1).*
(?i).*(drop\s+(table|database)).*
(?i).*(insert\s+into).*
(?i).*(delete\s+from).*
(?i).*(update\s+\w+\s+set).*
(?i).*(exec\s*\().*
(?i).*(;\s*(drop|delete|update|insert)).*
(?i).*(--|#|/\*).*  # SQL comments
```

#### XSS (8 patterns)
```regex
(?i).*<script.*
(?i).*javascript:.*
(?i).*on(load|error|click|mouse|focus)\s*=.*
(?i).*<iframe.*
(?i).*<object.*
(?i).*<embed.*
(?i).*eval\s*\(.*
(?i).*expression\s*\(.*
```

#### Path Traversal (4 patterns)
```regex
.*\.\.[\\/].*
.*/etc/passwd.*
.*/windows/system32.*
.*(\.\.%2[fF]|%2[eE]%2[eE]%2[fF]).*  # URL encoded
```

#### Command Injection (4 patterns)
```regex
.*[;&|`$].*  # Shell metacharacters
(?i).*(cat|ls|wget|curl|nc|bash|sh|cmd|powershell)\s.*
.*\$\(.*\).*  # Command substitution
.*`.*`.*      # Backtick command
```

#### NoSQL Injection (4 patterns)
```regex
(?i).*\$where.*
(?i).*\$ne.*
(?i).*\$gt.*
(?i).*\$regex.*
```

**Validation Points:**
- URI path
- Query parameters
- HTTP headers (User-Agent, etc.)

**Response on Attack:**
- HTTP 403 Forbidden
- Error code: `SECURITY_VIOLATION`
- Audit log: `CRITICAL` severity
- Message: "Request blocked by security policy"

---

### 2.3 Rate Limit Interceptor
**File:** `RateLimitInterceptor.java`
**Service:** `RateLimitService.java`
**Algorithm:** Token Bucket (Bucket4j)

**Limits by Endpoint:**
| Endpoint | Limit | Window | Bucket Capacity |
|----------|-------|--------|-----------------|
| `/api/auth/login` | 5 requests | 1 minute | 5 tokens |
| `/api/auth/register` | 3 requests | 1 hour | 3 tokens |
| `/api/auth/refresh` | 10 requests | 1 minute | 10 tokens |

**Tracking:** Per IP address
**Response on Limit Exceeded:**
- HTTP 429 Too Many Requests
- Error code: `RATE_LIMIT_EXCEEDED`
- Audit log: `WARN` severity
- Header: `Retry-After: 60` (seconds)

**Bucket Refill Strategy:**
```java
Refill.intervally(capacity, Duration.of(window))
```

---

### 2.4 API Abuse Detector
**File:** `ApiAbuseDetector.java`
**Type:** Service (tracks patterns)

**Detection Mechanisms:**

#### 1. Suspicious Request Patterns
```java
// More than 500 requests in 1 minute
if (duration < 60 && requestCount > 500) → SUSPICIOUS

// Same endpoint called >100 times in 1 minute
if (endpointCount > 100 && duration < 60) → SUSPICIOUS
```

#### 2. Data Scraping Detection
```java
// More than 1000 requests in time window
if (requestCount > 1000) → SCRAPING_DETECTED
```

#### 3. Excessive Failures
```java
// 20 failed operations (login, permission, etc.)
if (failureCount >= 20) → BLOCK_IDENTIFIER
```

**Tracking:**
- Request patterns per IP (in-memory ConcurrentHashMap)
- Failure trackers per user/IP
- Automatic cleanup every 10 minutes via @Scheduled

**Actions:**
- Log warning to audit system
- Return blocking signal for excessive failures
- Severity: WARN (suspicious), CRITICAL (threshold exceeded)

---

## Layer 3: Spring Security

### 3.1 JWT Authentication
**File:** `SecurityConfig.java`, `JwtTokenProvider.java`

**Token Structure:**
```json
{
  "sub": "user@example.com",
  "userId": "123",
  "roles": ["ROLE_USER"],
  "iat": 1234567890,
  "exp": 1234571490
}
```

**Token Expiry:**
- Access Token: 1 hour
- Refresh Token: 7 days

**Security:**
- HS512 signing algorithm
- Secret key from environment variable
- Token stored in Authorization header: `Bearer <token>`

---

### 3.2 CORS Configuration
**File:** `CorsConfig.java`

**Configuration:**
```yaml
chronovcs:
  cors:
    allowed-origins: ${CHRONOVCS_CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:8080}
    allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
    allow-credentials: true
    max-age: 3600
```

**Environment Variable:**
```bash
export CHRONOVCS_CORS_ALLOWED_ORIGINS="https://app.yourdomain.com,https://admin.yourdomain.com"
```

**Production Setup:**
- Never use wildcard (`*`) in production
- Specify exact frontend domains
- Enable credentials for cookie-based auth
- Set appropriate max-age for preflight caching

---

## Layer 4: Business Logic Security

### 4.1 Input Validation
**Technology:** Jakarta Bean Validation
**Files:** All DTOs in `com.ismile.core.chronovcs.dto.*`

**Examples:**

#### LoginRequest.java
```java
@NotBlank(message = "Email is required")
@Email(message = "Email must be valid")
@Size(max = 255)
private String email;

@NotBlank(message = "Password is required")
@Size(min = 8, max = 100, message = "Password must be 8-100 characters")
private String password;
```

#### CreateRepositoryRequestDto.java
```java
@NotBlank(message = "Repository name is required")
@Size(min = 3, max = 100)
@Pattern(regexp = "^[a-zA-Z0-9_-]+$",
         message = "Only letters, numbers, underscores and hyphens allowed")
private String name;
```

**Validation Trigger:**
- `@Valid` annotation on controller methods
- Automatic validation before method execution
- Errors caught by `GlobalExceptionHandler`

**Error Response:**
```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "Email is required, Password must be 8-100 characters",
  "path": "/api/auth/login",
  "timestamp": "2025-12-12T10:30:00Z"
}
```

---

### 4.2 Permission Checks
**File:** `PermissionService.java`

**Checks:**
- Repository ownership
- User roles (ADMIN, USER)
- Task integration permissions
- Token access permissions

**Example:**
```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(String userId) { ... }

// Custom permission check
if (!permissionService.canAccessRepository(userId, repositoryKey)) {
    throw new ForbiddenException("No access to repository");
}
```

**Audit on Failure:**
```java
auditService.logPermissionDenied(userId, resource, action);
// → EventType: PERMISSION_DENIED, Severity: WARN
```

---

### 4.3 Encryption Service
**File:** `EncryptionService.java`
**Algorithm:** AES-256-GCM
**Purpose:** Encrypt sensitive data at rest

**Encrypted Data:**
- JIRA API tokens
- Task integration secrets
- OAuth tokens
- Any user credentials

**Encryption Process:**
```java
// 1. Generate random IV (12 bytes for GCM)
byte[] iv = new byte[12];
secureRandom.nextBytes(iv);

// 2. Initialize cipher with GCM mode
GCMParameterSpec spec = new GCMParameterSpec(128, iv);
cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

// 3. Encrypt plaintext
byte[] ciphertext = cipher.doFinal(plaintext.getBytes(UTF_8));

// 4. Combine IV + ciphertext and Base64 encode
byte[] combined = concat(iv, ciphertext);
return Base64.getEncoder().encodeToString(combined);
```

**Key Management:**
```bash
# Generate master key
openssl rand -base64 32

# Set environment variable
export CHRONOVCS_SECURITY_MASTER_KEY="<base64-key>"
```

**Auto-encryption Points:**
- `TaskIntegrationService.createIntegration()` - encrypts before save
- `JiraIntegrationClient.fetchTasks()` - decrypts before use
- Transparent to application logic

---

## Layer 5: Data Layer Security

### 5.1 SQL Injection Prevention
**Technology:** JPA/Hibernate with Parametrized Queries

**Safe Query Examples:**
```java
// ✅ SAFE: Using named parameters
@Query("SELECT r FROM RepositoryEntity r WHERE r.repositoryKey = :key")
Optional<RepositoryEntity> findByRepositoryKey(@Param("key") String key);

// ✅ SAFE: Using Spring Data method names
List<AuditLog> findByUserIdAndTimestampAfter(String userId, Instant timestamp);

// ❌ UNSAFE: Never do this
// @Query("SELECT u FROM User u WHERE u.email = '" + email + "'")
```

**Hibernate automatically:**
- Escapes special characters
- Uses prepared statements
- Prevents SQL injection at database driver level

---

### 5.2 Encrypted Storage
**Files:** `TaskIntegrationEntity.java`, `UserEntity.java`

**Encrypted Fields:**
```java
@Column(name = "jira_api_token_encrypted", columnDefinition = "TEXT")
private String jiraApiTokenEncrypted;  // AES-256-GCM encrypted

@Column(name = "password_hash")
private String passwordHash;  // BCrypt hashed (not encrypted)
```

**Storage:**
- Sensitive data never stored in plain text
- Encryption happens automatically via service layer
- Database stores Base64-encoded ciphertext

---

### 5.3 Audit Logging
**Files:** `AuditLog.java`, `AuditService.java`, `AuditLogRepository.java`

**Event Types:**
```java
public enum EventType {
    LOGIN, LOGOUT, LOGIN_FAILED,
    REGISTER, PASSWORD_RESET,
    REPOSITORY_CREATED, REPOSITORY_DELETED,
    PERMISSION_DENIED, RATE_LIMIT_EXCEEDED,
    SUSPICIOUS_ACTIVITY, SECURITY_EVENT,
    TASK_INTEGRATION_CREATED, TASK_FETCHED,
    TOKEN_CREATED, TOKEN_REVOKED
}
```

**Severity Levels:**
```java
public enum Severity {
    DEBUG, INFO, WARN, ERROR, CRITICAL
}
```

**Async Logging:**
```java
@Async
public void logSecurityEvent(EventType type, String desc, Severity severity, Map<String, Object> metadata) {
    AuditLog log = AuditLog.builder()
        .timestamp(Instant.now())
        .eventType(type)
        .severity(severity)
        .ipAddress(getCurrentIpAddress())
        .description(desc)
        .metadata(toJson(metadata))
        .build();
    save(log);
}
```

**Thread Pool:**
```java
// AsyncConfig.java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(2);
executor.setMaxPoolSize(5);
executor.setQueueCapacity(100);
executor.setThreadNamePrefix("async-audit-");
```

**Indexed Columns:**
- `timestamp` - for time-range queries
- `user_id` - for user activity tracking
- `event_type` - for filtering by event
- `severity` - for critical event monitoring

**Query Examples:**
```java
// Last 24 hours security events
List<AuditLog> findSecurityEvents(Instant since);

// Failed login attempts by IP
long countFailedLoginsByIp(String ip, Instant since);

// Recent logs for dashboard
List<AuditLog> findRecentLogs(Instant since);
```

---

## Security Monitoring

### Real-Time Alerts
Monitor these audit events in production:

**Critical Priority:**
- `SECURITY_EVENT` with `CRITICAL` severity
- Multiple `LOGIN_FAILED` from same IP (>10 in 1 hour)
- `PERMISSION_DENIED` spikes
- `RATE_LIMIT_EXCEEDED` from same IP repeatedly

**Warning Priority:**
- `SUSPICIOUS_ACTIVITY` events
- Failed operations exceeding threshold
- Scraping detection triggers

**Query for Monitoring:**
```sql
-- Critical events in last hour
SELECT * FROM audit_logs
WHERE severity = 'CRITICAL'
  AND timestamp > NOW() - INTERVAL '1 HOUR'
ORDER BY timestamp DESC;

-- Failed login attempts by IP
SELECT ip_address, COUNT(*) as failed_count
FROM audit_logs
WHERE event_type = 'LOGIN_FAILED'
  AND timestamp > NOW() - INTERVAL '1 HOUR'
GROUP BY ip_address
HAVING COUNT(*) >= 10;
```

---

## Production Deployment Checklist

### Environment Variables
```bash
# JWT Secret (min 32 chars)
export JWT_SECRET="<strong-random-secret>"

# Master Encryption Key (32 bytes Base64)
export CHRONOVCS_SECURITY_MASTER_KEY="<base64-key>"

# CORS Origins (comma-separated)
export CHRONOVCS_CORS_ALLOWED_ORIGINS="https://app.yourdomain.com"

# Database URL
export CHRONOVCS_DB_URL="jdbc:postgresql://localhost:5432/chronovcs"
export CHRONOVCS_DB_USERNAME="chronovcs_user"
export CHRONOVCS_DB_PASSWORD="<strong-password>"
```

### Cloudflare Setup
1. ✅ DNS pointed to Cloudflare
2. ✅ SSL/TLS mode: Full (strict)
3. ✅ Security level: Medium or High
4. ✅ Bot Fight Mode: Enabled
5. ✅ WAF rules configured
6. ✅ Rate limiting rules: 100 req/10s per IP
7. ✅ Challenge page for suspicious traffic

### Application Configuration
1. ✅ All security filters registered
2. ✅ Rate limiting enabled
3. ✅ Audit logging to database
4. ✅ CORS restricted to production domains
5. ✅ Encryption keys rotated
6. ✅ HTTPS enforced (redirect HTTP → HTTPS)
7. ✅ Security headers on all responses

### Database Security
1. ✅ SSL/TLS connection to database
2. ✅ Sensitive data encrypted at rest
3. ✅ Audit log retention policy (e.g., 90 days)
4. ✅ Database user with minimal privileges
5. ✅ Regular backups configured

### Monitoring
1. ✅ Set up alerts for CRITICAL audit events
2. ✅ Monitor failed login attempts
3. ✅ Track rate limit violations
4. ✅ Dashboard for security events
5. ✅ Log aggregation (ELK, CloudWatch, etc.)

---

## Attack Scenarios & Defenses

### Scenario 1: SQL Injection Attack
**Attack:** `GET /api/repositories?name=test' OR '1'='1`

**Defense Layers:**
1. **Cloudflare WAF:** May block at edge if pattern recognized
2. **RequestSanitizationFilter:** Detects `OR '1'='1'` pattern → 403
3. **Spring Security:** Request doesn't reach here
4. **Input Validation:** Not reached
5. **JPA Parametrized Query:** Would be safe even if reached

**Result:** Blocked at Layer 2, logged as CRITICAL

---

### Scenario 2: XSS Attack
**Attack:** `POST /api/repositories {"name": "<script>alert('XSS')</script>"}`

**Defense Layers:**
1. **Cloudflare WAF:** May block if script tag detected
2. **RequestSanitizationFilter:** Detects `<script` pattern → 403
3. **Input Validation:** `@Pattern` would reject invalid characters
4. **Output Encoding:** Spring MVC auto-encodes HTML

**Result:** Blocked at Layer 2 or 4

---

### Scenario 3: Brute Force Login
**Attack:** 100 login attempts in 1 minute

**Defense Layers:**
1. **Cloudflare:** Rate limiting at edge (100 req/10s)
2. **RateLimitInterceptor:** 5 login attempts/minute → 429
3. **AuditService:** Tracks failed logins by IP
4. **ApiAbuseDetector:** Flags excessive failures

**Result:**
- First 5 attempts allowed
- 6th attempt blocked with 429
- IP flagged after 20 failures
- Audit log with WARN/CRITICAL severity

---

### Scenario 4: Data Scraping Bot
**Attack:** Automated bot fetching 2000 repositories

**Defense Layers:**
1. **Cloudflare:** Bot detection, challenge page
2. **ApiAbuseDetector:** Detects >1000 requests → flags IP
3. **RateLimitInterceptor:** Slows down requests
4. **Audit Logging:** Tracks suspicious activity

**Result:**
- Bot challenged at Cloudflare
- If bypassed, flagged by ApiAbuseDetector
- Logged for manual review

---

### Scenario 5: Path Traversal
**Attack:** `GET /api/files?path=../../etc/passwd`

**Defense Layers:**
1. **RequestSanitizationFilter:** Detects `../` pattern → 403
2. **Input Validation:** `@Pattern` on path parameters
3. **Business Logic:** Path validation in service layer

**Result:** Blocked at Layer 2, logged as CRITICAL

---

## Performance Considerations

### Filter Order Impact
```
SecurityHeadersFilter (0ms)
  ↓
RequestSanitizationFilter (~1-2ms for regex matching)
  ↓
RateLimitInterceptor (~0.5ms for token bucket check)
  ↓
Spring Security (~2-5ms for JWT validation)
  ↓
Controller + Business Logic
```

**Total Overhead:** ~5-10ms per request

### Optimization Tips
1. **Regex Compilation:** Patterns compiled once as `static final`
2. **In-Memory Buckets:** Token buckets stored in-memory (fast)
3. **Async Audit Logging:** Non-blocking, doesn't slow requests
4. **Static Resource Bypass:** Filters skip `.css`, `.js`, `.png`, etc.
5. **Database Indexing:** Audit log queries optimized with indexes

### Scaling
- **Rate Limiting:** Use Redis for distributed rate limiting if multiple instances
- **Session Storage:** JWT is stateless, no session replication needed
- **Audit Logs:** Consider time-series database for high volume (e.g., TimescaleDB)
- **API Abuse Detection:** Consider Redis for distributed pattern tracking

---

## Security Best Practices

### DO
✅ Always use HTTPS in production
✅ Rotate encryption keys regularly (every 90 days)
✅ Monitor audit logs daily
✅ Keep dependencies updated (`./gradlew dependencyUpdates`)
✅ Use strong passwords (bcrypt with strength 12)
✅ Validate all user input
✅ Use parametrized queries
✅ Encrypt sensitive data at rest
✅ Rate limit authentication endpoints
✅ Log security events

### DON'T
❌ Store secrets in code or git
❌ Use weak encryption keys
❌ Ignore security warnings
❌ Disable CSRF protection
❌ Allow unlimited login attempts
❌ Return detailed error messages to users
❌ Use wildcard CORS in production
❌ Store passwords in plain text
❌ Skip input validation
❌ Ignore failed login alerts

---

## Security Incident Response

### If Attack Detected

1. **Immediate Actions:**
   - Check audit logs for attack details
   - Block attacker IP at Cloudflare
   - Review recent security events
   - Check for data exfiltration

2. **Investigation:**
   ```sql
   -- Find all events from attacker IP
   SELECT * FROM audit_logs
   WHERE ip_address = '<attacker-ip>'
   ORDER BY timestamp DESC;

   -- Check if any data was accessed
   SELECT * FROM audit_logs
   WHERE event_type IN ('REPOSITORY_CREATED', 'TASK_FETCHED')
     AND ip_address = '<attacker-ip>';
   ```

3. **Remediation:**
   - Rotate affected tokens/keys
   - Reset passwords if credentials compromised
   - Update firewall rules
   - Patch vulnerabilities
   - Review and update security policies

4. **Post-Incident:**
   - Document incident details
   - Update runbooks
   - Enhance monitoring rules
   - Conduct security review

---

## Contact & Support

**Security Issues:**
Report security vulnerabilities privately to: security@yourdomain.com

**Documentation:**
- Cloudflare WAF: https://developers.cloudflare.com/waf/
- OWASP Top 10: https://owasp.org/www-project-top-ten/
- Spring Security: https://spring.io/projects/spring-security

**Compliance:**
- OWASP ASVS Level 2 compliant
- Follows GDPR data protection principles
- Implements CWE Top 25 mitigations
