# Odysseus
Autonomous binary vulnerability research workflow with dynamic and static capabilities

---

## Argos (Static Analysis)

```
                    __
               (___()'`;
               /,    /`
               \\"--\\

         "He knew his master after twenty years."
```

In Homer's Odyssey, **Argos** was the faithful hound of Odysseus. For twenty years he waited for his master's return from the Trojan War. When Odysseus finally came home disguised as a beggar, Argos alone recognized him. Though too old and weak to rise, he wagged his tail and lowered his ears at the sight of his master one last time.

**Argos** is our AI-powered binary analysis companion—a Ghidra extension that exposes reverse engineering capabilities through the Model Context Protocol (MCP). Like its namesake, Argos watches patiently, recognizes patterns others miss, and remains a faithful assistant in the journey through unknown binaries.

### Features

- Decompilation and disassembly with cross-reference analysis
- Function discovery, renaming, and prototype editing
- Structure definition and memory layout analysis
- Call graph traversal and data flow tracking
- Symbol and string search capabilities
- Docker container for headless operation

### Quick Start

```bash
# Build and run the Docker container
cd argos
docker build -t argos .
docker run -p 8080:8080 -v /path/to/binaries:/work argos
```

See [argos/README.md](argos/README.md) for detailed documentation.

---

## MCP-GDB (Dynamic Analysis)

**MCP-GDB** provides GDB debugging functionality through the Model Context Protocol, enabling AI-assisted dynamic analysis and debugging sessions.

### Features

- Start and manage GDB debugging sessions
- Load programs and core dumps for analysis
- Set breakpoints, step through code, and examine memory
- View call stacks, variables, and registers
- Execute arbitrary GDB commands

### Quick Start

```bash
cd mcp-gdb
npm install
npm run build
```

See [mcp-gdb/README.md](mcp-gdb/README.md) for detailed documentation.

---

## MCP Configuration

To use both tools with Claude, add them to your MCP configuration:

### Claude Code

```bash
# Add Argos (Ghidra)
claude mcp add argos -s user -- docker run -i --rm -p 8080:8080 -v /path/to/binaries:/work argos --mcp

# Add GDB
claude mcp add gdb -- npx -y mcp-gdb
```

### Claude Desktop

Add the following to your Claude Desktop MCP configuration (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "argos": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "-p", "8080:8080", "-v", "/path/to/binaries:/work", "argos", "--mcp"]
    },
    "gdb": {
      "command": "npx",
      "args": ["-y", "mcp-gdb"]
    }
  }
}
```

---

## Workflow

With both tools configured, you can perform comprehensive binary analysis:

1. **Static Analysis (Argos)**: Import binaries into Ghidra, decompile functions, analyze control flow, identify interesting targets
2. **Dynamic Analysis (MCP-GDB)**: Debug the binary, set breakpoints at identified locations, examine runtime behavior, validate hypotheses
