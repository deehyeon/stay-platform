---
name: create-pr
description: |
  GitHub Pull Request를 생성합니다. MUST USE this skill whenever the user wants to create a PR, pull request, or 풀리퀘스트. This skill contains the project's specific PR template with Motivation, Modification, Result, Analyzing queries sections that CANNOT be generated without consulting this skill. Triggers: PR 만들어줘, PR 생성, PR 올려줘, 풀리퀘 생성, create PR, open pull request, 변경사항 PR로, 코드 리뷰 요청, PR 날려줘. Always use this skill even for simple PR creation requests — it contains critical project-specific templates and conventions.
---

# Create PR

GitHub Pull Request를 생성한다. 커밋 히스토리와 코드 변경 내용을 분석하여 PR 템플릿의 각 섹션을 자동으로 작성한다.

## 호출 방법

- `/create-pr` — base 브랜치를 `main`으로 PR 생성
- `/create-pr develop` — base 브랜치를 `develop`으로 PR 생성
- "PR 만들어줘", "풀리퀘 생성해줘", "main에 PR 올려줘" 등 자연어로도 호출 가능

**파라미터가 없으면 기본 base 브랜치는 `main`이다.**

---

## 작업 흐름

### Phase 0: 사전 체크 (브랜치 & 커밋)

PR 생성 전에 브랜치와 커밋 상태를 확인하고, 필요 시 자동으로 생성한다.

```bash
# 1. 현재 브랜치 확인
git branch --show-current
# 2. 커밋되지 않은 변경사항 확인
git status
# 3. base 브랜치와의 커밋 차이 확인
git log {base}..HEAD --oneline
```

#### 브랜치 체크

현재 브랜치가 base 브랜치와 동일한 경우:
1. `git status`로 변경사항 존재 여부 확인
2. 변경사항이 있으면 → 사용자에게 브랜치 생성을 제안:
   - "현재 `{base}` 브랜치에 있습니다. PR을 생성하려면 작업 브랜치가 필요합니다. 브랜치를 생성할까요?"
   - 변경 내용을 분석하여 적절한 브랜치명 제안 (예: `no-issue/update-xxx`, `feature/xxx`)
3. 사용자 승인 시 → 브랜치 생성 (`git checkout -b {브랜치명}`)
4. 변경사항이 없으면 → "PR을 생성할 변경사항이 없습니다." 안내 후 종료

#### 커밋 체크

브랜치가 준비된 후, base 브랜치와 커밋 차이가 없는 경우:
1. `git status`로 커밋되지 않은 변경사항 확인
2. 변경사항이 있으면 → 변경 내용을 분석하여 커밋 생성을 제안
3. 사용자 승인 시 → `git add` + `git commit` 실행
4. 변경사항이 없으면 → "PR을 생성할 커밋이 없습니다." 안내 후 종료

### Phase 1: 정보 수집

**base 브랜치 결정**: 파라미터로 전달된 값이 있으면 원격 브랜치 존재 여부를 먼저 확인한다.

```bash
git ls-remote --heads origin {파라미터}
```

- **브랜치가 존재하면** → `{base}` = 해당 파라미터
- **브랜치가 존재하지 않으면** → 사용자에게 질문
- **파라미터가 없으면** → `{base}` = `main`

다음 git 명령어로 정보를 수집한다:

```bash
# 1. 현재 브랜치명 (이슈 번호 추출용)
git branch --show-current
# 2. {base}와의 커밋 차이
git log {base}..HEAD --oneline
# 3. 상세 커밋 로그
git log {base}..HEAD --pretty=format:"%h %s%n%b%n---"
# 4. 변경된 파일 목록
git diff {base}...HEAD --name-only
# 5. 상세 diff
git diff {base}...HEAD
# 6. 변경 통계
git diff {base}...HEAD --stat
```

### Phase 2: 분석 및 본문 생성

#### Motivation 섹션

- 커밋 메시지에서 "왜" 이 변경이 필요한지 추출
- 브랜치 prefix로 작업 유형 판단:
  - `feature/*` → 새 기능 추가
  - `bugfix/*`, `hotfix/*` → 버그 수정
  - `refactor/*` → 리팩토링
  - `chore/*` → 설정·환경 변경

#### Modification 섹션

변경 파일을 카테고리별로 분류하여 작성한다:

