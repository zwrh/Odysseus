# Odysseus
Autonomous binary vulnerability research workflow with dynamic and static capabilities

---

## Argos

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
