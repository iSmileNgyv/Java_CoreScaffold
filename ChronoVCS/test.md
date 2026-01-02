# ChronoVCS System Test Plan (CLI + Database)

This guide covers API, CLI, and database verification for ChronoVCS and ChronoVCS-CLI.

## 0. Test data and placeholders

Use these placeholders and replace values as you go:

- SERVER_URL: http://localhost:8081
- USER_EMAIL: tester@example.com
- USER_PASSWORD: Passw0rd!
- TOKEN_NAME: cli-token
- REPO_NAME: Test ChronoVCS
- REPO_KEY: (from create repo response)
- LOCAL_DIR: /tmp/chronovcs-local
- CLONE_DIR: /tmp/chronovcs-clone

Record these IDs from outputs:

- ACCESS_TOKEN, REFRESH_TOKEN (from /api/auth/login)
- PAT_TOKEN (from /api/auth/tokens)
- COMMIT_1, COMMIT_2, COMMIT_FEATURE, COMMIT_MAIN_CONFLICT (from CLI output)

## 1. Start services

1) Start PostgreSQL with the credentials in `ChronoVCS/src/main/resources/application.yml`.

2) Start the server:

```bash
cd /Users/ismile/IdeaProjects/Java_CoreScaffold/ChronoVCS
./gradlew bootRun
```

3) Confirm health:

```bash
curl http://localhost:8081/actuator/health
```

## 2. Build and run CLI

Option A (install alias using start.sh):

```bash
cd /Users/ismile/IdeaProjects/Java_CoreScaffold/ChronoVCS-CLI
./start.sh
```

Option B (run directly without installing):

```bash
cd /Users/ismile/IdeaProjects/Java_CoreScaffold/ChronoVCS-CLI
./gradlew clean build -x test
java -jar build/libs/ChronoVCS-CLI-0.0.1-SNAPSHOT.jar --help
```

In the rest of this document, replace `chronovcs` with your chosen command.

## 3. Auth and tokens (API)

### 3.1 Register user

```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "tester@example.com",
    "password": "Passw0rd!",
    "displayName": "Test User"
  }'
```

### 3.2 Login and get JWT

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "tester@example.com",
    "password": "Passw0rd!"
  }'
```

Save ACCESS_TOKEN and REFRESH_TOKEN.

### 3.3 Create PAT for CLI

```bash
curl -X POST http://localhost:8081/api/auth/tokens \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "tokenName": "cli-token",
    "expiresInDays": 30
  }'
```

Save PAT_TOKEN (rawToken) and tokenPrefix.

### 3.4 CLI login

```bash
chronovcs login --server http://localhost:8081 --email tester@example.com
```

Enter PAT_TOKEN when prompted.

Database check:
- Table `chronovcs_users`: row with email = tester@example.com, display_name = Test User, is_active = true.
- Table `chronovcs_refresh_tokens`: row for the user with token_hash set, revoked_at = null.
- Table `chronovcs_user_tokens`: row with token_name = cli-token, token_prefix = first 10 chars of PAT_TOKEN, is_revoked = false.

## 4. Create repository (API)

```bash
curl -X POST http://localhost:8081/api/repositories \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test ChronoVCS",
    "description": "Repo for system tests",
    "versioningMode": "project",
    "privateRepo": false,
    "defaultBranch": "main",
    "releaseEnabled": false
  }'
```

Save REPO_KEY from the response.

Database check:
- Table `chronovcs_repositories`: row with repo_key = REPO_KEY, name = Test ChronoVCS, default_branch = main, versioning_mode = PROJECT, is_private = false, owner_id = user id.
- Table `chronovcs_repository_settings`: row with repository_id pointing to the repo, release_enabled = false.

## 5. Local repo init and first commit (CLI)

```bash
mkdir -p /tmp/chronovcs-local
cd /tmp/chronovcs-local
chronovcs init
```

Create files:

```bash
mkdir -p src
cat <<'EOT' > README.md
# ChronoVCS Test Repository

Initial content.
EOT

cat <<'EOT' > src/main.js
console.log("hello world");
EOT
```

Stage and commit:

```bash
chronovcs add README.md
chronovcs add src/main.js
chronovcs status
chronovcs diff --staged
chronovcs commit -m "Initial commit"
```

Record COMMIT_1.

Database check:
- No server DB changes yet (local only).

## 6. Configure remote and handshake (CLI)

```bash
chronovcs remote-config --url http://localhost:8081 --repo REPO_KEY
chronovcs remote-handshake
```

Expected: handshake shows repo key, default branch, and versioning mode.

## 7. Push initial commit (CLI)

```bash
chronovcs push
```

Database check:
- Table `chronovcs_branch_heads`: row with branch = main, head_commit_id = COMMIT_1.
- Table `chronovcs_commits`: row with commit_id = COMMIT_1, branch = main, parent_commit_id = null, message = Initial commit, files_json includes README.md and src/main.js.
- Table `chronovcs_blobs`: 2 rows with hash values matching the files; storage_type = LOCAL; storage_path starts with REPO_KEY/.

## 8. Second commit on main (CLI)

Modify and add a file:

```bash
cat <<'EOT' > src/utils.js
export function helper() {
  return "helper";
}
EOT

