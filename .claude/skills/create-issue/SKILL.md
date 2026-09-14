---
name: create-issue
description: |
  GitHub Issue를 생성합니다. MUST USE this skill whenever the user wants to create an issue, 이슈, 티켓. This skill contains the project's issue title prefix convention (FEAT / REFACTOR / FIX) and template mapping that CANNOT be inferred without consulting this skill. Triggers: 이슈 만들어줘, 이슈 생성, 이슈 올려줘, create issue, open issue, 버그 이슈, 기능 이슈, 리팩토링 이슈. Always use this skill even for simple issue creation requests — it contains critical project-specific title/template conventions.
---

# Create Issue

GitHub Issue를 생성한다. 요청 내용을 분석해 작업 유형(FEAT/REFACTOR/FIX)을 판단하고, 그에 맞는 제목 접두사와 템플릿을 적용해 이슈를 생성한다.

## 호출 방법

- `/create-issue {내용}` — 내용을 분석해 유형을 자동 판단 후 이슈 생성
- `/create-issue feat {내용}` / `/create-issue refactor {내용}` / `/create-issue fix {내용}` — 유형을 직접 지정
- "이슈 만들어줘", "버그 이슈 올려줘", "리팩토링 이슈 생성해줘" 등 자연어로도 호출 가능

**유형이 명시되지 않으면 요청 내용을 분석해 자동으로 판단한다.**

---

## 작업 흐름

### Phase 1: 유형 판단 (FEAT / REFACTOR / FIX)

사용자 요청에서 다음 키워드/맥락으로 유형을 판단한다.

| 유형 | 판단 기준 | 제목 접두사 | 템플릿 |
|------|-----------|-------------|--------|
| FEAT | 새 기능/도메인/API 구현, "~구현한다", "~추가한다" | `FEAT : ` | `.github/ISSUE_TEMPLATE/feature.md` |
| REFACTOR | 기존 코드 구조 개선, "~수정한다"(동작 변경 없음), "~개선한다", "~정리한다" | `REFACTOR : ` | `.github/ISSUE_TEMPLATE/refactor.md` |
| FIX | 에러/버그 해결, "~에러를 해결한다", "~버그를 고친다" | `FIX : ` | `.github/ISSUE_TEMPLATE/fix.md` |

판단이 애매하면 사용자에게 묻는다. (예: "리팩토링인가요, 버그 수정인가요?")

### Phase 2: 정보 수집

해당 템플릿 파일을 읽어 섹션 구조를 확인한다.

```bash
cat .github/ISSUE_TEMPLATE/{feature|refactor|fix}.md
```

요청 내용과 (있다면) 현재 변경사항/코드베이스를 참고해 템플릿의 각 섹션을 채운다.

```bash
git status
git diff
```

### Phase 3: 제목 생성

```
{PREFIX} : {핵심 작업 내용을 한 문장으로 요약}
```

- `FEAT : Supplier C 연동 어댑터 구현`
- `REFACTOR : WebClient 타임아웃 설정 공통 모듈로 분리`
- `FIX : Supplier B resultCode 미확인으로 장애 오탐지되는 에러 해결`

이미 템플릿의 `title` front matter에 접두사(`FEAT : `, `REFACTOR : `, `FIX : `)가 채워져 있으므로, 그 뒤에 요약 문장만 이어 붙인다.

### Phase 4: 본문 생성

각 템플릿의 섹션 구성:

**feature.md**
```markdown
## 어떤 기능인지
[구현할 기능 설명]

## 작업 내용
- [ ] 작업 항목1
- [ ] 작업 항목2
```

**refactor.md**
```markdown
## 리팩토링 대상
[대상 코드/구조]

## 리팩토링 이유
[개선 필요 이유]

## 작업 내용
- [ ] 작업 항목1
```

**fix.md**
```markdown
## 에러 내용
[에러/버그 설명]

## 재현 방법
[재현 절차]

## 작업 내용
- [ ] 작업 항목1
```

### Phase 5: 이슈 생성

```bash
gh issue create \
  --title "{PREFIX} : {요약}" \
  --body "$(cat <<'EOF'
[Phase 4에서 채운 본문]
EOF
)"
```

라벨을 지정할 근거(사용자 명시, 기존 라벨 목록)가 있으면 `--label`을 추가한다. 근거가 없으면 라벨 없이 생성한다.

---

## 체크리스트

- [ ] 요청 내용으로 유형(FEAT/REFACTOR/FIX) 판단, 애매하면 사용자에게 확인
- [ ] 해당 템플릿 파일을 읽어 섹션 구조 확인
- [ ] 제목에 올바른 접두사 적용
- [ ] 템플릿의 모든 섹션 채움 (빈 섹션 남기지 않기)
- [ ] `gh issue create`로 생성 후 결과 URL 사용자에게 전달
