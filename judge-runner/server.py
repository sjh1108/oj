#!/usr/bin/env python3
"""Judge0-compatible execution service for arm64 boxes.

Judge0's official images are amd64-only (every published tag, 1.11 through
1.13), so the Oracle Ampere box cannot run them. The app, however, only ever
calls two Judge0 endpoints — see Judge0Client — so this reimplements exactly
that slice and nothing else:

    POST /submissions?base64_encoded=true&wait=true
    GET  /languages          (the app uses it only as a liveness probe)

Point JUDGE0_URL at this service and the app is unchanged.

Isolation: every submission runs in its own throwaway container started from
the sandbox image, with no network, all capabilities dropped, a read-only
rootfs, an unprivileged user, and cgroup caps on memory and pids. This process
itself never executes contestant code — it only writes files and calls
`docker run`. See README.md for the security model and its limits.
"""

import base64
import json
import os
import shutil
import subprocess
import sys
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

# ── configuration (all overridable from the environment) ────────────────────
LISTEN_PORT = int(os.environ.get("JUDGE_PORT", "2358"))
SANDBOX_IMAGE = os.environ.get("JUDGE_SANDBOX_IMAGE", "ghcr.io/sjh1108/oj-judge-sandbox:latest")
# Host-side path of the scratch directory. This process talks to the host's
# docker daemon, so a bind mount must name a path as the HOST sees it — the
# compose file mounts the same path here under the same name.
WORK_DIR = os.environ.get("JUDGE_WORK_DIR", "/opt/algoj/judge-work")
# How many submissions may execute at once. The box has 2 cores and the app's
# listener concurrency is 2 by default (JUDGE_WORKER_CONCURRENCY).
MAX_PARALLEL = int(os.environ.get("JUDGE_MAX_PARALLEL", "2"))
COMPILE_TIMEOUT = int(os.environ.get("JUDGE_COMPILE_TIMEOUT", "20"))
# A memory cap below this starves the interpreter itself rather than the
# solution, so a too-small problem limit is raised to it.
MIN_MEMORY_KB = int(os.environ.get("JUDGE_MIN_MEMORY_KB", str(64 * 1024)))
PIDS_LIMIT = os.environ.get("JUDGE_PIDS_LIMIT", "128")

# Judge0 language ids, mirroring Submission.Language. Only the ids matter to the
# app; the names are for humans reading /languages.
LANGUAGES = [
    {"id": 50, "name": "C (GCC 13)"},
    {"id": 54, "name": "C++ (G++ 13)"},
    {"id": 62, "name": "Java (OpenJDK 21)"},
    {"id": 63, "name": "JavaScript (Node.js 18)"},
    {"id": 71, "name": "Python 3 (CPython 3.12)"},
    {"id": 200, "name": "PyPy 3"},
]
SUPPORTED_IDS = {lang["id"] for lang in LANGUAGES}

# Judge0 status ids — the app maps these in Judge0StatusMapper, so they are a
# contract, not a detail.
ST_ACCEPTED = (3, "Accepted")
ST_WRONG_ANSWER = (4, "Wrong Answer")
ST_TIME_LIMIT = (5, "Time Limit Exceeded")
ST_COMPILE_ERROR = (6, "Compilation Error")
ST_SIGSEGV = (7, "Runtime Error (SIGSEGV)")
ST_SIGXFSZ = (8, "Runtime Error (SIGXFSZ)")
ST_SIGFPE = (9, "Runtime Error (SIGFPE)")
ST_SIGABRT = (10, "Runtime Error (SIGABRT)")
ST_NZEC = (11, "Runtime Error (NZEC)")
ST_RUNTIME_OTHER = (12, "Runtime Error (Other)")
ST_INTERNAL = (13, "Internal Error")

_slots = threading.Semaphore(MAX_PARALLEL)


def log(msg):
    print(msg, file=sys.stderr, flush=True)


# ── output comparison ──────────────────────────────────────────────────────
def normalize_output(text):
    """Trailing whitespace never decides a verdict.

    Judge0 compares stdout to expected_output ignoring trailing whitespace, and
    every problem's stored answer was written under that assumption — a stricter
    comparison here would fail submissions the old box accepted.
    """
    if text is None:
        return ""
    lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    return "\n".join(line.rstrip() for line in lines).rstrip("\n")


