# ChronoVCS User Guide

## 📋 Table of Contents
1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Getting Started](#getting-started)
4. [Basic Workflow](#basic-workflow)
5. [Command Reference](#command-reference)
6. [Configuration](#configuration)
7. [Common Workflows](#common-workflows)
8. [Troubleshooting](#troubleshooting)

---

## Introduction

**ChronoVCS** is a distributed version control system similar to Git, designed for tracking changes in your code projects.

### Key Features
✅ Track file changes over time
✅ Create commits with meaningful messages
✅ Push/clone repositories to/from remote servers
✅ Collaborate with team members
✅ Secure authentication with personal access tokens

---

## Installation

### Prerequisites
- Java 17 or higher
- Internet connection (for remote operations)

### Setup

1. **Clone or download** the ChronoVCS-CLI project

2. **Build the project:**
   ```bash
   cd ChronoVCS-CLI
   ./gradlew build
   ```

3. **Add to PATH** (optional):
   ```bash
   # Add this to your ~/.bashrc or ~/.zshrc
   alias chronovcs='cd /path/to/ChronoVCS-CLI && ./start.sh'
   ```

4. **Verify installation:**
   ```bash
   chronovcs --version
   ```

---

## Getting Started

### First Time Setup

1. **Start the ChronoVCS server** (if you're hosting):
   ```bash
   cd ChronoVCS
   ./gradlew bootRun
   ```
   Server will run on `http://localhost:8080` by default.

2. **Login to server:**
   ```bash
   chronovcs login http://localhost:8080
   ```

   You'll be prompted for:
   - Email
   - Password

   This creates a personal access token stored in `~/.vcs/credentials.json`

3. **You're ready to use ChronoVCS!**

---

## Basic Workflow

### Creating a New Repository

```bash
# 1. Create project folder
mkdir my-project
cd my-project

# 2. Initialize ChronoVCS repository
chronovcs init

# 3. Create some files
echo "Hello World" > hello.txt
echo "# My Project" > README.md

# 4. Add files to staging
chronovcs add hello.txt
chronovcs add README.md

# 5. Check status
chronovcs status

# 6. Commit changes
chronovcs commit "Initial commit"

# 7. Configure remote
chronovcs remote-config http://localhost:8080 my-project

# 8. Push to server
chronovcs push
```

### Cloning an Existing Repository

```bash
# Clone to current directory
chronovcs clone http://localhost:8080 my-project .

# Clone to specific directory
chronovcs clone http://localhost:8080 my-project ./my-project-copy
```

### Making Changes

```bash
# 1. Modify files
echo "More content" >> hello.txt

# 2. Check what changed
chronovcs status

# 3. Stage changes
chronovcs add hello.txt

# 4. Commit
chronovcs commit "Updated hello.txt"

# 5. Push to server
chronovcs push
```

---

## Command Reference

### `chronovcs init`
Initialize a new repository in the current directory.

**Usage:**
```bash
chronovcs init
```

**What it does:**
- Creates `.vcs/` directory
- Sets up internal structure (objects, commits, refs)
- Creates `main` branch by default
- Creates HEAD pointer

**Example:**
```bash
mkdir my-project
cd my-project
chronovcs init
# Output: Initialized empty ChronoVCS repository in .vcs/
```

---

### `chronovcs add`
Add files to the staging area (index).

**Usage:**
```bash
chronovcs add <file>
```

**What it does:**
- Calculates hash of file content
- Stores file content in `.vcs/objects/`
- Adds file to staging index

**Examples:**
```bash
# Add single file
chronovcs add hello.txt

# Add multiple files
chronovcs add file1.txt file2.txt

# Add all files in directory
chronovcs add .
```

---

### `chronovcs status`
Show the status of your working directory and staging area.

**Usage:**
```bash
chronovcs status
```

**What it shows:**
- New files (untracked)
- Modified files
- Staged files (ready to commit)

**Example output:**
```
Staged files:
  hello.txt
  README.md

Modified files:
  app.js

New files:
  config.json
```

---

### `chronovcs commit`
Create a commit from staged changes.

**Usage:**
```bash
chronovcs commit "Your commit message"
```

**What it does:**
- Creates snapshot of all staged files
- Links to parent commit
- Generates unique commit hash
- Updates branch HEAD pointer
- Clears staging area

**Examples:**
```bash
# Basic commit
chronovcs commit "Add new feature"

# Multi-word message
chronovcs commit "Fix bug in login system"
```

**Best Practices:**
- Use clear, descriptive messages
- Start with verb (Add, Fix, Update, Remove)
- Keep it concise (< 72 characters)

---

### `chronovcs login`
Authenticate with a ChronoVCS server.

**Usage:**
```bash
chronovcs login <server-url>
```

**What it does:**
1. Prompts for email and password
2. Authenticates with server
3. Creates Personal Access Token (PAT)
4. Saves credentials to `~/.vcs/credentials.json`

**Example:**
```bash
chronovcs login http://localhost:8080

# Prompts:
Enter email: user@example.com
Enter password: ********

# Output:
Login successful!
Token saved to ~/.vcs/credentials.json
```

**Note:** Credentials are stored securely with restricted file permissions.

---

### `chronovcs remote-config`
Configure the remote server for this repository.

**Usage:**
```bash
chronovcs remote-config <server-url> <repo-key>
```

**What it does:**
- Saves remote configuration to `.vcs/remote`
- Links local repository to remote repository

**Example:**
```bash
chronovcs remote-config http://localhost:8080 my-project

# Output:
Remote configured successfully:
  URL: http://localhost:8080
  Repository: my-project
```

---

### `chronovcs remote-handshake`
Test connection and verify permissions with remote server.

**Usage:**
```bash
chronovcs remote-handshake
```

**What it does:**
- Checks credentials
- Verifies repository access
- Shows your permissions (read/write)

**Example:**
```bash
chronovcs remote-handshake

# Output:
Handshake successful!
User: user@example.com
Repository: my-project
Permissions: READ, WRITE
```

---

### `chronovcs push`
Upload commits and files to remote server.

**Usage:**
```bash
chronovcs push
```

**What it does:**
1. Loads latest local commit
2. Collects all file blobs (content)
3. Sends commit + blobs to server
4. Updates remote branch pointer

**Example:**
```bash
chronovcs push

# Output:
Pushing to http://localhost:8080/chronovcs...
Uploading 3 files...
Push successful!
Remote branch 'main' updated to commit abc123...
```

**Prerequisites:**
- Must be logged in (`chronovcs login`)
- Must have remote configured (`chronovcs remote-config`)
- Must have commits to push

---

### `chronovcs clone`
Download a repository from remote server.

**Usage:**
```bash
chronovcs clone <server-url> <repo-key> [target-directory]
```

**What it does:**
1. Authenticates with server
2. Downloads all commits and branches
3. Downloads all file content (blobs)
4. Sets up local `.vcs/` structure
5. Checks out latest commit to working directory

**Examples:**
```bash
# Clone to current directory
chronovcs clone http://localhost:8080 my-project .

# Clone to new directory
chronovcs clone http://localhost:8080 my-project ./my-copy

# Clone to auto-named directory
chronovcs clone http://localhost:8080 my-project
```

**What gets downloaded:**
- All commits (full history)
- All branches
- All file versions
- Remote configuration

**Example output:**
```bash
chronovcs clone http://localhost:8080 my-project .

Cloning repository 'my-project' from http://localhost:8080...
Fetching repository information...
Default branch: main
Fetching commit history for branch 'main'...
Found 5 commits
Downloading 12 objects...
Downloaded 12/12 objects
Setting up local repository...
Checked out 8 files
Clone completed successfully!
Repository cloned to: /Users/you/my-project
```

---

### `chronovcs --help`
Show help information for ChronoVCS commands.

**Usage:**
```bash
chronovcs --help
chronovcs <command> --help
```

**Examples:**
```bash
# General help
chronovcs --help

# Help for specific command
chronovcs clone --help
```

---

## Configuration

### Credentials File
Location: `~/.vcs/credentials.json`

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

**Security:**
- File has restricted permissions (600)
- Tokens are hashed on server
- Never commit this file to version control

### Repository Config
Location: `.vcs/config`

```ini
[repository]
default_branch=main
versioning_mode=project
```

### Remote Config
Location: `.vcs/remote`

```json
{
  "baseUrl": "http://localhost:8080",
  "repoKey": "my-project"
}
```

### Ignore Files
Location: `.chronoignore` (in project root)

```
# Build outputs
build/
dist/
*.class
*.jar

# Dependencies
node_modules/
vendor/

# IDE files
.idea/
.vscode/

# OS files
.DS_Store
Thumbs.db
```

---

## Common Workflows

### Daily Development

```bash
# Start of day - get latest changes
cd my-project

# Make changes
vim src/app.js

# Check status
chronovcs status

# Stage changes
chronovcs add src/app.js

# Commit
chronovcs commit "Implement new feature"

# Push to server
chronovcs push
```

### Starting a New Feature

```bash
# Initialize repository
mkdir new-feature
cd new-feature
chronovcs init

# Write code
vim feature.js

# Stage and commit
chronovcs add feature.js
chronovcs commit "Initial implementation"

# Configure remote (first time)
chronovcs remote-config http://server.com my-feature

# Push
chronovcs push
```

### Collaborating with Team

**Person A - Initial setup:**
```bash
mkdir shared-project
cd shared-project
chronovcs init
echo "# Project" > README.md
chronovcs add README.md
chronovcs commit "Initial commit"
chronovcs remote-config http://server.com shared-project
chronovcs push
```

**Person B - Clone and contribute:**
```bash
chronovcs clone http://server.com shared-project ./my-copy
cd my-copy

# Make changes
echo "New feature" > feature.txt
chronovcs add feature.txt
chronovcs commit "Add feature"
chronovcs push
```

**Person A - Get updates:**
```bash
# Currently manual process - use clone to get updates
cd ..
chronovcs clone http://server.com shared-project ./updated-copy
```

### Recovering from Mistakes

**Undo uncommitted changes:**
```bash
# Currently not directly supported
# Workaround: Re-clone or manually restore files
```

**View commit history:**
```bash
# Check .vcs/commits/ directory
ls .vcs/commits/

# Read commit details
cat .vcs/commits/<commit-hash>
```

---

## Troubleshooting

### "Not a ChronoVCS repository"

**Problem:** Command fails with this error.

**Solution:**
```bash
# Check if .vcs/ exists
ls -la .vcs

# If not, initialize
chronovcs init

# Or clone from remote
chronovcs clone http://server.com repo-name .
```

---

### "No credentials found"

**Problem:** Remote operations fail with authentication error.

**Solution:**
```bash
# Login first
chronovcs login http://server.com

# Verify credentials file exists
ls -la ~/.vcs/credentials.json
```

---

### Clone fails or downloads nothing

**Problem:** Clone command completes but folder is empty.

**Possible causes:**
1. Wrong server URL
2. Wrong repository key
3. No permission to repository
4. Repository is empty

**Solution:**
```bash
# Test connection
chronovcs login http://server.com

# Verify repository name
# Check with server admin

# Try handshake first
chronovcs remote-config http://server.com repo-name
chronovcs remote-handshake
```

---

### Push fails with 401 Unauthorized

**Problem:** Push command returns authentication error.

**Solution:**
```bash
# Re-login (token may have expired)
chronovcs login http://server.com

# Verify remote config
cat .vcs/remote

# Test handshake
chronovcs remote-handshake
```

---

### Files not being tracked

**Problem:** `chronovcs add` doesn't track some files.

**Possible causes:**
1. Files are in `.chronoignore`
2. Files are in `.vcs/` directory

**Solution:**
```bash
# Check ignore rules
cat .chronoignore

# Remove from ignore if needed
vim .chronoignore

# Try adding again
chronovcs add file.txt
```

---

### Large files slow down operations

**Problem:** Commands are slow with large files.

**Current limitation:** ChronoVCS transfers full file content.

**Workaround:**
- Avoid committing large binary files
- Use `.chronoignore` for build artifacts
- Consider compressing large files

---

### Getting help

**Check command help:**
```bash
chronovcs --help
chronovcs <command> --help
```

**Check logs:**
```bash
# CLI logs are printed to console
# Server logs are in ChronoVCS/logs/ or console output
```

**Debug mode:**
```bash
# Set logging level in application.yml
logging:
  level:
    com.ismile.core.chronovcscli: DEBUG
```

---

## Tips & Best Practices

### Commit Messages
✅ **Good:**
- "Add user authentication"
- "Fix login validation bug"
- "Update database schema"

❌ **Bad:**
- "changes"
- "update"
- "asdfasdf"

### When to Commit
- After completing a logical unit of work
- Before switching tasks
- At end of day
- Before pushing to server

### What to Ignore
Add to `.chronoignore`:
- Build outputs (build/, dist/, target/)
- Dependencies (node_modules/, vendor/)
- IDE files (.idea/, .vscode/)
- OS files (.DS_Store)
- Temporary files (*.tmp, *.log)
- Sensitive data (.env, secrets.json)

### Repository Organization
```
my-project/
├── .vcs/              # ChronoVCS data (don't modify manually)
├── .chronoignore      # Ignore rules
├── src/               # Source code
├── tests/             # Tests
├── docs/              # Documentation
└── README.md          # Project info
```

---

## Keyboard Shortcuts

ChronoVCS CLI uses standard terminal input - no special shortcuts.

**Terminal tips:**
```bash
# Repeat last command
!!

# Search command history
Ctrl + R

# Clear screen
Ctrl + L

# Cancel current input
Ctrl + C
```

---

## FAQ

**Q: Is ChronoVCS compatible with Git?**
A: No, ChronoVCS uses a different storage format and protocol. You cannot use Git commands with ChronoVCS repositories.

**Q: Can I use ChronoVCS offline?**
A: Yes! Local operations (init, add, commit, status) work offline. Only push/clone require server connection.

**Q: How do I delete a repository?**
A: Simply delete the `.vcs/` directory or the entire project folder.

**Q: Can I have multiple branches?**
A: Currently, ChronoVCS supports a single branch (main). Branch management is planned for future releases.

**Q: How do I undo a commit?**
A: There's no built-in undo yet. You can manually edit `.vcs/refs/heads/main` to point to a previous commit hash (advanced users only).

**Q: What happens if push fails midway?**
A: The server transaction should rollback. You can retry the push command.

**Q: Can I rename files?**
A: Yes, but it's treated as delete + add. Rename the file, then `chronovcs add` the new name.

**Q: Where is the server hosted?**
A: You host the ChronoVCS server yourself (Spring Boot application). See DEVELOPER_GUIDE.md for setup.

---

## Quick Reference Card

```
┌─────────────────────────────────────────────────────┐
│              ChronoVCS Quick Reference              │
├─────────────────────────────────────────────────────┤
│ Setup                                               │
│  chronovcs init              Initialize repository  │
│  chronovcs login <url>       Authenticate          │
│                                                     │
│ Basic Workflow                                      │
│  chronovcs add <file>        Stage file            │
│  chronovcs status            Check status          │
│  chronovcs commit "msg"      Create commit         │
│                                                     │
│ Remote Operations                                   │
│  chronovcs remote-config <url> <key>  Configure    │
│  chronovcs push              Upload changes        │
│  chronovcs clone <url> <key> <dir>  Download repo  │
│                                                     │
│ Help                                                │
│  chronovcs --help            Show help             │
│  chronovcs <cmd> --help      Command help          │
└─────────────────────────────────────────────────────┘
```

---

## Next Steps

Now that you understand the basics:

1. **Try the Getting Started tutorial** above
2. **Practice with a test project**
3. **Read DEVELOPER_GUIDE.md** if you want to extend ChronoVCS
4. **Share feedback** with the development team

---

## Support

For issues or questions:
- Check this guide and DEVELOPER_GUIDE.md
- Review error messages carefully
- Check server logs if using self-hosted server

---

**Happy versioning with ChronoVCS! 🚀**
