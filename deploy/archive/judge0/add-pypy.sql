-- Judge0 Postgres에 PyPy 3 커스텀 언어를 등록한다.
--
-- id 200은 백엔드 enum(Submission.Language.PYPY3(200))과 반드시 일치해야 한다.
-- 200을 쓰는 이유: 스톡 Judge0 CE는 id 43~89를 쓰므로, 향후 Judge0 업그레이드가
-- 추가할 언어와 충돌하지 않도록 멀리 떨어진 값을 고정한다.
--
-- 실행 방법은 같은 폴더의 README.md 참고.
INSERT INTO languages (id, name, is_archived, source_file, compile_cmd, run_cmd)
VALUES (200,
        'Python (PyPy 3.10)',
        false,
        'script.py',
        NULL,
        '/usr/local/pypy3/bin/pypy3 script.py')
ON CONFLICT (id) DO NOTHING;
