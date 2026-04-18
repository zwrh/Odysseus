#!/usr/bin/env python3
# Argos - AI-powered binary analysis
# Copyright (c) 2024-2025

"""
Headless Ghidra MCP Server entrypoint.
Runs Java directly with full classpath instead of through PyGhidra.
"""

import subprocess
import sys
import os
import glob
import signal

def main():
    ghidra_dir = "/opt/ghidra"
    ext_dir = f"{ghidra_dir}/Ghidra/Extensions"

    # Build classpath from Ghidra core + extensions
    classpath = []

    # Ghidra core JARs
    for pattern in [
        f"{ghidra_dir}/Ghidra/Framework/*/lib/*.jar",
        f"{ghidra_dir}/Ghidra/Features/*/lib/*.jar",
        f"{ghidra_dir}/Ghidra/Processors/*/lib/*.jar",
        f"{ghidra_dir}/Ghidra/patch",
    ]:
        classpath.extend(glob.glob(pattern))

    # Extension JARs
    ext_jars = glob.glob(f"{ext_dir}/**/lib/*.jar", recursive=True)
    classpath.extend(ext_jars)

    print(f"Classpath: {len(classpath)} entries", file=sys.stderr)
    print(f"Extension JARs: {len(ext_jars)}", file=sys.stderr)
    for jar in ext_jars:
        print(f"  {os.path.basename(jar)}", file=sys.stderr)

    classpath_str = ":".join(classpath)

    # Java command
    config_path = "/app/config/argos.properties"
    project_dir = "/tmp/ghidra-project"
    project_name = "session"

    # Ensure project directory exists
    os.makedirs(project_dir, exist_ok=True)

    # Change to /work so relative paths work for binaries
    os.chdir("/work")

    cmd = [
        "java",
        "-cp", classpath_str,
        "-Dghidra.install.dir=" + ghidra_dir,
        "argos.headless.ArgosHeadlessLauncher",
        config_path if os.path.exists(config_path) else "",
        project_dir,
        project_name
    ]

    print(f"Starting Argos MCP server...", file=sys.stderr)
    print(f"Working directory: /work (binaries go here)", file=sys.stderr)
    print(f"Project: {project_dir}/{project_name} (ephemeral)", file=sys.stderr)

    # Run Java process from /work directory
    proc = subprocess.Popen(cmd, stdout=sys.stdout, stderr=sys.stderr, cwd="/work")

    def shutdown(sig, frame):
        print("\nShutting down...", file=sys.stderr)
        proc.terminate()
        proc.wait(timeout=10)
        sys.exit(0)

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    proc.wait()
    sys.exit(proc.returncode)

if __name__ == "__main__":
    main()
