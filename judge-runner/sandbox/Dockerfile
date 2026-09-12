# Sandbox image — the only place a contestant's code ever runs.
#
# Judge0's official image is amd64-only, so this replaces it on the arm64
# (Oracle Ampere) box. It carries just the toolchains; the isolation comes from
# the flags the runner passes to `docker run` (no network, dropped caps,
# read-only rootfs, memory/pids caps, unprivileged user) — see judge-runner/README.md.
#
# Language set and version strings must stay in sync with Submission.Language
# (src/main/java/dev/algoj/domain/submission/entity/Submission.java) and with
# LANGUAGES in judge-runner/server.py.
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

# `time` is GNU time — the run step reads CPU seconds and peak RSS from it.
# tzdata so timestamps inside a submission match the rest of the service.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        python3 \
        pypy3 \
        openjdk-21-jdk-headless \
        nodejs \
        time \
        tzdata \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Seoul

COPY judge-run.sh /usr/local/bin/judge-run.sh
RUN chmod 755 /usr/local/bin/judge-run.sh

# The runner always overrides this with an explicit command, but a sane default
# makes the image easy to poke at by hand when debugging on the box.
WORKDIR /work
ENTRYPOINT ["/usr/local/bin/judge-run.sh"]
