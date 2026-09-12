# The judge runner service — the HTTP half of the Judge0 replacement.
#
# It never executes contestant code itself: it writes the source and input to a
# scratch directory and asks the host's docker daemon to run the sandbox image
# on them. That is why the docker CLI is the base here, and why the compose
# file hands this container the docker socket (see README.md § 보안 모델).
FROM docker:29-cli

# Stdlib only — the service is two endpoints and pip would just be another
# thing to keep patched on a box that runs untrusted code next door.
RUN apk add --no-cache python3

COPY server.py /usr/local/bin/judge-runner.py

EXPOSE 2358

# -u: unbuffered, so `docker logs` shows failures as they happen rather than
# whenever the pipe buffer happens to flush.
ENTRYPOINT ["python3", "-u", "/usr/local/bin/judge-runner.py"]
