# Headless Ghidra MCP Server
# Provides AI-assisted reverse engineering via Model Context Protocol
#
# Ghidra is packaged inside this container - no external installation needed.
#
# Build:
#   docker build -t argos .
#
# Run (HTTP mode - direct connection):
#   docker run -p 8080:8080 -v /path/to/binaries:/work argos
#
# Run (stdio mode - for Claude CLI):
#   docker run -i -v /path/to/binaries:/work argos mcp-argos

# ============================================================================
# Builder stage - compile extension
# ============================================================================
FROM eclipse-temurin:21-jdk AS builder

# Install build dependencies
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Gradle 8.10 (required by Ghidra 12.x)
ARG GRADLE_VERSION=8.10
RUN wget -q "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle && \
    rm /tmp/gradle.zip

# Ghidra version configuration
# Update these for newer Ghidra releases
ARG GHIDRA_VERSION=12.0.4
ARG GHIDRA_DATE=20260303
ARG GHIDRA_SHA256=c3b458661d69e26e203d739c0c82d143cc8a4a29d9e571f099c2cf4bda62a120

# Download and verify Ghidra
WORKDIR /opt
RUN echo "Downloading Ghidra ${GHIDRA_VERSION}..." && \
    wget -q "https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_${GHIDRA_VERSION}_build/ghidra_${GHIDRA_VERSION}_PUBLIC_${GHIDRA_DATE}.zip" \
    -O ghidra.zip && \
    echo "${GHIDRA_SHA256}  ghidra.zip" | sha256sum -c - && \
    unzip -q ghidra.zip && \
    mv ghidra_${GHIDRA_VERSION}_PUBLIC ghidra && \
    rm ghidra.zip && \
    echo "Ghidra ${GHIDRA_VERSION} installed successfully"

ENV GHIDRA_INSTALL_DIR=/opt/ghidra

# Copy extension source
WORKDIR /build
COPY java/ java/
COPY build.gradle extension.properties Module.manifest ./

# Build extension using system Gradle
# Increase memory and run without daemon to avoid OOM in Docker
# TieredStopAtLevel=1 prevents JIT crashes under x86_64 emulation on ARM
ENV GRADLE_OPTS="-Xmx2g -Dorg.gradle.daemon=false"
ENV JAVA_TOOL_OPTIONS="-XX:TieredStopAtLevel=1"
RUN echo "Building Argos extension..." && \
    gradle buildExtension && \
    echo "Installing extension..." && \
    mkdir -p /opt/ghidra/Ghidra/Extensions && \
    unzip -q dist/*.zip -d /opt/ghidra/Ghidra/Extensions/ && \
    echo "Extension installed"

# ============================================================================
# Runtime stage - Ghidra + extension + Python CLI (JDK required by PyGhidra)
# ============================================================================
FROM eclipse-temurin:21-jdk

# Install Python and runtime dependencies
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-venv \
    && rm -rf /var/lib/apt/lists/*

# Copy Ghidra with pre-built extension from builder
COPY --from=builder /opt/ghidra /opt/ghidra

# Set up Ghidra environment
ENV GHIDRA_INSTALL_DIR=/opt/ghidra
ENV PATH="${PATH}:/opt/ghidra/support"
# TieredStopAtLevel=1 prevents JIT crashes under x86_64 emulation on ARM
ENV JAVA_TOOL_OPTIONS="-XX:TieredStopAtLevel=1"

# Build classpath from extension JARs for ServiceLoader to find MCP services
RUN echo "Building extension classpath..." && \
    find /opt/ghidra/Ghidra/Extensions -name "*.jar" -print0 | \
    xargs -0 -I {} echo -n "{}:" > /tmp/ext_cp && \
    echo "Extension JARs: $(cat /tmp/ext_cp | tr ':' '\n' | wc -l)"
# Set classpath - will be picked up by PyGhidra/JPype
ENV CLASSPATH_PREFIX="/opt/ghidra/Ghidra/Extensions/build/lib/*"

# Copy Python CLI source
WORKDIR /app
COPY python/ python/
COPY pyproject.toml ./

# Create virtual environment and install dependencies
RUN python3 -m venv /app/venv
ENV PATH="/app/venv/bin:${PATH}"
ENV VIRTUAL_ENV="/app/venv"

# Install uv and Python package with dependencies (pyghidra, mcp, httpx)
RUN pip install uv && uv pip install -e .

# Copy server configuration
COPY config/argos.properties /app/config/

# Create work directory for analyzing binaries
RUN mkdir -p /work
WORKDIR /work

# Expose MCP HTTP port
EXPOSE 8080

# Copy entrypoint script for HTTP mode
COPY entrypoint.py /app/entrypoint.py
RUN chmod +x /app/entrypoint.py

# Default: run HTTP server
# Override with: docker run -i argos mcp-argos (for stdio mode)
CMD ["python", "/app/entrypoint.py"]
