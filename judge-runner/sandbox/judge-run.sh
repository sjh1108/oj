#!/bin/bash
# Compile and run ONE submission inside the sandbox container.
#
# Everything this script sees is untrusted: the source file and stdin come from
# a contestant. It therefore never evaluates them as shell, and writes results
# to fixed files that the runner reads back:
#
#   in :  /work/source            raw source bytes (the runner wrote it)
#         /work/stdin             the test case input
#   out:  /work/stdout            program stdout
#         /work/stderr            program stderr
#         /work/compile_output    compiler output (warnings on success too)
#         /work/meta              key=value facts for the runner to interpret
#
# The runner decides the Judge0 status from /work/meta; this script never does.
# Limits arrive as environment variables, all optional but normally set:
#   LANG_ID CPU_LIMIT WALL_LIMIT FSIZE_KB JVM_HEAP_MB COMPILE_TIMEOUT
set -uo pipefail

cd /work 2>/dev/null || { echo "outcome=internal_error"; exit 0; }

LANG_ID="${LANG_ID:-0}"
CPU_LIMIT="${CPU_LIMIT:-5}"            # seconds, may be fractional
WALL_LIMIT="${WALL_LIMIT:-15}"         # seconds, integer — backstop for sleeps/IO waits
FSIZE_KB="${FSIZE_KB:-4096}"
JVM_HEAP_MB="${JVM_HEAP_MB:-192}"
COMPILE_TIMEOUT="${COMPILE_TIMEOUT:-20}"

: > stdout
: > stderr
: > compile_output

# ulimit -t takes whole seconds, so round the CPU limit up. The wall-clock
# `timeout` below is the real cutoff; this is the belt to its braces (it also
# catches a process that busy-loops without ever yielding).
CPU_LIMIT_INT="$(awk -v v="$CPU_LIMIT" 'BEGIN { printf "%d", (v == int(v) ? v : int(v) + 1) }')"
[ "$CPU_LIMIT_INT" -lt 1 ] && CPU_LIMIT_INT=1

# Per language: source filename, compile command, run command.
# Keep the ids identical to Submission.Language's judge0Id values.
case "$LANG_ID" in
  50)
    SRC=main.c
    COMPILE=(gcc -O2 -std=gnu17 -o prog main.c -lm)
    RUN=(./prog)
    ;;
  54)
    SRC=main.cpp
    COMPILE=(g++ -O2 -std=gnu++17 -o prog main.cpp)
    RUN=(./prog)
    ;;
  62)
    # The app rewrites the public class to Main before submitting
    # (JavaSourceNormalizer), so Main.java / Main are always correct here.
    SRC=Main.java
    COMPILE=(javac -encoding UTF-8 -d . Main.java)
    RUN=(java -Dfile.encoding=UTF-8 -XX:+UseSerialGC -Xss64m "-Xmx${JVM_HEAP_MB}m" Main)
    ;;
  63)
    SRC=main.js
    COMPILE=(node --check main.js)
    RUN=(node main.js)
    ;;
  71)
    SRC=main.py
    COMPILE=(python3 -m py_compile main.py)
    RUN=(python3 main.py)
    ;;
  200)
    SRC=main.py
    COMPILE=(pypy3 -m py_compile main.py)
    RUN=(pypy3 main.py)
    ;;
  *)
    printf 'outcome=unsupported_language\n' > meta
    exit 0
    ;;
esac

cp source "$SRC" 2>/dev/null || { printf 'outcome=internal_error\n' > meta; exit 0; }

# ─── compile ──────────────────────────────────────────────────────────────
# Interpreted languages get a syntax check here (py_compile / node --check) so a
# syntax error surfaces as a compile error instead of a runtime error, which is
# what Judge0 does and what the app's status mapping expects.
compile_rc=0
timeout -k 2 "$COMPILE_TIMEOUT" "${COMPILE[@]}" > compile_stdout 2> compile_stderr || compile_rc=$?
cat compile_stdout compile_stderr > compile_output 2>/dev/null
rm -f compile_stdout compile_stderr

if [ "$compile_rc" -ne 0 ]; then
    printf 'outcome=compile_error\ncompile_rc=%s\n' "$compile_rc" > meta
    exit 0
fi

# ─── run ──────────────────────────────────────────────────────────────────
# fsize caps a runaway writer (SIGXFSZ); the cgroup memory cap and pids cap come
# from the docker flags the runner passes, not from here.
ulimit -f "$FSIZE_KB" 2>/dev/null
ulimit -t "$CPU_LIMIT_INT" 2>/dev/null

run_rc=0
/usr/bin/time -f '%e %U %S %M' -o timing -- \
    timeout -k 1 "$WALL_LIMIT" "${RUN[@]}" < stdin > stdout 2> stderr || run_rc=$?

# GNU time's own line is the last one in the file (it appends after any of its
# own errors), and a killed child still gets one.
read -r wall user sys maxrss <<< "$(tail -1 timing 2>/dev/null)"
rm -f timing

cpu="$(awk -v u="${user:-0}" -v s="${sys:-0}" 'BEGIN { printf "%.3f", u + s }')"

{
    printf 'outcome=ok\n'
    printf 'exit_code=%s\n' "$run_rc"
    printf 'wall=%s\n' "${wall:-0}"
    printf 'cpu=%s\n' "$cpu"
    printf 'maxrss_kb=%s\n' "${maxrss:-0}"
    printf 'cpu_limit=%s\n' "$CPU_LIMIT"
} > meta
