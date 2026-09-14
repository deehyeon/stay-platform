# CLAUDE.md — stay-platform 백엔드 프로젝트 지침

> 이 파일을 먼저 읽고 작업을 시작하세요. 추가 규칙은 `.claude/rules/` 디렉토리를 참조하세요.

## 프로젝트 개요

**stay-platform** — 여러 외부 숙박 공급사(Supplier)의 상품을 하나의 플랫폼으로 통합하는 채널 연동 백엔드 시스템

| 항목 | 내용 |
|------|------|
| Framework | Spring Boot 4.0.6 |
| Language | Java 21 |
| Build | Gradle |
| DB | PostgreSQL (주 DB) |
| Cache | Redis (토큰 블랙리스트, 세션) |
| Auth | JWT (Access + Refresh Token) + OAuth2 |

---

## 아키텍처: 헥사고날 (Ports & Adapters)

```
{domain}/
├── domain/          # 핵심 도메인 모델, 값 객체, 열거형
├── application/
│   ├── {Name}WriterService.java   # 비즈니스 로직 구현체
│   ├── {Name}ReaderService.java
│   ├── provided/    # 출력 포트 인터페이스 (application이 제공하는 계약)
│   ├── required/    # 입력 포트 인터페이스 (repository 등 외부 의존)
│   └── dto/         # 데이터 전송 객체
├── adapter/         # 기술 구현체 (JWT, Redis 등)
└── exception/       # 도메인별 예외
global/
├── config/          # Spring 설정 빈
├── domain/          # 공유 도메인 (BaseEntity, Email)
├── exception/       # 전역 예외 처리
├── application/     # 공유 인터페이스 (MemoryMap)
├── adapter/         # 인프라 어댑터 (Redis)
└── webapi/          # REST 응답 포맷, ExceptionHandler
```

**핵심 원칙**: 비즈니스 로직은 `domain` + `application` 레이어에만 존재하며, 인프라 기술(JPA, Redis, JWT)은 `adapter`에 위치.

---

## 주요 패턴 & 규칙 요약

### 1. 응답 포맷 — `ApiResponse<T>`
모든 API는 `ApiResponse<T>` 래퍼를 반환한다.

```java
// 성공
ApiResponse.success()           // 데이터 없음
ApiResponse.success(data)       // 데이터 있음

// 에러
ApiResponse.error(ErrorType)
ApiResponse.error(ErrorType, customMessage)
```

응답 구조:
```json
{ "result": "SUCCESS|ERROR", "data": {...}, "error": { "code": "ENUM_NAME", "message": "..." } }
```

### 2. 예외 처리 — `GlobalException` 계층
```
RuntimeException
└── GlobalException(ErrorType errorType)
    └── AuthException
        └── {Domain}Exception  ← 새 도메인 예외 추가 시 이 패턴 사용
```

**에러 타입 정의 패턴** (한국어 메시지):
```java
@Getter @RequiredArgsConstructor
public enum {Domain}ErrorType implements ErrorType {
    ERROR_CODE(HttpStatus.STATUS, "한국어 메시지");
    private final HttpStatus status;
    private final String message;
}
```

`ApiControllerAdvice`가 모든 예외를 처리 → `ResponseEntity<ApiResponse<?>>` 반환.

### 3. 엔티티 기본 구조
```java
// ID 없는 Audit 전용
class BaseEntity          // createdAt, modifiedAt, isDeleted (소프트 삭제)

// ID 포함 일반 엔티티
abstract class AbstractEntity extends BaseEntity  // + Long id (IDENTITY 전략)
```

**소프트 삭제**: `isDeleted = true` 방식. 물리적 삭제 금지.

### 4. 값 객체 (Value Object)
Record 기반, 생성자에서 검증:
```java
public record Email(String address) {
    public Email { /* 검증 로직 */ }
    public static Email from(String email) { return new Email(email); }
}
```

### 5. 엔티티 팩토리 메서드
생성자는 `protected` 또는 `private`, 정적 팩토리 메서드 사용:
```java
public static Member create(String nickname, ...) { return new Member(...); }
```

### 6. Redis (MemoryMap)
`MemoryMap` 인터페이스를 통해서만 Redis 접근:
```java
memoryMap.setValue(key, value, timeoutMillis);
memoryMap.getValue(key);
memoryMap.deleteValue(key);
memoryMap.checkExistsValue(key);
```
Refresh Token 키 접두사: `"RT:"` + memberId

---

## 인증 흐름 요약

1. 클라이언트 → `Authorization: Bearer {accessToken}` 헤더 전송
2. `JwtAuthenticationFilter` → 토큰 추출 → `TokenProviderPort.getAuthentication()`
3. `SecurityContextHolder`에 `UsernamePasswordAuthenticationToken` 저장
4. 컨트롤러에서 `AuthDetails` (memberId 포함) 사용

**퍼블릭 경로** (인증 불필요):
- `/v1/connect/**` (OAuth)
- `/swagger-ui/**`, `/v1/api-docs/**`
- `/actuator/health/**`

---

## 현재 구현 도메인

| 도메인 | 상태 | 주요 클래스 |
|--------|------|------------|
| `auth` | 기본 구조 완료, 서비스 구현 중 | `AuthWriterService`, `JwtTokenProvider`, `JwtAuthenticationFilter` |
| `member` | 도메인 모델 완료 | `Member`, `MemberRole`, `MemberRepository` |
| `global` | 완료 | `ApiResponse`, `GlobalException`, `RedisMemoryMap`, `SecurityConfig` |

---

## 네이밍 컨벤션

| 종류 | 패턴 | 예시 |
|------|------|------|
| 서비스 (쓰기) | `{Entity}WriterService` | `AuthWriterService` |
| 서비스 (읽기) | `{Entity}ReaderService` | `MemberReaderService` |
| 포트 (provided) | `{Feature}Port` or `{Feature}` | `TokenProviderPort`, `AuthWriter` |
| 포트 (required) | `{Entity}Repository` | `AuthRepository` |
| 어댑터 | `{Tech}{Role}` | `JwtTokenProvider`, `RedisMemoryMap` |
| DTO (요청) | `{Domain}Req` | `LoginReq` |
| DTO (응답) | `{Domain}Res` | `TokenRes` |
| 예외 타입 | `{Domain}ErrorType` | `AuthErrorType` |
| 예외 클래스 | `{Domain}Exception` | `AuthException` |

---

## 상세 규칙 참조

- **코드 스타일**: `.claude/rules/code-style.md`
- **테스트**: `.claude/rules/testing.md`
- **보안**: `.claude/rules/security.md`