| 파일 패턴 | 카테고리 |
|-----------|----------|
| `*Controller.java` | API 변경 |
| `*Service.java` | 비즈니스 로직 변경 |
| `*Repository.java` | 데이터 접근 변경 |
| `**/domain/**/*.java` | 도메인 모델 변경 |
| `**/adapter/supplier/**` | 공급사 연동 변경 |
| `src/main/resources/**` | 설정/리소스 변경 |

**작성 제외 항목**:
- `src/test/**` 테스트 파일 변경
- 단순 getter/setter 등 중요 로직이 없는 변경

#### Result 섹션

- PR 머지 후 예상되는 결과 작성
- 이슈 연동: 브랜치명에서 번호 추출
  - `feature/1-add-login` → `resolve #1`
  - `feature/1/add-login` → `resolve #1`
  - `no-issue/xxx` → 이슈 연동 없음 (생략)

#### Analyzing queries 섹션

**목적**: 이 PR의 코드가 호출하는 쿼리와 호출 순서를 기록 (리뷰어가 성능 영향 파악용)

**분석 방법**:
1. 변경된 Service 코드에서 Repository 호출 추적
2. Controller → Service → Repository 순으로 쿼리 호출 흐름 파악

**기록할 내용**:
- 호출되는 Repository 메서드명
- 쿼리 호출 순서
- EntityGraph 사용 시 fetch 대상
- Native Query 여부

출력 예시:
```markdown
### Analyzing queries
1. `SupplierMappingRepository.findBySupplierAndHotelCode(Supplier, String)` - 공급사 숙소 매핑 조회
2. `RoomTypeMappingRepository.findAllBySupplierAndHotelCode(Supplier, String)` - 객실 타입 매핑 배치 조회
3. `SupplierMappingRepository.save(SupplierMapping)` - 매핑 저장
```

쿼리 변경이 없는 경우:
```markdown
### Analyzing queries
이번 PR에서 쿼리 관련 변경사항이 없습니다.
```

### Phase 3: PR 생성

#### PR 타이틀 규칙

```
[카테고리] 핵심 변경 내용 요약 (#이슈번호)
```

카테고리 자동 결정:
- `bugfix/*`, `hotfix/*` → `[bugfix]`
- `feature/*` → `[feat]`
- `refactor/*` → `[refactor]`
- `chore/*` → `[chore]`
- 기타 → 커밋 메시지/변경 파일에서 추론

#### gh CLI로 PR 생성

```bash
gh pr create \
  --base {base} \
  --title "[카테고리] PR 제목 (#이슈번호)" \
  --body "$(cat <<'EOF'
### Motivation
[분석된 동기]

### Modification
[분석된 변경 내용]

### Result
[분석된 결과]
resolve #XXX

### Analyzing queries
[분석된 쿼리]
EOF
)"
```

---

## 특수 케이스 처리

### 이슈 번호 없는 브랜치

`no-issue/*` 등의 경우:
- Result 섹션에서 `resolve #XXX` 생략
- 사용자에게 이슈 연동 여부 확인

### 커밋이 많을 때 (10개 이상)

카테고리별로 그룹화하여 요약:
```markdown
### Modification
**주요 변경 사항** (총 15개 커밋):
1. **공급사 어댑터** (5개 커밋)
   - WebClient 타임아웃 설정 추가
   - Supplier B 실패 판정 로직 구현
2. **매핑 저장** (7개 커밋)
   - 숙소 매핑 upsert 로직 구현
```

### 변경 파일이 많을 때 (30개 이상)

도메인별로 그룹화하고 핵심 파일 위주로 나열한다.

---

## 체크리스트

### 사전 체크
- [ ] 현재 브랜치가 base 브랜치와 동일하지 않은지 확인
- [ ] base 브랜치와 커밋 차이가 있는지 확인
- [ ] 필요 시 브랜치 생성 / 커밋 생성

### 정보 수집
- [ ] 현재 브랜치명 확인
- [ ] 이슈 번호 추출 시도
- [ ] base 브랜치와의 커밋 차이 확인
- [ ] 변경된 파일 목록 확인
- [ ] 상세 diff 분석

### 분석
- [ ] Motivation: 작업 동기 파악
- [ ] Modification: 변경 내용 분류
- [ ] Result: 예상 결과 + 이슈 연동
- [ ] Analyzing queries: Repository/Query 변경 분석

### PR 생성
- [ ] PR 타이틀이 컨벤션을 따르는지 확인
- [ ] 모든 섹션이 채워졌는지 확인
- [ ] 이슈 연동 여부 확인
- [ ] base 브랜치가 파라미터 또는 기본값(main)과 일치하는지 확인