cat <<'EOT' > src/main.js
console.log("hello world v2");
EOT

chronovcs add src/utils.js
chronovcs add src/main.js
chronovcs commit -m "Update main.js and add utils.js"
chronovcs push
```

Record COMMIT_2.

Database check:
- Table `chronovcs_commits`: new row with commit_id = COMMIT_2, parent_commit_id = COMMIT_1, branch = main, message matches.
- Table `chronovcs_branch_heads`: main head_commit_id = COMMIT_2.
- Table `chronovcs_blobs`: count increased (new blob for utils.js, updated blob for main.js).

## 9. Branch management (CLI + API)

### 9.1 Local branch create and push

```bash
chronovcs branch feature/login
chronovcs checkout feature/login
```

Create a new file and commit:

```bash
cat <<'EOT' > src/login.js
export function login(u, p) {
  return !!(u && p);
}
EOT

chronovcs add src/login.js
chronovcs commit -m "Add login"
chronovcs push
```

Record COMMIT_FEATURE.

Database check:
- Table `chronovcs_branch_heads`: row with branch = feature/login, head_commit_id = COMMIT_FEATURE.
- Table `chronovcs_commits`: row with commit_id = COMMIT_FEATURE, branch = feature/login, parent_commit_id = COMMIT_2.

### 9.2 API branch list and branch info

```bash
curl -X GET http://localhost:8081/api/repositories/REPO_KEY/branches \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"

curl -X GET http://localhost:8081/api/repositories/REPO_KEY/branches/feature/login \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected: branch list includes main and feature/login, commitsAhead/Behind values reflect one commit ahead for feature/login.

## 10. Merge analysis and merge (API)

### 10.1 Fast-forward analysis

```bash
curl -X GET "http://localhost:8081/api/repositories/REPO_KEY/branches/merge/analyze?sourceBranch=feature/login&targetBranch=main" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected: canAutoMerge = true, canFastForward = true, conflicts = empty.

### 10.2 Fast-forward merge

```bash
curl -X POST http://localhost:8081/api/repositories/REPO_KEY/branches/merge \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceBranch": "feature/login",
    "targetBranch": "main",
    "strategy": "fast-forward",
    "message": "Merge feature/login into main"
  }'
```

Database check:
- Table `chronovcs_branch_heads`: main head_commit_id = COMMIT_FEATURE.

## 11. Conflict test (API + CLI merge resolution)

### 11.1 Create conflict commits

Switch back to main and create a change:

```bash
chronovcs checkout main
cat <<'EOT' > src/utils.js
export function helper() {
  return "main branch";
}
EOT
chronovcs add src/utils.js
chronovcs commit -m "Main changes utils"
chronovcs push
```

Record COMMIT_MAIN_CONFLICT.

Switch to feature/login and create a conflicting change:

```bash
chronovcs checkout feature/login
cat <<'EOT' > src/utils.js
export function helper() {
  return "feature branch";
}
EOT
chronovcs add src/utils.js
chronovcs commit -m "Feature changes utils"
chronovcs push
```

### 11.2 Analyze conflict

```bash
curl -X GET "http://localhost:8081/api/repositories/REPO_KEY/branches/merge/analyze?sourceBranch=feature/login&targetBranch=main" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected: canAutoMerge = false, conflicts includes src/utils.js with MODIFIED_MODIFIED.

### 11.3 CLI merge resolution via pull

Create a second working copy to simulate divergence:

```bash
chronovcs clone http://localhost:8081 REPO_KEY /tmp/chronovcs-clone
```

In /tmp/chronovcs-clone, create a local change and commit without pushing:

```bash
cd /tmp/chronovcs-clone
cat <<'EOT' > src/utils.js
export function helper() {
  return "local clone change";
}
EOT
chronovcs add src/utils.js
chronovcs commit -m "Local diverged change"
```

Now pull to trigger merge:

```bash
chronovcs pull
chronovcs merge
```

Expected: merge in progress and conflicted file listed. Resolve file, add, and continue:

```bash
# resolve src/utils.js manually
chronovcs add src/utils.js
chronovcs merge --continue
```

Database check:
- Table `chronovcs_commits`: new merge commit on main after the merge is pushed (if you push from the clone).
- Table `chronovcs_branch_heads`: main head_commit_id updated to merge commit after push.

## 12. Diff and compare (API)

