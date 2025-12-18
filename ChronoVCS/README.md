# ChronoVCS Manual Testing Guide

Bu sənəd ChronoVCS-in bütün yeni feature-lərini manual test etmək üçün tam təlimatdır.

## İçindəkilər
1. [Hazırlıq - Database və Server](#1-hazırlıq)
2. [Test Data Yaratmaq](#2-test-data-yaratmaq)
3. [Branch Management Test](#3-branch-management-test)
4. [Merge və Conflict Test](#4-merge-və-conflict-test)
5. [Diff/Compare Test](#5-diffcompare-test)
6. [File History Test](#6-file-history-test)
7. [Blame Test](#7-blame-test)
8. [Database Verification](#8-database-verification)

---

## 1. Hazırlıq

### 1.1 Database Setup

**PostgreSQL-ə qoşul:**
```bash
psql -U chronovcs_user -d chronovcs_db -h localhost
# Password: chronovcs_password
```

**Database-də cədvəlləri yoxla:**
```sql
-- Cədvəl siyahısı
\dt

-- Əsas cədvəllər:
-- chronovcs_repositories
-- chronovcs_commits
-- chronovcs_branch_heads
-- chronovcs_blobs
-- chronovcs_users
-- chronovcs_repo_permissions
```

**İlkin yoxlama:**
```sql
-- Repository sayı
SELECT COUNT(*) FROM chronovcs_repositories;

-- User sayı
SELECT * FROM chronovcs_users;

-- Branch sayı
SELECT COUNT(*) FROM chronovcs_branch_heads;
```

### 1.2 Server Start

**Terminal 1 - Server başlat:**
```bash
cd /Users/ismile/IdeaProjects/Java_CoreScaffold/ChronoVCS
./gradlew bootRun
```

**Server hazır olduqda görəcəksən:**
```
Started ChronoVcsApplication in X.XXX seconds
```

**Server test et:**
```bash
curl http://localhost:8080/actuator/health
# Nəticə: {"status":"UP"}
```

### 1.3 Authentication Token Al

**Login ol:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "tester",
    "password": "Ismayil1200"
  }'
```

**Nəticə (Token-i kopyala):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Token-i environment variable olaraq saxla:**
```bash
export TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

İndi bütün sorğularda istifadə et:
```bash
-H "Authorization: Bearer $TOKEN"
```

---

## 2. Test Data Yaratmaq

### 2.1 Repository Yarat

**CLI Komanda:**
```bash
curl -X POST http://localhost:8080/api/repositories \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-chronovcs",
    "description": "Test repository for manual testing",
    "versioningMode": "project",
    "privateRepo": false,
    "defaultBranch": "main"
  }'
```

**Nəticə (Terminal-da):**
```json
{
  "id": 1,
  "key": "test-chronovcs",
  "name": "test-chronovcs",
  "description": "Test repository for manual testing",
  "privateRepo": false,
  "versioningMode": "PROJECT",
  "defaultBranch": "main",
  "ownerUid": "...",
  "createdAt": "2025-12-18T..."
}
```

**Database-də Yoxla:**
```sql
SELECT id, repo_key, name, default_branch, created_at
FROM chronovcs_repositories
WHERE repo_key = 'test-chronovcs';
```

**Görməli olduğun:**
```
 id |    repo_key     |      name       | default_branch |     created_at
----+-----------------+-----------------+----------------+-------------------
  1 | test-chronovcs  | test-chronovcs  | main           | 2025-12-18 ...
```

### 2.2 İlk Commit (main branch)

**CLI Komanda:**
```bash
curl -X POST http://localhost:8080/api/repositories/test-chronovcs/push \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "branch": "main",
    "message": "Initial commit - Add README and main.js",
    "timestamp": "2025-01-01T10:00:00",
    "files": [
      {
        "path": "README.md",
        "content": "# ChronoVCS Test Repository\n\nThis is a test repository for manual testing.\n\n## Features\n- Branch management\n- Merge with conflict detection\n- Diff and compare\n- File history\n- Blame",
        "contentType": "text/markdown"
      },
      {
        "path": "src/main.js",
        "content": "console.log(\"Hello World\");\n\nfunction init() {\n  console.log(\"Starting application...\");\n  return true;\n}\n\ninit();",
        "contentType": "text/javascript"
      }
    ]
  }'
```

**Nəticə:**
```json
{
  "success": true,
  "message": "Push successful",
  "commitId": "abc123...",
  "branch": "main"
}
```

**⚠️ ÖNƏMLİ: commitId-ni yaz, sonra lazım olacaq!**

**Database-də Yoxla:**
```sql
-- Commit yarandı?
SELECT commit_id, branch, message, created_at
FROM chronovcs_commits
WHERE branch = 'main'
ORDER BY created_at DESC
LIMIT 1;

-- Branch HEAD yeniləndi?
SELECT branch, head_commit_id, updated_at
FROM chronovcs_branch_heads
WHERE branch = 'main';

-- Blob-lar yarandı? (2 fayl üçün 2 blob)
SELECT COUNT(*) FROM chronovcs_blobs;
```

**Görməli olduğun:**
- 1 commit main branch-də
- 1 branch_head (main)
- 2 blob (README.md və main.js)

### 2.3 İkinci Commit (main-də dəyişiklik)

**CLI Komanda:**
```bash
curl -X POST http://localhost:8080/api/repositories/test-chronovcs/push \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "branch": "main",
    "message": "Update main.js - rename init to initialize",
    "timestamp": "2025-01-02T11:00:00",
    "files": [
      {
        "path": "README.md",
        "content": "# ChronoVCS Test Repository\n\nThis is a test repository for manual testing.\n\n## Features\n- Branch management\n- Merge with conflict detection\n- Diff and compare\n- File history\n- Blame",
        "contentType": "text/markdown"
      },
      {
        "path": "src/main.js",
        "content": "console.log(\"Hello World v2\");\n\nfunction initialize() {\n  console.log(\"Starting application...\");\n  console.log(\"Version 2.0\");\n  return true;\n}\n\ninitialize();",
        "contentType": "text/javascript"
      },
      {
        "path": "src/utils.js",
        "content": "export function helper() {\n  return \"Helper function\";\n}\n\nexport function format(text) {\n  return text.toUpperCase();\n}",
        "contentType": "text/javascript"
      }
    ]
  }'
```

**Nəticə:**
```json
{
  "success": true,
  "commitId": "def456...",
  "branch": "main"
}
```

**Database-də Yoxla:**
```sql
-- İndi 2 commit olmalı
SELECT commit_id, branch, message, parent_commit_id
FROM chronovcs_commits
WHERE branch = 'main'
ORDER BY created_at DESC;

-- 2-ci commit-in parent-i 1-ci commit olmalı
```

---

## 3. Branch Management Test

### 3.1 Branch List (Başlanğıcda yalnız main)

**CLI Komanda:**
```bash
curl -X GET http://localhost:8080/api/repositories/test-chronovcs/branches \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə (Terminal-da):**
```json
{
  "branches": [
    {
      "branchName": "main",
      "headCommitId": "def456...",
      "isDefault": true,
      "isCurrent": false,
      "updatedAt": "2025-12-18T...",
      "commitsAhead": null,
      "commitsBehind": null
    }
  ],
  "defaultBranch": "main",
  "currentBranch": null,
  "totalCount": 1
}
```

**✅ Yoxla:**
- `totalCount`: 1
- `branches` array-də 1 element
- `isDefault`: true
- `commitsAhead` və `commitsBehind`: null (default branch özü)

**Database-də:**
```sql
SELECT * FROM chronovcs_branch_heads;
```

### 3.2 Yeni Branch Yarat (feature/login)

**CLI Komanda:**
```bash
curl -X POST http://localhost:8080/api/repositories/test-chronovcs/branches \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "branchName": "feature/login",
    "fromBranch": "main"
  }'
```

**Nəticə:**
```json
{
  "success": true,
  "message": "Branch created successfully",
  "branch": {
    "branchName": "feature/login",
    "headCommitId": "def456...",
    "isDefault": false,
    "isCurrent": false,
    "updatedAt": "2025-12-18T...",
    "commitsAhead": 0,
    "commitsBehind": 0
  },
  "details": "Created from commit: def456..."
}
```

**✅ Yoxla:**
- `success`: true
- `branchName`: "feature/login"
- `headCommitId`: main ilə eyni
- `commitsAhead`: 0, `commitsBehind`: 0

**Database-də:**
```sql
-- İndi 2 branch olmalı
SELECT branch, head_commit_id, updated_at
FROM chronovcs_branch_heads
ORDER BY branch;
```

**Görməli olduğun:**
```
    branch     | head_commit_id | updated_at
---------------+----------------+------------
 feature/login | def456...      | 2025-12-18...
 main          | def456...      | 2025-12-18...
```

### 3.3 Feature Branch-ə Commit

**CLI Komanda:**
```bash
curl -X POST http://localhost:8080/api/repositories/test-chronovcs/push \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "branch": "feature/login",
    "message": "Add login functionality",
    "timestamp": "2025-01-03T12:00:00",
    "files": [
      {
        "path": "README.md",
        "content": "# ChronoVCS Test Repository\n\nThis is a test repository for manual testing.\n\n## Features\n- Branch management\n- Merge with conflict detection\n- Diff and compare\n- File history\n- Blame",
        "contentType": "text/markdown"
      },
      {
        "path": "src/main.js",
        "content": "console.log(\"Hello World v2\");\n\nfunction initialize() {\n  console.log(\"Starting application...\");\n  console.log(\"Version 2.0\");\n  return true;\n}\n\ninitialize();",
        "contentType": "text/javascript"
      },
      {
        "path": "src/utils.js",
        "content": "export function helper() {\n  return \"Helper function\";\n}\n\nexport function format(text) {\n  return text.toUpperCase();\n}",
        "contentType": "text/javascript"
      },
      {
        "path": "src/login.js",
        "content": "export function login(username, password) {\n  console.log(\"Logging in user:\", username);\n  \n  if (!username || !password) {\n    return { success: false, error: \"Invalid credentials\" };\n  }\n  \n  return { \n    success: true, \n    user: { \n      username: username,\n      loggedInAt: new Date().toISOString()\n    }\n  };\n}\n\nexport function logout() {\n  console.log(\"Logging out...\");\n  return { success: true };\n}",
        "contentType": "text/javascript"
      }
    ]
  }'
```

**Nəticə:**
```json
{
  "success": true,
  "commitId": "ghi789...",
  "branch": "feature/login"
}
```

**Database-də:**
```sql
-- feature/login branch-in HEAD-i yeniləndi?
SELECT branch, head_commit_id
FROM chronovcs_branch_heads
WHERE branch = 'feature/login';

-- Yeni commit yarandı?
SELECT commit_id, branch, message, parent_commit_id
FROM chronovcs_commits
WHERE branch = 'feature/login';
```

### 3.4 Branch List (Commits Ahead/Behind)

**CLI Komanda:**
```bash
curl -X GET http://localhost:8080/api/repositories/test-chronovcs/branches \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə:**
```json
{
  "branches": [
    {
      "branchName": "feature/login",
      "headCommitId": "ghi789...",
      "isDefault": false,
      "isCurrent": false,
      "updatedAt": "2025-12-18T...",
      "commitsAhead": 1,
      "commitsBehind": 0
    },
    {
      "branchName": "main",
      "headCommitId": "def456...",
      "isDefault": true,
      "isCurrent": false,
      "updatedAt": "2025-12-18T...",
      "commitsAhead": null,
      "commitsBehind": null
    }
  ],
  "defaultBranch": "main",
  "currentBranch": null,
  "totalCount": 2
}
```

**✅ Yoxla:**
- `totalCount`: 2
- feature/login: `commitsAhead`: 1, `commitsBehind`: 0
- main: `commitsAhead`: null (default branch)

### 3.5 Konkret Branch Info

**CLI Komanda:**
```bash
curl -X GET http://localhost:8080/api/repositories/test-chronovcs/branches/feature/login \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə:**
```json
{
  "branchName": "feature/login",
  "headCommitId": "ghi789...",
  "isDefault": false,
  "isCurrent": false,
  "updatedAt": "2025-12-18T...",
  "commitsAhead": 1,
  "commitsBehind": 0
}
```

---

## 4. Merge və Conflict Test

### 4.1 Merge Analysis (Konflikt yoxdur)

**CLI Komanda:**
```bash
curl -X GET "http://localhost:8080/api/repositories/test-chronovcs/branches/merge/analyze?sourceBranch=feature/login&targetBranch=main" \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə:**
```json
{
  "canAutoMerge": true,
  "canFastForward": true,
  "mergeBase": "def456...",
  "commitsAhead": 1,
  "commitsBehind": 0,
  "conflicts": [],
  "filesChangedInSource": 1,
  "filesChangedInTarget": 0,
  "summary": "Fast-forward merge is possible. Source is 1 commits ahead and 0 commits behind target. No conflicts detected. Merge can be performed automatically."
}
```

**✅ Yoxla:**
- `canAutoMerge`: true
- `canFastForward`: true
- `conflicts`: [] (boş)
- `filesChangedInSource`: 1 (login.js)

### 4.2 Fast-Forward Merge

**CLI Komanda:**
```bash
curl -X POST http://localhost:8080/api/repositories/test-chronovcs/branches/merge \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceBranch": "feature/login",
    "targetBranch": "main",
    "strategy": "fast-forward",
    "message": "Merge feature/login into main"
  }'
```

**Nəticə:**
```json
{
  "success": true,
  "message": "Fast-forward merge completed successfully",
  "branch": {
    "branchName": "main",
    "headCommitId": "ghi789...",
    "isDefault": true,
    "isCurrent": false,
    "updatedAt": "2025-12-18T...",
    "commitsAhead": null,
    "commitsBehind": null
  },
  "details": "Fast-forwarded from def456... to ghi789..."
}
```

**Database-də:**
```sql
-- main branch-in HEAD-i yeniləndi?
SELECT branch, head_commit_id
FROM chronovcs_branch_heads
WHERE branch = 'main';

-- İndi main və feature/login eyni HEAD-də olmalı
SELECT branch, head_commit_id
FROM chronovcs_branch_heads
ORDER BY branch;
```

### 4.3 Conflict Yaratmaq

**Əvvəlcə main-ə commit (utils.js dəyişir):**
```bash
curl -X POST http://localhost:8080/api/repositories/test-chronovcs/push \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "branch": "main",
    "message": "Update utils.js in main branch",
    "timestamp": "2025-01-04T13:00:00",
    "files": [
      {
        "path": "README.md",
        "content": "# ChronoVCS Test Repository\n\nThis is a test repository for manual testing.\n\n## Features\n- Branch management\n- Merge with conflict detection\n- Diff and compare\n- File history\n- Blame",
        "contentType": "text/markdown"
      },
      {
        "path": "src/main.js",
        "content": "console.log(\"Hello World v2\");\n\nfunction initialize() {\n  console.log(\"Starting application...\");\n  console.log(\"Version 2.0\");\n  return true;\n}\n\ninitialize();",
        "contentType": "text/javascript"
      },
      {
        "path": "src/utils.js",
        "content": "export function helper() {\n  return \"Helper function from MAIN BRANCH\";\n}\n\nexport function format(text) {\n  return text.toLowerCase();\n}",
        "contentType": "text/javascript"
      },
      {
        "path": "src/login.js",
        "content": "export function login(username, password) {\n  console.log(\"Logging in user:\", username);\n  \n  if (!username || !password) {\n    return { success: false, error: \"Invalid credentials\" };\n  }\n  \n  return { \n    success: true, \n    user: { \n      username: username,\n      loggedInAt: new Date().toISOString()\n    }\n  };\n}\n\nexport function logout() {\n  console.log(\"Logging out...\");\n  return { success: true };\n}",
        "contentType": "text/javascript"
      }
    ]
  }'
```

**Sonra feature/login-ə fərqli commit (eyni fayl, fərqli content):**
```bash
curl -X POST http://localhost:8080/api/repositories/test-chronovcs/push \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "branch": "feature/login",
    "message": "Update utils.js in feature branch",
    "timestamp": "2025-01-04T14:00:00",
    "files": [
      {
        "path": "README.md",
        "content": "# ChronoVCS Test Repository\n\nThis is a test repository for manual testing.\n\n## Features\n- Branch management\n- Merge with conflict detection\n- Diff and compare\n- File history\n- Blame",
        "contentType": "text/markdown"
      },
      {
        "path": "src/main.js",
        "content": "console.log(\"Hello World v2\");\n\nfunction initialize() {\n  console.log(\"Starting application...\");\n  console.log(\"Version 2.0\");\n  return true;\n}\n\ninitialize();",
        "contentType": "text/javascript"
      },
      {
        "path": "src/utils.js",
        "content": "export function helper() {\n  return \"Helper function from FEATURE BRANCH\";\n}\n\nexport function format(text) {\n  return text.toUpperCase();\n}",
        "contentType": "text/javascript"
      },
      {
        "path": "src/login.js",
        "content": "export function login(username, password) {\n  console.log(\"Logging in user:\", username);\n  \n  if (!username || !password) {\n    return { success: false, error: \"Invalid credentials\" };\n  }\n  \n  return { \n    success: true, \n    user: { \n      username: username,\n      loggedInAt: new Date().toISOString()\n    }\n  };\n}\n\nexport function logout() {\n  console.log(\"Logging out...\");\n  return { success: true };\n}",
        "contentType": "text/javascript"
      }
    ]
  }'
```

### 4.4 Conflict Detection

**CLI Komanda:**
```bash
curl -X GET "http://localhost:8080/api/repositories/test-chronovcs/branches/merge/analyze?sourceBranch=feature/login&targetBranch=main" \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə:**
```json
{
  "canAutoMerge": false,
  "canFastForward": false,
  "mergeBase": "ghi789...",
  "commitsAhead": 1,
  "commitsBehind": 1,
  "conflicts": [
    {
      "filePath": "src/utils.js",
      "baseBlob": "old_hash...",
      "targetBlob": "main_hash...",
      "sourceBlob": "feature_hash...",
      "conflictType": "MODIFIED_MODIFIED",
      "description": "Both branches modified this file differently"
    }
  ],
  "filesChangedInSource": 1,
  "filesChangedInTarget": 1,
  "summary": "Source is 1 commits ahead and 1 commits behind target. Found 1 conflict(s) that need manual resolution."
}
```

**✅ Yoxla:**
- `canAutoMerge`: false
- `conflicts` array-də 1 element
- `conflictType`: "MODIFIED_MODIFIED"
- `filePath`: "src/utils.js"

---

## 5. Diff/Compare Test

### 5.1 Branch Diff (Git-style)

**CLI Komanda:**
```bash
curl -X GET "http://localhost:8080/api/repositories/test-chronovcs/compare/main...feature/login" \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə:**
```json
{
  "baseCommitId": "main_commit...",
  "headCommitId": "feature_commit...",
  "baseCommitMessage": "Update utils.js in main branch",
  "headCommitMessage": "Update utils.js in feature branch",
  "files": [
    {
      "oldPath": "src/utils.js",
      "newPath": "src/utils.js",
      "changeType": "MODIFIED",
      "oldBlobHash": "main_blob...",
      "newBlobHash": "feature_blob...",
      "linesAdded": null,
      "linesDeleted": null,
      "totalChanges": null,
      "patch": null,
      "binary": false
    }
  ],
  "stats": {
    "filesChanged": 1,
    "filesAdded": 0,
    "filesModified": 1,
    "filesDeleted": 0,
    "totalLinesAdded": 0,
    "totalLinesDeleted": 0,
    "totalChanges": 0
  },
  "identical": false
}
```

**✅ Yoxla:**
- `identical`: false
- `files` array-də dəyişikliklər
- `changeType`: "MODIFIED"

### 5.2 Diff with Patch

**CLI Komanda:**
```bash
curl -X GET "http://localhost:8080/api/repositories/test-chronovcs/compare/main...feature/login?patch=true" \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə (əlavə olaraq):**
```json
{
  ...
  "files": [
    {
      "oldPath": "src/utils.js",
      "newPath": "src/utils.js",
      "changeType": "MODIFIED",
      "oldBlobHash": "...",
      "newBlobHash": "...",
      "linesAdded": 1,
      "linesDeleted": 1,
      "totalChanges": 2,
      "patch": "-export function helper() {\n-  return \"Helper function from MAIN BRANCH\";\n-}\n+export function helper() {\n+  return \"Helper function from FEATURE BRANCH\";\n+}\n ...",
      "binary": false
    }
  ],
  "stats": {
    "filesChanged": 1,
    "filesAdded": 0,
    "filesModified": 1,
    "filesDeleted": 0,
    "totalLinesAdded": 1,
    "totalLinesDeleted": 1,
    "totalChanges": 2
  }
}
```

**✅ Yoxla:**
- `patch` field dolu
- `linesAdded` və `linesDeleted` düzgün
- Patch-də "-" (silindi) və "+" (əlavə) sətirlər

### 5.3 Commit Diff

**CLI Komanda (ən son commit-in diff-i):**
```bash
# Əvvəl son commit ID-ni tap
curl -X GET http://localhost:8080/api/repositories/test-chronovcs/commits?branch=main \
  -H "Authorization: Bearer $TOKEN"

# Sonra diff al (COMMIT_ID-ni əvəz et)
curl -X GET "http://localhost:8080/api/repositories/test-chronovcs/commits/COMMIT_ID/diff?patch=true" \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə:**
- Commit-in parent ilə fərqi
- Hansı fayllar dəyişdi
- Patch content

---

## 6. File History Test

### 6.1 File History (src/utils.js)

**CLI Komanda:**
```bash
curl -X GET "http://localhost:8080/api/repositories/test-chronovcs/files/src/utils.js/history?branch=main&limit=10" \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə:**
```json
{
  "filePath": "src/utils.js",
  "repoKey": "test-chronovcs",
  "totalCommits": 2,
  "commits": [
    {
      "commitId": "latest_commit...",
      "message": "Update utils.js in main branch",
      "author": "Unknown",
      "timestamp": "2025-01-04T13:00:00",
      "branch": "main",
      "changeType": "MODIFIED",
      "oldPath": "src/utils.js",
      "newPath": "src/utils.js",
      "blobHash": "new_blob...",
      "linesAdded": 1,
      "linesDeleted": 1,
      "createdAt": "2025-12-18T..."
    },
    {
      "commitId": "old_commit...",
      "message": "Update main.js - rename init to initialize",
      "author": "Unknown",
      "timestamp": "2025-01-02T11:00:00",
      "branch": "main",
      "changeType": "ADDED",
      "oldPath": null,
      "newPath": "src/utils.js",
      "blobHash": "old_blob...",
      "linesAdded": null,
      "linesDeleted": null,
      "createdAt": "2025-12-18T..."
    }
  ],
  "exists": true,
  "currentBlobHash": "current_blob..."
}
```

**✅ Yoxla:**
- `totalCommits` > 0
- Commit-lər ən yenidən köhnəyə
- İlk commit: `changeType`: "ADDED"
- Sonrakı: `changeType`: "MODIFIED"
- `exists`: true

**Database-də:**
```sql
-- Bütün commit-lərdə utils.js-in mövcudluğunu yoxla
SELECT c.commit_id, c.message, c.files_json
FROM chronovcs_commits c
WHERE c.branch = 'main'
ORDER BY c.created_at DESC;

-- files_json içində "src/utils.js" olmalı
```

### 6.2 Feature Branch-də File History

**CLI Komanda:**
```bash
curl -X GET "http://localhost:8080/api/repositories/test-chronovcs/files/src/login.js/history?branch=feature/login&limit=10" \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə:**
```json
{
  "filePath": "src/login.js",
  "repoKey": "test-chronovcs",
  "totalCommits": 1,
  "commits": [
    {
      "commitId": "...",
      "message": "Add login functionality",
      "author": "Unknown",
      "timestamp": "2025-01-03T12:00:00",
      "branch": "feature/login",
      "changeType": "ADDED",
      "oldPath": null,
      "newPath": "src/login.js",
      "blobHash": "...",
      "linesAdded": null,
      "linesDeleted": null,
      "createdAt": "2025-12-18T..."
    }
  ],
  "exists": true,
  "currentBlobHash": "..."
}
```

**✅ Yoxla:**
- `totalCommits`: 1
- `changeType`: "ADDED"
- login.js yalnız feature branch-də var

---

## 7. Blame Test

### 7.1 Main Branch-də Blame

**CLI Komanda:**
```bash
curl -X GET "http://localhost:8080/api/repositories/test-chronovcs/files/src/main.js/blame?commit=main" \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə:**
```json
{
  "filePath": "src/main.js",
  "repoKey": "test-chronovcs",
  "commitId": "current_commit...",
  "branch": "main",
  "totalLines": 8,
  "lines": [
    {
      "lineNumber": 1,
      "commitId": "current_commit...",
      "commitMessage": "Update main.js - rename init to initialize",
      "author": "Unknown",
      "timestamp": "2025-01-02T11:00:00",
      "content": "console.log(\"Hello World v2\");",
      "age": 0
    },
    {
      "lineNumber": 2,
      "commitId": "current_commit...",
      "commitMessage": "Update main.js - rename init to initialize",
      "author": "Unknown",
      "timestamp": "2025-01-02T11:00:00",
      "content": "",
      "age": 0
    },
    {
      "lineNumber": 3,
      "commitId": "current_commit...",
      "commitMessage": "Update main.js - rename init to initialize",
      "author": "Unknown",
      "timestamp": "2025-01-02T11:00:00",
      "content": "function initialize() {",
      "age": 0
    }
    // ... digər sətirlər
  ],
  "binary": false,
  "uniqueCommits": 1,
  "uniqueAuthors": 1
}
```

**✅ Yoxla:**
- `totalLines` düzgün
- Hər sətir üçün:
  - `lineNumber`: 1-dən başlayır
  - `commitId` dolu
  - `content`: sətrin real content-i
  - `author`: "Unknown" (hal-hazırda)
- `binary`: false
- `uniqueCommits` və `uniqueAuthors` düzgün

### 7.2 Query Parameter ilə Blame

**CLI Komanda:**
```bash
curl -X GET "http://localhost:8080/api/repositories/test-chronovcs/blame?path=src/utils.js&commit=main" \
  -H "Authorization: Bearer $TOKEN"
```

**Nəticə:**
- utils.js-in hər sətri üçün blame məlumatı
- Kim yazdı, nə zaman, hansı commit

---

## 8. Database Verification

### 8.1 Repository Status

```sql
-- Repositories
SELECT * FROM chronovcs_repositories WHERE repo_key = 'test-chronovcs';

-- Branches
SELECT * FROM chronovcs_branch_heads WHERE repository_id = (
  SELECT id FROM chronovcs_repositories WHERE repo_key = 'test-chronovcs'
);

-- Commits
SELECT commit_id, branch, message, parent_commit_id, created_at
FROM chronovcs_commits
WHERE repository_id = (
  SELECT id FROM chronovcs_repositories WHERE repo_key = 'test-chronovcs'
)
ORDER BY created_at DESC;

-- Blobs
SELECT COUNT(*), SUM(content_size) as total_size
FROM chronovcs_blobs
WHERE repository_id = (
  SELECT id FROM chronovcs_repositories WHERE repo_key = 'test-chronovcs'
);
```

### 8.2 Commit Graph Visualization

```sql
-- Parent-child relationships
SELECT
  c.commit_id as commit,
  c.parent_commit_id as parent,
  c.branch,
  c.message,
  c.created_at
FROM chronovcs_commits c
WHERE repository_id = (
  SELECT id FROM chronovcs_repositories WHERE repo_key = 'test-chronovcs'
)
ORDER BY c.created_at;
```

### 8.3 File Changes Tracking

```sql
-- Faylların dəyişiklik tarixçəsi (files_json-dan)
SELECT
  commit_id,
  branch,
  message,
  files_json::jsonb
FROM chronovcs_commits
WHERE repository_id = (
  SELECT id FROM chronovcs_repositories WHERE repo_key = 'test-chronovcs'
)
ORDER BY created_at DESC;
```

---

## 9. Edge Cases Test

### 9.1 Mövcud Olmayan Branch

```bash
curl -X GET http://localhost:8080/api/repositories/test-chronovcs/branches/nonexistent \
  -H "Authorization: Bearer $TOKEN"
```

**Gözlənilən:** Status 404 və ya 400, Error message

### 9.2 Mövcud Olmayan File History

```bash
curl -X GET "http://localhost:8080/api/repositories/test-chronovcs/files/nonexistent.js/history" \
  -H "Authorization: Bearer $TOKEN"
```

**Gözlənilən:**
- `totalCommits`: 0
- `exists`: false

### 9.3 Default Branch Silməyə Çalış

```bash
curl -X DELETE "http://localhost:8080/api/repositories/test-chronovcs/branches/main" \
  -H "Authorization: Bearer $TOKEN"
```

**Gözlənilən:** Status 400, Error: "Cannot delete the default branch"

---

## 10. Cleanup (Təmizləmək)

Test bitdikdən sonra:

```sql
-- Repository və bütün əlaqəli data-nı sil
DELETE FROM chronovcs_commits WHERE repository_id = (
  SELECT id FROM chronovcs_repositories WHERE repo_key = 'test-chronovcs'
);

DELETE FROM chronovcs_branch_heads WHERE repository_id = (
  SELECT id FROM chronovcs_repositories WHERE repo_key = 'test-chronovcs'
);

DELETE FROM chronovcs_blobs WHERE repository_id = (
  SELECT id FROM chronovcs_repositories WHERE repo_key = 'test-chronovcs'
);

DELETE FROM chronovcs_repositories WHERE repo_key = 'test-chronovcs';
```

---

## Qeydlər

**✅ = Test uğurlu**
**❌ = Test uğursuz**
**⚠️ = Xəbərdarlıq və ya qeyd**

Test zamanı tapılan problemləri qeyd et:
- Status kod düzgün deyil?
- Response structure düzgün deyil?
- Database-də data düzgün yaranmır?
- Edge case-lər düzgün handle olunmur?

Hər problem üçün:
1. Hansı test
2. Nə gözləyirdin
3. Nə aldın
4. Error log (server console-dan)
