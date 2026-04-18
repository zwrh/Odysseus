# Argos

AI-powered binary analysis through the Model Context Protocol (MCP). Argos provides a complete reverse engineering toolkit that AI assistants can use to analyze executables, malware, and firmware.

**Everything runs in Docker** - Ghidra, analysis tools, and the MCP server are fully containerized.

## Quick Start

```bash
# Build
docker build -t argos .

# Run (mount your binaries directory)
docker run -p 8080:8080 -v $(pwd):/work argos

# Add to Claude
claude mcp add --transport http argos http://localhost:8080/mcp/message
```

Your current directory is mounted to `/work` inside the container. All file paths in tool calls are relative to this mount.

## Features

- **70+ Analysis Tools**: Decompilation, disassembly, cross-references, data flow, call graphs, strings, symbols, memory inspection, and more
- **Automatic Analysis**: Binaries are auto-analyzed on import
- **HTTP & stdio Modes**: Direct HTTP connection or Claude CLI integration
- **Persistent Sessions**: Analysis results persist for the container lifetime

## Filesystem

Argos runs in an isolated Docker container with its own filesystem:

```
/work/                  ← Your mounted directory (e.g., $(pwd))
  ├── binary.exe        ← Files you want to analyze
  ├── firmware.bin
  └── samples/
      └── malware.dll

/tmp/ghidra-project/    ← Ephemeral analysis data (auto-created)
```

When using tools, reference files as they appear in `/work`:
- `import-file` with path `binary.exe` or `/work/binary.exe`
- Imported programs appear in the project as `/binary.exe`

## Available Tools

| Category | Tools |
|----------|-------|
| **Import** | `import-file`, `list-project-files`, `analyze-program` |
| **Functions** | `get-functions`, `get-function-count`, `create-function`, `set-function-prototype` |
| **Decompilation** | `get-decompilation`, `search-decompilation`, `rename-variables`, `set-decompilation-comment` |
| **Symbols** | `get-symbols`, `get-symbols-count`, `create-label` |
| **Strings** | `get-strings`, `get-strings-count` |
| **Data Types** | `get-data-types`, `get-data-type-by-string`, `apply-data-type`, `parse-c-structure` |
| **Cross-References** | `find-cross-references`, `get-callers-decompiled`, `get-referencers-decompiled` |
| **Memory** | `read-memory`, `get-memory-blocks` |
| **Call Graph** | `get-call-graph`, `get-call-tree`, `find-common-callers` |
| **Data Flow** | `trace-data-flow-forward`, `trace-data-flow-backward`, `find-variable-accesses` |
| **Imports/Exports** | `list-imports`, `list-exports`, `find-import-references` |
| **Comments** | `set-comment`, `get-comments`, `search-comments` |
| **Bookmarks** | `set-bookmark`, `get-bookmarks`, `search-bookmarks` |

## Configuration

Edit `config/argos.properties`:

```properties
argos.server.options.server.port=8080
argos.server.options.server.host=0.0.0.0
argos.server.options.decompiler.timeout.seconds=10
argos.server.options.import.analysis.timeout.seconds=600
```

## Project Structure

```
argos/
├── java/argos/           # Java extension
│   ├── tools/            # Tool providers
│   ├── server/           # MCP server
│   └── headless/         # Headless launcher
├── python/argos_cli/     # Python CLI (stdio mode)
├── config/               # Configuration
├── Dockerfile
└── entrypoint.py
```

## License

Apache License 2.0