```bash
curl -X GET "http://localhost:8081/api/repositories/REPO_KEY/compare/main...feature/login" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"

curl -X GET "http://localhost:8081/api/repositories/REPO_KEY/compare/main...feature/login?patch=true" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected: files array lists src/utils.js with changeType = MODIFIED; patch is present when patch=true.

Commit diff:

```bash
curl -X GET "http://localhost:8081/api/repositories/REPO_KEY/commits/COMMIT_2/diff?patch=true" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

## 13. File history and blame (API)

File history:

```bash
curl -X GET "http://localhost:8081/api/repositories/REPO_KEY/files/src/utils.js/history?branch=main&limit=10" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Blame:

```bash
curl -X GET "http://localhost:8081/api/repositories/REPO_KEY/files/src/main.js/blame?commit=main" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected: history lists commits newest to oldest; blame lines include commitId and content.

## 14. CLI status, diff, log

```bash
chronovcs status
chronovcs diff
chronovcs diff --staged
chronovcs log
chronovcs log --oneline
chronovcs log --stat
chronovcs log --graph
```

Expected: output includes current branch, list of modified/untracked files, and commit history.

## 15. Repository settings (API)

Enable releases:

```bash
curl -X PUT http://localhost:8081/api/repositories/REPO_KEY/settings \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "releaseEnabled": true,
    "taskRequired": false,
    "autoIncrement": true,
    "enforceSemanticVersioning": true
  }'
```

Database check:
- Table `chronovcs_repository_settings`: release_enabled = true for the repo.

## 16. Releases (API + CLI)

### 16.1 Create release

```bash
curl -X POST http://localhost:8081/api/repositories/REPO_KEY/releases \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "versionType": "MINOR",
    "message": "First release",
    "jiraIssueKeys": ["PROJ-101"]
  }'
```

### 16.2 Get releases

```bash
curl -X GET http://localhost:8081/api/repositories/REPO_KEY/releases \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"

curl -X GET http://localhost:8081/api/repositories/REPO_KEY/releases/latest \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Database check:
- Table `chronovcs_releases`: row with version = (auto-incremented), snapshot_commit_id = latest main commit, created_by = user.
- Table `chronovcs_release_tasks`: row with jira_issue_key = PROJ-101 linked to the release.

### 16.3 CLI pull and revert releases

```bash
chronovcs pull --release latest
chronovcs revert --release latest
```

Expected: working tree checked out to release snapshot commit.

## 17. Clone (CLI)

```bash
chronovcs clone http://localhost:8081 REPO_KEY /tmp/chronovcs-clone
```

Expected: .vcs folder created, files checked out, and default branch set.

## 18. Task integration (API)

### 18.1 Create integration (GENERIC)

```bash
curl -X POST http://localhost:8081/api/v1/task-integrations \
  -H "Content-Type: application/json" \
  -d '{
    "type": "GENERIC",
    "name": "generic-test",
    "enabled": true,
    "apiUrl": "http://localhost:9000/tasks",
    "httpMethod": "POST",
    "headers": [{"key": "Authorization", "value": "Bearer test"}],
    "request": {"bodyTemplate": "{\"ids\": {{ids}} }"},
    "response": {
      "taskIdPath": "$.items[*].id",
      "taskTypePath": "$.items[*].type",
      "taskTitlePath": "$.items[*].title",
      "taskDescriptionPath": "$.items[*].description"
    },
    "versionMappings": [
      {"fieldName": "type", "fieldValue": "Bug", "versionType": "PATCH"}
    ]
  }'
```

Database check:
- Table `chronovcs_task_integration`: row with name = generic-test, type = GENERIC, enabled = true, api_url set.
- Table `chronovcs_task_integration_headers`: row with header_name = Authorization and header_value = Bearer test.
- Table `chronovcs_task_integration_request`: row with body_template matching the request.
- Table `chronovcs_task_integration_response`: row with task_id_path, task_type_path, task_title_path, task_description_path.
- Table `chronovcs_version_mapping`: row with field_name = type, field_value = Bug, version_type = PATCH.

### 18.2 Fetch tasks (requires a reachable API)

```bash
curl -X POST http://localhost:8081/api/v1/task-integrations/{id}/fetch-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskIds": ["PROJ-123", "PROJ-456"]
  }'
```

Database check:
- Table `chronovcs_tasks`: rows with external_id = PROJ-123 and PROJ-456, task_integration_id = {id}, version_type mapped from response.

## 19. Edge cases (API)

- Non-existent branch:

```bash
curl -X GET http://localhost:8081/api/repositories/REPO_KEY/branches/does-not-exist \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected: 404 or 400 with error message.

- Non-existent file history:

```bash
curl -X GET http://localhost:8081/api/repositories/REPO_KEY/files/none.js/history \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected: totalCommits = 0, exists = false.

## 20. Cleanup (optional)

- Delete test repo from DB if needed. Remove local test folders in /tmp.