# ── sandbox execution ──────────────────────────────────────────────────────
def read_file(path, limit):
    try:
        with open(path, "rb") as f:
            return f.read(limit)
    except OSError:
        return b""


def parse_meta(path):
    meta = {}
    for line in read_file(path, 4096).decode("utf-8", "replace").splitlines():
        key, _, value = line.partition("=")
        if key:
            meta[key.strip()] = value.strip()
    return meta


def docker_run(token, language_id, cpu_limit, memory_kb, fsize_kb, host_dir):
    """Start the sandbox container and wait for it. Returns (exit_code, oom_killed)."""
    name = "judge-" + token
    memory_mb = max(memory_kb, MIN_MEMORY_KB) // 1024
    # The JVM needs headroom above its heap for metaspace, code cache and
    # thread stacks; without this the cgroup kills it before the solution has a
    # chance to exceed anything.
    jvm_heap_mb = max(64, memory_mb - 96)
    wall_limit = max(5, int(cpu_limit * 2) + 5)

    cmd = [
        "docker", "run",
        "--name", name,
        "--network", "none",
        "--memory", f"{memory_mb}m",
        # Equal to --memory means no swap: a solution over the limit dies
        # instead of silently crawling and blowing the time limit instead.
        "--memory-swap", f"{memory_mb}m",
        "--pids-limit", PIDS_LIMIT,
        "--cpus", "1",
        "--user", "65534:65534",
        "--cap-drop", "ALL",
        "--security-opt", "no-new-privileges",
        "--read-only",
        "--tmpfs", "/tmp:rw,size=64m",
        "-v", f"{host_dir}:/work",
        "-w", "/work",
        # The image is pulled once at startup (warm_sandbox_image). Failing fast
        # here beats an implicit pull inside the guard below: a cold ~1GB pull
        # outlives it, and the submission dies as an unexplained internal error.
        "--pull", "never",
        "-e", f"LANG_ID={language_id}",
        "-e", f"CPU_LIMIT={cpu_limit}",
        "-e", f"WALL_LIMIT={wall_limit}",
        "-e", f"FSIZE_KB={fsize_kb}",
        "-e", f"JVM_HEAP_MB={jvm_heap_mb}",
        "-e", f"COMPILE_TIMEOUT={COMPILE_TIMEOUT}",
        "-e", "HOME=/work",
        SANDBOX_IMAGE,
    ]

    # Outer guard: the in-container `timeout` should always fire first, so
    # reaching this means the container itself is stuck.
    guard = wall_limit + COMPILE_TIMEOUT + 30
    try:
        proc = subprocess.run(cmd, capture_output=True, timeout=guard)
        exit_code = proc.returncode
        if proc.stderr:
            stderr_text = proc.stderr.decode("utf-8", "replace").strip()
            if stderr_text:
                log(f"[{token}] docker: {stderr_text[:500]}")
    except subprocess.TimeoutExpired:
        log(f"[{token}] container exceeded the outer guard ({guard}s) — killing")
        subprocess.run(["docker", "kill", name], capture_output=True)
        exit_code = 124

    oom = False
    try:
        inspect = subprocess.run(
            ["docker", "inspect", "--format", "{{.State.OOMKilled}}", name],
            capture_output=True, timeout=15)
        oom = inspect.stdout.decode().strip() == "true"
    except (subprocess.SubprocessError, OSError):
        pass
    subprocess.run(["docker", "rm", "-f", name], capture_output=True)
    return exit_code, oom


