# Argos - AI-powered binary analysis
# Copyright (c) 2024-2025

"""
Java Argos launcher wrapper for Python CLI.

Handles PyGhidra initialization, Argos server startup, and project management.
"""

import sys
from typing import Optional
from pathlib import Path


class ArgosLauncher:
    """Wraps Argos headless launcher with Python-side project management.

    Note: Stdio mode uses ephemeral projects in temp directories.
    Projects are created per-session and cleaned up on exit.
    """

    def __init__(self, config_file: Optional[Path] = None, use_random_port: bool = True):
        """
        Initialize Argos launcher.

        Args:
            config_file: Optional configuration file path
            use_random_port: Whether to use random available port (default: True)
        """
        self.config_file = config_file
        self.use_random_port = use_random_port
        self.java_launcher = None
        self.port = None
        self.temp_project_dir = None

    def start(self) -> int:
        """
        Start Argos headless server.

        Returns:
            Server port number

        Raises:
            RuntimeError: If server fails to start
        """
        try:
            # Import Argos launcher (PyGhidra already initialized by CLI)
            from argos.headless import ArgosHeadlessLauncher
            from java.io import File
            from .project_manager import ProjectManager
            import tempfile

            # Stdio mode: ephemeral projects in temp directory (session-scoped, auto-cleanup)
            # Keeps working directory clean - no .argos creation in cwd
            self.temp_project_dir = Path(tempfile.mkdtemp(prefix="argos_project_"))
            project_manager = ProjectManager()
            project_name = project_manager.get_project_name()

            # Use temp directory for the project (not .argos/projects)
            projects_dir = self.temp_project_dir

            # Convert to Java File objects
            java_project_location = File(str(projects_dir))

            print(f"Project location: {projects_dir}/{project_name}", file=sys.stderr)

            # Create launcher with project parameters
            if self.config_file:
                print(f"Using config file: {self.config_file}", file=sys.stderr)
                java_config_file = File(str(self.config_file))
                self.java_launcher = ArgosHeadlessLauncher(
                    java_config_file,
                    self.use_random_port,
                    java_project_location,
                    project_name
                )
            else:
                print("Using default configuration", file=sys.stderr)
                # Use constructor with project parameters
                self.java_launcher = ArgosHeadlessLauncher(
                    None,
                    True,  # autoInitializeGhidra
                    self.use_random_port,
                    java_project_location,
                    project_name
                )

            # Start server
            print("Starting Argos MCP server...", file=sys.stderr)
            self.java_launcher.start()

            # Wait for server to be ready
            if self.java_launcher.waitForServer(30000):
                self.port = self.java_launcher.getPort()
                print(f"Argos server ready on port {self.port}", file=sys.stderr)
                return self.port
            else:
                raise RuntimeError("Server failed to start within timeout")

        except Exception as e:
            print(f"Error starting Argos server: {e}", file=sys.stderr)
            import traceback
            traceback.print_exc(file=sys.stderr)
            raise

    def get_port(self) -> Optional[int]:
        """
        Get the server port.

        Returns:
            Server port number, or None if not started
        """
        return self.port

    def is_running(self) -> bool:
        """
        Check if server is running.

        Returns:
            True if server is running
        """
        if self.java_launcher:
            return self.java_launcher.isRunning()
        return False

    def stop(self):
        """Stop the Argos server and cleanup."""
        if self.java_launcher:
            print("Stopping Argos server...", file=sys.stderr)
            try:
                self.java_launcher.stop()
            except Exception as e:
                print(f"Error stopping server: {e}", file=sys.stderr)
            finally:
                self.java_launcher = None
                self.port = None

        # Clean up temporary project directory
        if self.temp_project_dir and self.temp_project_dir.exists():
            try:
                import shutil
                shutil.rmtree(self.temp_project_dir)
                print(f"Cleaned up temporary project directory: {self.temp_project_dir}", file=sys.stderr)
            except Exception as e:
                print(f"Error cleaning up temporary directory: {e}", file=sys.stderr)
            finally:
                self.temp_project_dir = None
