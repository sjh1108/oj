#!/usr/bin/env python3
"""End-to-end check of the judge runner — run this on the box after deploying.

The unit tests cover the verdict logic; this covers the half that needs a real
docker daemon: every language actually compiles and runs, and each failure mode
comes back as the status the app expects.

    python3 judge-runner/selftest.py                    # finds the container itself
    JUDGE_URL=http://127.0.0.1:2358 python3 judge-runner/selftest.py
"""

import base64
import json
import os
import subprocess
import sys
import urllib.request

CONTAINER = os.environ.get("JUDGE_CONTAINER", "algoj-judge-runner")


def resolve_url():
    url = os.environ.get("JUDGE_URL")
    if url:
        return url
    # The runner deliberately publishes no host port, so reach it on its
    # address inside algoj-net (the host can route to the bridge directly).
    out = subprocess.run(
        ["docker", "inspect", "-f",
         "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", CONTAINER],
        capture_output=True, text=True)
    ip = out.stdout.strip()
    if not ip:
        sys.exit(f"could not find container {CONTAINER}; set JUDGE_URL instead")
    return f"http://{ip}:2358"


BASE_URL = resolve_url()


def b64(s):
    return base64.b64encode(s.encode()).decode()


def unb64(s):
    return base64.b64decode(s).decode("utf-8", "replace") if s else ""


def submit(source, language_id, stdin="", expected=None, cpu=2.0, memory_kb=262144):
    payload = {
        "source_code": b64(source),
        "language_id": language_id,
        "stdin": b64(stdin),
        "cpu_time_limit": cpu,
        "memory_limit": memory_kb,
        "max_file_size": 4096,
    }
    if expected is not None:
        payload["expected_output"] = b64(expected)
    req = urllib.request.Request(
        f"{BASE_URL}/submissions?base64_encoded=true&wait=true",
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode())


# Two integers on one line, print the sum — the same program in six languages.
SUM_PROGRAMS = {
    50: '#include <stdio.h>\nint main(){long a,b;scanf("%ld %ld",&a,&b);printf("%ld\\n",a+b);return 0;}',
    54: '#include <iostream>\nint main(){long a,b;std::cin>>a>>b;std::cout<<a+b<<std::endl;return 0;}',
    62: 'import java.util.Scanner;\npublic class Main{public static void main(String[] a){Scanner s=new Scanner(System.in);System.out.println(s.nextLong()+s.nextLong());}}',
    63: 'const d=require("fs").readFileSync(0,"utf8").trim().split(/\\s+/);console.log(Number(d[0])+Number(d[1]));',
    71: 'a,b=map(int,input().split())\nprint(a+b)',
    200: 'a,b=map(int,input().split())\nprint(a+b)',
}

failures = []


def check(name, got, want):
    ok = got == want
    print(f"  {'PASS' if ok else 'FAIL'}  {name}: got {got}, want {want}")
    if not ok:
        failures.append(name)
    return ok


def main():
    print(f"judge runner at {BASE_URL}\n")

    with urllib.request.urlopen(f"{BASE_URL}/languages", timeout=10) as resp:
        languages = json.loads(resp.read().decode())
    print(f"/languages -> {[l['id'] for l in languages]}\n")

    print("every language runs and is judged correct:")
    for language_id, source in SUM_PROGRAMS.items():
        res = submit(source, language_id, stdin="2 3\n", expected="5\n")
        if not check(f"language {language_id} accepted", res["status"]["id"], 3):
            print(f"        stderr: {unb64(res.get('stderr'))[:200]}")
            print(f"        compile: {unb64(res.get('compile_output'))[:200]}")
        else:
            print(f"        time={res['time']}s memory={res['memory']}KB")

    print("\nwrong output is a wrong answer, not an error:")
    res = submit(SUM_PROGRAMS[71], 71, stdin="2 3\n", expected="6\n")
    check("wrong answer", res["status"]["id"], 4)

    print("\ntrailing whitespace does not fail a correct answer:")
    res = submit('print("5   ")', 71, stdin="", expected="5")
    check("trailing whitespace tolerated", res["status"]["id"], 3)

    print("\nfailure modes:")
    res = submit("while True: pass", 71, stdin="", expected="x", cpu=1.0)
    check("time limit", res["status"]["id"], 5)

    res = submit("int main(){ this is not c++ }", 54, stdin="", expected="x")
    check("compile error", res["status"]["id"], 6)

    res = submit("import sys; sys.exit(3)", 71, stdin="", expected="x")
    check("runtime error (nonzero exit)", res["status"]["id"], 11)

    res = submit("raise SystemExit(1)", 71, stdin="", expected="x")
    check("runtime error (exception)", res["status"]["id"], 11)

    res = submit("x = bytearray(400 * 1024 * 1024)\nprint(len(x))", 71,
                 stdin="", expected="x", memory_kb=65536)
    check("memory limit -> runtime error", res["status"]["id"] in (7, 10, 11, 12), True)

    print("\nno expected output means accepted (run / generator flow):")
    res = submit('print("hello")', 71, stdin="")
    check("bare run", res["status"]["id"], 3)

    print()
    if failures:
        print(f"{len(failures)} check(s) failed: {', '.join(failures)}")
        sys.exit(1)
    print("all checks passed")


if __name__ == "__main__":
    main()
