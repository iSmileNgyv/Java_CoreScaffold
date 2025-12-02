# ChronoVCS Developer Guide

## 📋 Table of Contents
1. [System Architecture](#system-architecture)
2. [Backend (ChronoVCS)](#backend-chronovcs)
3. [CLI (ChronoVCS-CLI)](#cli-chronovcs-cli)
4. [API Reference](#api-reference)
5. [Database Schema](#database-schema)
6. [Authentication Flow](#authentication-flow)
7. [Clone Implementation](#clone-implementation)

---

## System Architecture

ChronoVCS is a distributed version control system consisting of two main components:

```
┌─────────────────┐         HTTP/REST API        ┌─────────────────┐
│  ChronoVCS-CLI  │  ◄──────────────────────────► │    ChronoVCS    │
│   (Client)      │                                │    (Server)     │
└─────────────────┘                                └─────────────────┘
        │                                                   │
        │ Local Storage                                    │ Database
        ▼                                                   ▼
   .vcs/ directory                                  PostgreSQL/MySQL
```

### Key Features
- **Project-level versioning**: Track entire project snapshots
- **Object-level versioning**: Track individual file history
- **Distributed**: Full repository clone with local operations
- **RESTful API**: Standard HTTP-based communication
- **Token authentication**: Secure PAT (Personal Access Token) based auth

---

## Backend (ChronoVCS)

### Technology Stack
- **Framework**: Spring Boot 3.x
- **Language**: Java 17+
- **Database**: JPA/Hibernate with PostgreSQL/MySQL
- **Security**: Spring Security with Basic Auth
- **Build**: Gradle

### Project Structure

```
ChronoVCS/
├── src/main/java/com/ismile/core/chronovcs/
│   ├── config/                     # Configuration classes
│   │   ├── SecurityConfig.java     # Security & auth config
│   │   └── AppConfig.java          # General app config
│   │
│   ├── controller/                 # REST API controllers
│   │   ├── AuthController.java     # Login, token management
│   │   ├── RepositoryController.java # Repo operations & clone APIs
│   │   └── PushController.java     # Push operations
│   │
│   ├── dto/                        # Data Transfer Objects
│   │   ├── auth/                   # Auth DTOs
│   │   ├── clone/                  # Clone API DTOs
│   │   │   ├── RefsResponseDto.java
│   │   │   ├── CommitHistoryResponseDto.java
│   │   │   ├── BatchObjectsRequestDto.java
│   │   │   └── BatchObjectsResponseDto.java
│   │   ├── handshake/              # Handshake DTOs
│   │   └── push/                   # Push DTOs
│   │       └── CommitSnapshotDto.java
│   │
│   ├── entity/                     # JPA Entities
│   │   ├── UserEntity.java         # User accounts
│   │   ├── UserTokenEntity.java    # Personal Access Tokens
│   │   ├── RepositoryEntity.java   # Repository metadata
│   │   ├── CommitEntity.java       # Commit records
│   │   ├── BlobEntity.java         # File blob metadata
│   │   ├── BranchHeadEntity.java   # Branch pointers
│   │   └── RepoPermissionEntity.java # Access control
│   │
│   ├── repository/                 # JPA Repositories
│   │   ├── UserRepository.java
│   │   ├── CommitRepository.java
│   │   ├── BlobRepository.java
│   │   └── BranchHeadRepository.java
│   │
│   ├── service/                    # Business logic
│   │   ├── auth/                   # Authentication services
│   │   │   ├── AuthService.java
│   │   │   └── JwtTokenService.java
│   │   ├── clone/                  # Clone services
│   │   │   └── CloneService.java
│   │   ├── repository/             # Repository services
│   │   │   └── RepositoryService.java
│   │   ├── storage/                # Storage services
│   │   │   ├── BlobStorageService.java
│   │   │   ├── CommitStorage.java
│   │   │   └── impl/
│   │   └── versioning/             # Versioning strategies
│   │       └── PushService.java
│   │
│   └── security/                   # Security components
│       ├── ChronoAuthFilter.java
│       └── PatAuthenticationProvider.java
│
└── src/main/resources/
    └── application.yml             # Application configuration
```

### Core Components

#### 1. Entity Layer

**RepositoryEntity** - Repository metadata
```java
- id: Long
- repoKey: String (unique identifier)
- name: String
- description: String
- privateRepo: boolean
- versioningMode: VersioningMode (PROJECT/OBJECT)
- defaultBranch: String
- owner: UserEntity
- storageType: StorageType (LOCAL/S3/etc)
```

**CommitEntity** - Commit records
```java
- id: Long
- repository: RepositoryEntity
- commitId: String (hash from client)
- parentCommitId: String
- branch: String
- message: String
- timestamp: String (ISO-8601)
- filesJson: String (JSON map: filename -> blobHash)
```

**BlobEntity** - File blob metadata
```java
- id: Long
- repository: RepositoryEntity
- hash: String (SHA-256)
- storageType: StorageType
- storagePath: String (where actual content is stored)
- contentType: String
- contentSize: Long
```

**BranchHeadEntity** - Branch pointers
```java
- id: Long
- repository: RepositoryEntity
- branch: String
- headCommitId: String (points to latest commit)
```

#### 2. Service Layer

**CloneService** - Clone operations
```java
+ getRefs(repoKey): RefsResponseDto
  // Returns all branches and their HEAD commits

+ getCommit(repoKey, commitHash): CommitSnapshotDto
  // Returns single commit details

+ getCommitHistory(repoKey, branch, limit, fromCommit): CommitHistoryResponseDto
  // Returns commit chain by following parent links

+ getBatchObjects(repoKey, hashes): BatchObjectsResponseDto
  // Returns blob content for multiple hashes (base64 encoded)
```

**BlobStorageService** - Blob storage abstraction
```java
+ saveBlob(repository, hash, content, contentType): BlobEntity
  // Saves blob content to storage (local/S3/etc)

+ findByHash(repository, hash): Optional<BlobEntity>
  // Finds blob metadata

+ loadContent(blob): byte[]
  // Loads blob content from storage
```

**PushService** - Push operations
```java
+ push(user, repoKey, request): PushResultDto
  // Processes push request:
  // 1. Validates permissions
  // 2. Saves blobs
  // 3. Saves commit
  // 4. Updates branch head
```

#### 3. Storage Strategy

Blobs are stored using a Git-like structure:
```
<storage-root>/
  <repoKey>/
    <first-2-chars-of-hash>/
      <remaining-hash>
```

Example:
```
storage/
  chronovcs/
    ab/
      cdef1234567890...  (actual file content)
```

---

## CLI (ChronoVCS-CLI)

### Technology Stack
- **Framework**: Spring Boot 3.x (for dependency injection)
- **CLI Framework**: Picocli
- **Language**: Java 17+
- **HTTP Client**: Java HttpClient
- **Build**: Gradle

### Project Structure

```
ChronoVCS-CLI/
├── src/main/java/com/ismile/core/chronovcscli/
│   ├── commands/                   # CLI Commands
│   │   ├── ChronoCommand.java      # Root command
│   │   ├── InitCommand.java        # chronovcs init
│   │   ├── AddCommand.java         # chronovcs add
│   │   ├── CommitCommand.java      # chronovcs commit
│   │   ├── StatusCommand.java      # chronovcs status
│   │   ├── LoginCommand.java       # chronovcs login
│   │   ├── PushCommand.java        # chronovcs push
│   │   ├── CloneCommand.java       # chronovcs clone
│   │   ├── RemoteConfigCommand.java
│   │   └── RemoteHandshakeCommand.java
│   │
│   ├── core/                       # Core VCS logic
│   │   ├── VcsDirectoryManager.java # .vcs directory setup
│   │   ├── add/
│   │   │   ├── AddEngine.java
│   │   │   └── impl/AddEngineImpl.java
│   │   ├── commit/
│   │   │   ├── CommitEngine.java
│   │   │   ├── CommitModel.java
│   │   │   └── impl/CommitEngineImpl.java
│   │   ├── hash/
│   │   │   ├── HashEngine.java
│   │   │   └── impl/Sha256HashEngine.java
│   │   ├── ignore/
│   │   │   ├── IgnoreEngine.java
│   │   │   ├── IgnoreParser.java
│   │   │   └── IgnoreRule.java
│   │   ├── index/
│   │   │   ├── IndexEngine.java
│   │   │   ├── IndexEntry.java
│   │   │   ├── IndexModel.java
│   │   │   └── impl/IndexEngineImpl.java
│   │   ├── objectsStore/
│   │   │   ├── ObjectStore.java
│   │   │   └── impl/ObjectStoreImpl.java
│   │   └── status/
│   │       ├── StatusEngine.java
│   │       ├── StatusResult.java
│   │       └── impl/StatusEngineImpl.java
│   │
│   ├── remote/                     # Remote API communication
│   │   ├── RemoteCloneService.java # Clone API calls
│   │   ├── RemotePushService.java  # Push API calls
│   │   ├── RemoteHandshakeService.java
│   │   ├── RemoteConfig.java       # Remote config model
│   │   ├── RemoteConfigService.java
│   │   └── dto/                    # Remote DTOs
│   │       ├── CommitSnapshotDto.java
│   │       ├── RefsResponseDto.java
│   │       ├── CommitHistoryResponseDto.java
│   │       ├── BatchObjectsRequestDto.java
│   │       └── BatchObjectsResponseDto.java
│   │
│   ├── auth/                       # Authentication
│   │   ├── CredentialsService.java # Credential management
│   │   ├── CredentialsStore.java   # Credential storage model
│   │   └── CredentialsEntry.java   # Single credential entry
│   │
│   └── config/
│       └── HttpClientConfig.java
│
└── start.sh                        # CLI startup script
```

### Local Repository Structure

When you run `chronovcs init` or `chronovcs clone`, this structure is created:

```
.vcs/
├── objects/                 # Content-addressed blob storage
│   ├── ab/
│   │   └── cdef1234...     # Blob file (hash: abcdef1234...)
│   └── 12/
│       └── 3456789a...
│
├── commits/                 # Commit metadata (JSON files)
│   ├── hash1234...         # Commit JSON file
│   └── hash5678...
│
├── refs/                    # Branch references
│   └── heads/
│       ├── main            # Contains commit hash
│       └── develop
│
├── HEAD                     # Current branch pointer
│                            # Content: "ref: refs/heads/main"
│
├── index                    # Staging area (JSON)
│                            # Maps files to their staged blob hashes
│
├── config                   # Repository config
│                            # Contains default_branch, versioning_mode
│
└── remote                   # Remote server config (JSON)
                             # Contains baseUrl, repoKey
```

### Core Components

#### 1. VCS Directory Manager

**VcsDirectoryManager** - Repository initialization
```java
+ initRepository(): void
  // Creates .vcs directory structure
  // Sets up default branch (main)
  // Creates HEAD pointer
  // Creates config file
```

#### 2. Object Store

**ObjectStore** - Content-addressed storage
```java
+ writeBlob(file): String
  // 1. Calculate SHA-256 hash of file content
  // 2. Store in .vcs/objects/<prefix>/<suffix>
  // 3. Return hash
  // Uses Git-like object storage

+ readBlob(hash): byte[]
  // Retrieve blob content by hash

+ exists(hash): boolean
  // Check if blob exists locally
```

Storage pattern:
```
hash = "abcdef1234567890..."
path = ".vcs/objects/ab/cdef1234567890..."
      prefix = first 2 chars
      suffix = remaining chars
```

#### 3. Index Engine

**IndexEngine** - Staging area management
```java
+ addFile(file, hash): void
  // Add file to staging area (index)

+ getIndex(): IndexModel
  // Load current staging area

+ saveIndex(model): void
  // Persist staging area to .vcs/index
```

**IndexModel** - Index structure
```json
{
  "entries": [
    {
      "path": "src/Main.java",
      "hash": "abcdef123...",
      "timestamp": "2025-11-30T10:00:00Z"
    }
  ]
}
```

#### 4. Commit Engine

**CommitEngine** - Commit creation
```java
+ commit(projectRoot, message): String
  // 1. Load staged files from index
  // 2. Create commit object (JSON)
  // 3. Link to parent commit
  // 4. Save to .vcs/commits/<hash>
  // 5. Update branch HEAD
  // 6. Clear index
  // Returns: commit hash
```

**CommitModel** - Commit structure
```json
{
  "id": "commit-hash-123",
  "parent": "parent-hash-456",
  "branch": "main",
  "message": "Initial commit",
  "timestamp": "2025-11-30T10:00:00Z",
  "files": {
    "src/Main.java": "blob-hash-1",
    "README.md": "blob-hash-2"
  }
}
```

#### 5. Remote Services

**RemoteCloneService** - Clone API client
```java
+ getRefs(config, creds): RefsResponseDto
  // GET /api/repositories/{repoKey}/refs

+ getCommit(config, creds, commitHash): CommitSnapshotDto
  // GET /api/repositories/{repoKey}/commits/{commitHash}

+ getCommitHistory(config, creds, branch, limit): CommitHistoryResponseDto
  // GET /api/repositories/{repoKey}/commits?branch=X&limit=Y

+ getBatchObjects(config, creds, hashes): BatchObjectsResponseDto
  // POST /api/repositories/{repoKey}/objects/batch
```

**RemotePushService** - Push API client
```java
+ push(config, creds, request): PushResultDto
  // POST /api/repositories/{repoKey}/push
  // Sends commit + blobs to server
```

#### 6. Authentication

**CredentialsService** - Credential management
```java
+ findForServer(baseUrl): Optional<CredentialsEntry>
  // Find stored credentials for server

+ saveOrUpdate(entry): void
  // Save/update credentials
  // Stored in ~/.vcs/credentials.json
```

**Credentials Storage** (`~/.vcs/credentials.json`):
```json
{
  "servers": [
    {
      "baseUrl": "http://localhost:8080",
      "userUid": "user-123",
      "email": "user@example.com",
      "token": "pat_abc123..."
    }
  ]
}
```

---

## API Reference

### Authentication APIs

#### POST /api/auth/login
Login and get tokens.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "expiresIn": 3600
}
```

#### POST /api/auth/tokens
Create Personal Access Token.

**Request:**
```json
{
  "name": "my-cli-token",
  "expiresAt": "2025-12-31T23:59:59Z"
}
```

**Response:**
```json
{
  "tokenId": "123",
  "token": "pat_abc123...",
  "name": "my-cli-token",
  "expiresAt": "2025-12-31T23:59:59Z"
}
```

### Repository APIs

#### POST /api/repositories/{repoKey}/handshake
Verify access and get repository info.

**Response:**
```json
{
  "success": true,
  "user": {
    "userUid": "user-123",
    "email": "user@example.com"
  },
  "repository": {
    "repoKey": "chronovcs",
    "name": "ChronoVCS Repository",
    "versioningMode": "PROJECT"
  },
  "permissions": {
    "canRead": true,
    "canWrite": true
  }
}
```

#### GET /api/repositories/{repoKey}/refs
Get branch references.

**Response:**
```json
{
  "defaultBranch": "main",
  "branches": {
    "main": "commit-hash-abc123",
    "develop": "commit-hash-def456"
  }
}
```

#### GET /api/repositories/{repoKey}/commits/{commitHash}
Get single commit details.

**Response:**
```json
{
  "id": "commit-hash-123",
  "parent": "commit-hash-456",
  "authorUid": "user-123",
  "branch": "main",
  "message": "Initial commit",
  "timestamp": "2025-11-30T10:00:00Z",
  "files": {
    "src/Main.java": "blob-hash-1",
    "README.md": "blob-hash-2"
  }
}
```

#### GET /api/repositories/{repoKey}/commits
Get commit history.

**Query Parameters:**
- `branch` (optional, default: "main") - Branch name
- `limit` (optional, default: 100) - Max commits to return
- `fromCommit` (optional) - Start from this commit

**Response:**
```json
{
  "commits": [
    {
      "id": "hash-123",
      "parent": "hash-456",
      "message": "Latest commit",
      "timestamp": "2025-11-30T12:00:00Z",
      "branch": "main",
      "files": {...}
    }
  ],
  "hasMore": false
}
```

#### POST /api/repositories/{repoKey}/objects/batch
Download multiple blobs.

**Request:**
```json
{
  "hashes": ["blob-hash-1", "blob-hash-2", "blob-hash-3"]
}
```

**Response:**
```json
{
  "objects": {
    "blob-hash-1": "base64-encoded-content...",
    "blob-hash-2": "base64-encoded-content...",
    "blob-hash-3": "base64-encoded-content..."
  }
}
```

#### POST /api/repositories/{repoKey}/push
Push commits and blobs.

**Request:**
```json
{
  "branch": "main",
  "baseCommitId": "parent-hash-456",
  "newCommit": {
    "id": "new-hash-123",
    "parent": "parent-hash-456",
    "message": "New commit",
    "timestamp": "2025-11-30T12:00:00Z",
    "files": {
      "file.txt": "blob-hash-1"
    }
  },
  "blobs": {
    "blob-hash-1": "base64-content..."
  }
}
```

**Response:**
```json
{
  "success": true,
  "message": "Push successful",
  "commitId": "new-hash-123"
}
```

---

## Database Schema

### Users & Authentication

```sql
-- Users
CREATE TABLE chronovcs_users (
    id BIGSERIAL PRIMARY KEY,
    user_uid VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- Personal Access Tokens
CREATE TABLE chronovcs_user_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES chronovcs_users(id),
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255),
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

-- Refresh Tokens
CREATE TABLE chronovcs_refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES chronovcs_users(id),
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

### Repositories

```sql
-- Repositories
CREATE TABLE chronovcs_repositories (
    id BIGSERIAL PRIMARY KEY,
    repo_key VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    is_private BOOLEAN NOT NULL DEFAULT true,
    versioning_mode VARCHAR(32) NOT NULL, -- PROJECT or OBJECT
    default_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    owner_id BIGINT NOT NULL REFERENCES chronovcs_users(id),
    storage_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- Repository Permissions
CREATE TABLE chronovcs_repo_permissions (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES chronovcs_repositories(id),
    user_id BIGINT NOT NULL REFERENCES chronovcs_users(id),
    permission VARCHAR(32) NOT NULL, -- READ, WRITE, ADMIN
    granted_at TIMESTAMP NOT NULL
);
```

### Version Control

```sql
-- Commits
CREATE TABLE chronovcs_commits (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES chronovcs_repositories(id),
    commit_id VARCHAR(128) NOT NULL,
    parent_commit_id VARCHAR(128),
    branch VARCHAR(255) NOT NULL,
    message VARCHAR(2000),
    timestamp VARCHAR(64),
    files_json TEXT NOT NULL, -- JSON: {"file.txt": "blob-hash"}
    created_at TIMESTAMP NOT NULL,

    INDEX idx_commit_repo_commit_id (repository_id, commit_id),
    INDEX idx_commit_repo_branch (repository_id, branch)
);

-- Blobs (File Content)
CREATE TABLE chronovcs_blobs (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES chronovcs_repositories(id),
    hash VARCHAR(128) NOT NULL,
    storage_type VARCHAR(32) NOT NULL, -- LOCAL, S3, etc.
    storage_path VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255),
    content_size BIGINT,
    created_at TIMESTAMP NOT NULL,

    UNIQUE (repository_id, hash),
    INDEX idx_blob_repo_hash (repository_id, hash)
);

-- Branch Heads
CREATE TABLE chronovcs_branch_heads (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES chronovcs_repositories(id),
    branch VARCHAR(255) NOT NULL,
    head_commit_id VARCHAR(128),
    updated_at TIMESTAMP NOT NULL,

    UNIQUE (repository_id, branch)
);
```

---

## Authentication Flow

### 1. Initial Login Flow

```
CLI                           Backend
 │                               │
 │  POST /api/auth/login         │
 │  {email, password}           │
 ├──────────────────────────────>│
 │                               │ Validate credentials
 │                               │ Generate JWT tokens
 │                               │
 │  {accessToken, refreshToken} │
 │<──────────────────────────────┤
 │                               │
 │  POST /api/auth/tokens        │
 │  Authorization: Bearer {JWT}  │
 ├──────────────────────────────>│
 │                               │ Create PAT
 │  {token: "pat_abc123..."}     │
 │<──────────────────────────────┤
 │                               │
 │  Save to ~/.vcs/credentials   │
 │                               │
```

### 2. Subsequent API Calls (Basic Auth with PAT)

```
CLI                           Backend
 │                               │
 │  Load credentials from        │
 │  ~/.vcs/credentials.json      │
 │                               │
 │  GET /api/repositories/.../   │
 │  Authorization: Basic {base64}│
 │  base64(email:pat_token)      │
 ├──────────────────────────────>│
 │                               │ Decode Basic Auth
 │                               │ Validate PAT
 │                               │ Load user
 │  Response                     │
 │<──────────────────────────────┤
```

### 3. PAT Validation in Backend

```java
// PatAuthenticationProvider.java
1. Extract Basic Auth header
2. Decode base64 → get email:token
3. Find user by email
4. Hash the token
5. Find UserTokenEntity by hash
6. Verify token not expired
7. Return authenticated user
```

---

## Clone Implementation

### Clone Flow Diagram

```
CLI                                    Backend
 │                                        │
 │ 1. Load credentials                   │
 │                                        │
 │ 2. GET /refs                          │
 ├───────────────────────────────────────>│
 │                                        │ Query BranchHeadRepository
 │ {defaultBranch, branches}              │ Return all branches
 │<───────────────────────────────────────┤
 │                                        │
 │ 3. GET /commits?branch=main           │
 ├───────────────────────────────────────>│
 │                                        │ Start from branch HEAD
 │                                        │ Follow parent chain
 │ {commits: [...], hasMore: false}      │ Return commit list
 │<───────────────────────────────────────┤
 │                                        │
 │ 4. Collect all unique blob hashes     │
 │    from commit.files maps              │
 │                                        │
 │ 5. POST /objects/batch (50 hashes)    │
 ├───────────────────────────────────────>│
 │                                        │ Query BlobRepository
 │                                        │ Load blob content
 │ {objects: {hash: base64}}              │ Base64 encode
 │<───────────────────────────────────────┤
 │                                        │
 │ 6. Repeat batch download until done   │
 │                                        │
 │ 7. Setup local .vcs structure          │
 │    - Create directories                │
 │    - Write blobs to .vcs/objects/      │
 │    - Write commits to .vcs/commits/    │
 │    - Create branch refs                │
 │    - Write HEAD                        │
 │    - Write config                      │
 │    - Write remote config               │
 │                                        │
 │ 8. Checkout latest commit              │
 │    - Extract files from blobs          │
 │    - Write to working directory        │
 │                                        │
```

### Clone Algorithm (Detailed)

```java
// CloneCommand.java - run() method

1. Validate credentials
   - Load from ~/.vcs/credentials.json
   - Match by baseUrl

2. Fetch refs
   response = GET /api/repositories/{repoKey}/refs
   → {defaultBranch: "main", branches: {"main": "hash123"}}

3. Fetch commit history
   response = GET /api/repositories/{repoKey}/commits?branch=main
   → {commits: [...], hasMore: false}

   Commits are returned in reverse chronological order
   (newest first, following parent chain)

4. Collect blob hashes
   Set<String> allBlobHashes = new HashSet<>();
   for (commit : commits) {
     allBlobHashes.addAll(commit.files.values());
   }

5. Batch download blobs
   Map<String, String> allObjects = new HashMap<>();
   int batchSize = 50;

   for (batch : splitInBatches(allBlobHashes, batchSize)) {
     response = POST /api/repositories/{repoKey}/objects/batch
                {hashes: batch}
     allObjects.putAll(response.objects);
   }

6. Setup local repository
   a. Create .vcs structure
      mkdir .vcs
      mkdir .vcs/objects
      mkdir .vcs/commits
      mkdir .vcs/refs/heads

   b. Write blobs
      for (hash, base64Content : allObjects) {
        content = base64Decode(base64Content)
        path = .vcs/objects/{hash[0:2]}/{hash[2:]}
        write(path, content)
      }

   c. Write commits
      for (commit : commits) {
        json = toJson(commit)
        write(.vcs/commits/{commit.id}, json)
      }

   d. Write branch refs
      for (branch, headCommit : refs.branches) {
        write(.vcs/refs/heads/{branch}, headCommit)
      }

   e. Write HEAD
      write(.vcs/HEAD, "ref: refs/heads/{defaultBranch}")

   f. Write config
      write(.vcs/config, """
        [repository]
        default_branch={defaultBranch}
        versioning_mode=project
      """)

   g. Write remote config
      write(.vcs/remote, toJson({
        baseUrl: remoteUrl,
        repoKey: repoKey
      }))

7. Checkout files
   latestCommit = commits[0]
   for (filePath, blobHash : latestCommit.files) {
     content = base64Decode(allObjects[blobHash])
     write(filePath, content)
   }
```

### Backend Clone Services

```java
// CloneService.java

getRefs(repoKey):
  1. Find repository by repoKey
  2. Query BranchHeadRepository.findAllByRepository(repo)
  3. Build map: {branchName → headCommitId}
  4. Return {defaultBranch, branches}

getCommit(repoKey, commitHash):
  1. Find repository
  2. Query CommitRepository.findByRepositoryAndCommitId(repo, hash)
  3. Parse filesJson to Map
  4. Return CommitSnapshotDto

getCommitHistory(repoKey, branch, limit, fromCommit):
  1. Find repository
  2. Get starting commit:
     - If fromCommit: use it
     - Else: get branch HEAD from BranchHeadRepository
  3. Follow parent chain:
     currentCommit = startCommit
     while (currentCommit != null && count < limit):
       commits.add(currentCommit)
       currentCommit = getCommit(currentCommit.parentCommitId)
  4. Return {commits, hasMore}

getBatchObjects(repoKey, hashes):
  1. Find repository
  2. For each hash:
     - Find BlobEntity from BlobRepository
     - Load content from storage (BlobStorageService)
     - Base64 encode
  3. Return {objects: Map<hash, base64Content>}
```

---

## Development Workflow

### Building the Project

```bash
# Backend
cd ChronoVCS
./gradlew build
./gradlew bootRun

# CLI
cd ChronoVCS-CLI
./gradlew build
./gradlew bootRun --args="<command>"

# Or use start script
./start.sh init
```

### Running Tests

```bash
# Backend tests
cd ChronoVCS
./gradlew test

# CLI tests
cd ChronoVCS-CLI
./gradlew test
```

### Database Setup

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chronovcs
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update  # Creates tables automatically
```

### Adding a New Command

1. Create command class in `commands/`
2. Implement `Runnable`
3. Add `@Command` annotation
4. Inject required services
5. Register in `ChronoCommand.subcommands`

```java
@Component
@Command(name = "mycommand", description = "My command")
public class MyCommand implements Runnable {
    @Override
    public void run() {
        // Implementation
    }
}
```

### Adding a New API Endpoint

1. Define DTOs in `dto/`
2. Add service method in appropriate service
3. Add controller endpoint
4. Add corresponding CLI service method
5. Update command to use new endpoint

---

## Troubleshooting

### Common Issues

**1. "Not a ChronoVCS repository"**
- Ensure you're in a directory with `.vcs/` folder
- Run `chronovcs init` first

**2. "No credentials found"**
- Run `chronovcs login` first
- Check `~/.vcs/credentials.json` exists

**3. Clone fails with JSON parsing error**
- Ensure CLI and Backend DTOs match
- Add `@JsonIgnoreProperties(ignoreUnknown = true)`

**4. Push fails with 401**
- Token may be expired
- Re-run `chronovcs login`

**5. Build fails**
- Check Java version (17+)
- Run `./gradlew clean build`

### Debug Logging

```yaml
# application.yml
logging:
  level:
    com.ismile.core.chronovcs: DEBUG
    com.ismile.core.chronovcscli: DEBUG
```

---

## Future Enhancements

### Planned Features
- [ ] Pull command (fetch + merge)
- [ ] Branch management (create, delete, switch)
- [ ] Checkout command (restore working directory)
- [ ] Diff command (compare commits)
- [ ] Merge command
- [ ] Conflict resolution
- [ ] Tags support
- [ ] Partial clone (sparse checkout)
- [ ] Delta compression for network transfer
- [ ] Web UI for repository browsing

### Performance Optimizations
- [ ] Incremental clone (resume interrupted clone)
- [ ] Parallel blob download
- [ ] Blob deduplication
- [ ] Compression for blob storage
- [ ] Caching layer for frequently accessed blobs

---

## Contributing

### Code Style
- Follow Java naming conventions
- Use Lombok for boilerplate reduction
- Add JavaDoc for public APIs
- Write unit tests for new features

### Pull Request Process
1. Create feature branch
2. Implement feature with tests
3. Update documentation
4. Submit PR with description

---

## License

[Specify your license here]

---

## Contact

For questions or issues, contact: [your-email@example.com]