def classify(meta, oom_killed, container_rc, expected_output, stdout_text):
    """Turn the sandbox's facts into a Judge0 status. Order matters."""
    outcome = meta.get("outcome")

    if outcome == "compile_error":
        return ST_COMPILE_ERROR, None
    if outcome == "unsupported_language":
        return ST_INTERNAL, "unsupported language_id"
    if outcome != "ok":
        # No meta at all: the container died before the script wrote one.
        if oom_killed:
            return ST_SIGSEGV, "memory limit exceeded"
        return ST_INTERNAL, f"sandbox produced no result (container exit {container_rc})"

    try:
        run_rc = int(meta.get("exit_code", "0"))
    except ValueError:
        run_rc = 0

    if oom_killed:
        return ST_SIGSEGV, "memory limit exceeded"

    # `timeout` reports 124; a process killed by a signal reports 128+signal.
    # SIGKILL (137) after `timeout -k`, and SIGXCPU (152) from `ulimit -t`, both
    # mean the same thing here.
    if run_rc in (124, 137, 152):
        return ST_TIME_LIMIT, None
    # Belt and braces: a run that used its whole CPU budget is a timeout even if
    # the exit status came back looking ordinary.
    try:
        if float(meta.get("cpu", "0")) >= float(meta.get("cpu_limit", "0")) > 0:
            return ST_TIME_LIMIT, None
    except ValueError:
        pass

    if run_rc == 139:
        return ST_SIGSEGV, None
    if run_rc == 136:
        return ST_SIGFPE, None
    if run_rc == 134:
        return ST_SIGABRT, None
    if run_rc == 153:
        return ST_SIGXFSZ, "output size limit exceeded"
    if run_rc > 128:
        return ST_RUNTIME_OTHER, f"killed by signal {run_rc - 128}"
    if run_rc != 0:
        return ST_NZEC, f"exited with code {run_rc}"

    # Ran to completion. With no expected output there is nothing to compare —
    # the run flow and the test-case generator both rely on that being Accepted.
    if expected_output is None:
        return ST_ACCEPTED, None
    if normalize_output(stdout_text) == normalize_output(expected_output):
        return ST_ACCEPTED, None
    return ST_WRONG_ANSWER, None


def execute(source, language_id, stdin_text, expected_output,
            cpu_limit, memory_kb, fsize_kb):
    token = uuid.uuid4().hex
    host_dir = os.path.join(WORK_DIR, token)
    os.makedirs(host_dir, exist_ok=True)
    # The sandbox runs as nobody and has to write its outputs here.
    os.chmod(host_dir, 0o777)

    try:
        with open(os.path.join(host_dir, "source"), "wb") as f:
            f.write(source.encode("utf-8"))
        with open(os.path.join(host_dir, "stdin"), "wb") as f:
            f.write((stdin_text or "").encode("utf-8"))

        container_rc, oom = docker_run(
            token, language_id, cpu_limit, memory_kb, fsize_kb, host_dir)

        # Cap what we read back: the fsize ulimit bounds the file on disk, but a
        # limit raised for generated test data would otherwise land in memory.
        cap = max(fsize_kb, 1024) * 1024 + 1024
        stdout_text = read_file(os.path.join(host_dir, "stdout"), cap).decode("utf-8", "replace")
        stderr_text = read_file(os.path.join(host_dir, "stderr"), 64 * 1024).decode("utf-8", "replace")
        compile_text = read_file(os.path.join(host_dir, "compile_output"), 64 * 1024).decode("utf-8", "replace")
        meta = parse_meta(os.path.join(host_dir, "meta"))

        status, message = classify(meta, oom, container_rc, expected_output, stdout_text)

        try:
            cpu_seconds = float(meta.get("cpu", "0"))
        except ValueError:
            cpu_seconds = 0.0
        try:
            memory_used_kb = int(float(meta.get("maxrss_kb", "0")))
        except ValueError:
            memory_used_kb = 0

        return {
            "token": token,
            "stdout": stdout_text,
            "stderr": stderr_text,
            "compile_output": compile_text,
            "message": message,
            # Judge0 sends CPU seconds as a string; the app parses it back
            # (Judge0SubmissionResponse.runtimeMs).
            "time": f"{cpu_seconds:.3f}",
            "memory": memory_used_kb,
            "status": {"id": status[0], "description": status[1]},
        }
    finally:
        shutil.rmtree(host_dir, ignore_errors=True)


# ── HTTP layer ─────────────────────────────────────────────────────────────
def b64_decode(value):
    if value is None:
        return None
    try:
        return base64.b64decode(value, validate=False).decode("utf-8", "replace")
    except (ValueError, TypeError):
        return value


