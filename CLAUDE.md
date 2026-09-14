# 프로젝트 가이드

## 설계 문서

작업 전 아래 문서를 참고한다.

- **아키텍처**: `docs/architecture.md` — 패키지 구조, 핵심 플로우, 설계 결정 사항, Mock Supplier 설계
- **구현 계획**: `docs/implementation-plan.md` — 필수 구현 체크리스트(①~⑥), 개발 순서(Phase 1~8), 핵심 검증 시나리오
- **공급사 스펙**: `docs/supplier-spec.md` — Supplier A·B API 엔드포인트, 요청/응답 필드, 에러 코드, 실패 판정 조건

## 규칙 및 스킬

- 규칙: `.claude/rules/` — 파일 경로에 따라 자동 로드
- 스킬: `.claude/skills/` — `/create-issue`, `/create-pr` 등 슬래시 명령으로 호출
