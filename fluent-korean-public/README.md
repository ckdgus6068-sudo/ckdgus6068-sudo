# fluent-korean (일반 배포판)

LLM이 한국어로 답할 때 조사와 어미를 생략하거나, 명사로 문장을 끝내거나, 비유적 어휘를 남용해서 읽기 어려운 문장을 만드는 경향이 있습니다. 이 스킬은 그런 경향을 줄이고 의미가 명확한 한국어 문장을 출력하도록 안내하는 문장 지침입니다.

## 구성

- `fluent-korean/SKILL.md`: 스킬 본문입니다.
- `fluent-korean.zip`: 업로드용 압축 파일입니다. 내부에 `fluent-korean/SKILL.md`가 들어 있습니다.

## 설치

- Claude 앱(claude.ai, 데스크톱 앱): Settings → Capabilities에서 ‘Code execution and file creation’을 켠 뒤, Customize → Skills → ‘+’ → ‘+ Create skill’ → ‘Upload a skill’ 순서로 `fluent-korean.zip`을 올리고 스위치를 켭니다. 일반 사용자용 단계별 안내는 저장소 최상위의 `kakao-install-guide.txt`에 있습니다.
- Claude Code: `fluent-korean` 폴더를 `~/.claude/skills/` 아래에 복사하면 모든 프로젝트에서 사용할 수 있습니다. 특정 프로젝트에서만 사용하려면 해당 프로젝트의 `.claude/skills/` 아래에 복사합니다.

## 적용 범위

- 한국어로 작성하는 응답과 문서 전반에 적용됩니다.
- 인용문, 코드, 코드 주석, 커밋 메시지, 로그 문자열에는 적용되지 않습니다.