def b64_encode(value):
    if value is None:
        return None
    return base64.b64encode(value.encode("utf-8")).decode("ascii")


class Handler(BaseHTTPRequestHandler):
    server_version = "algoj-judge-runner/1.0"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        # The default handler logs every request to stderr; judging is chatty
        # enough without it. Failures are logged explicitly where they happen.
        pass

    def _send(self, code, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path).path.rstrip("/")
        if path in ("/languages", "/languages/all"):
            self._send(200, LANGUAGES)
        elif path in ("", "/health", "/about"):
            self._send(200, {"status": "ok", "service": "algoj-judge-runner"})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path.rstrip("/") != "/submissions":
            self._send(404, {"error": "not found"})
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        raw = self.rfile.read(length) if length else b"{}"

        try:
            body = json.loads(raw.decode("utf-8"))
        except (ValueError, UnicodeDecodeError) as e:
            self._send(400, {"error": f"invalid JSON body: {e}"})
            return

        query = parse_qs(parsed.query)
        encoded = query.get("base64_encoded", ["false"])[0].lower() == "true"
        decode = b64_decode if encoded else (lambda v: v)

        source = decode(body.get("source_code")) or ""
        stdin_text = decode(body.get("stdin"))
        expected_output = decode(body.get("expected_output"))

        try:
            language_id = int(body.get("language_id"))
        except (TypeError, ValueError):
            self._send(422, {"language_id": ["must be an integer"]})
            return
        if language_id not in SUPPORTED_IDS:
            self._send(422, {"language_id": ["is not supported"]})
            return

        cpu_limit = float(body.get("cpu_time_limit") or 5)
        memory_kb = int(body.get("memory_limit") or 262144)
        fsize_kb = int(body.get("max_file_size") or 4096)

        with _slots:
            try:
                result = execute(source, language_id, stdin_text, expected_output,
                                 cpu_limit, memory_kb, fsize_kb)
            except Exception as e:  # never drop the connection on the app
                log(f"execute failed: {e!r}")
                result = {
                    "token": uuid.uuid4().hex,
                    "stdout": None, "stderr": None, "compile_output": None,
                    "message": f"judge runner error: {e}",
                    "time": None, "memory": None,
                    "status": {"id": ST_INTERNAL[0], "description": ST_INTERNAL[1]},
                }

        if encoded:
            for field in ("stdout", "stderr", "compile_output", "message"):
                result[field] = b64_encode(result[field])

        # Judge0 answers a created submission with 201.
        self._send(201, result)


def warm_sandbox_image():
    """Make sure the sandbox image is on the box before the first submission.

    Otherwise the first `docker run` pulls it — around a gigabyte of toolchains —
    inside the per-submission guard, which it comfortably outlives. Best effort:
    a runner that starts while the registry is unreachable should still come up
    and serve, reporting a clear error per submission until the image lands.
    """
    have = subprocess.run(["docker", "image", "inspect", SANDBOX_IMAGE],
                          capture_output=True)
    if have.returncode == 0:
        log(f"sandbox image present: {SANDBOX_IMAGE}")
        return
    log(f"pulling sandbox image {SANDBOX_IMAGE} (first start; this takes a while)")
    try:
        pull = subprocess.run(["docker", "pull", SANDBOX_IMAGE],
                              capture_output=True, timeout=1800)
        if pull.returncode == 0:
            log("sandbox image ready")
        else:
            log(f"sandbox pull failed: {pull.stderr.decode('utf-8', 'replace').strip()[:500]}")
    except (subprocess.SubprocessError, OSError) as e:
        log(f"sandbox pull failed: {e!r}")


def main():
    os.makedirs(WORK_DIR, exist_ok=True)
    warm_sandbox_image()
    log(f"algoj-judge-runner listening on :{LISTEN_PORT} "
        f"(sandbox={SANDBOX_IMAGE}, work={WORK_DIR}, parallel={MAX_PARALLEL})")
    ThreadingHTTPServer(("0.0.0.0", LISTEN_PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
