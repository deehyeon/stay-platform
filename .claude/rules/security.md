# 보안 요구사항

## 인증 & 인가

### JWT 토큰 정책

| 항목 | 내용 |
|------|------|
| 알고리즘 | HS512 |
| Subject | `memberId` (Long → String) |
| Access Token 전달 | `Authorization: Bearer {token}` 헤더 |
| Refresh Token 저장 | Redis (`RT:{memberId}` 키) |
| 서명 키 | `@Value("${jwt.secret}")` — 환경변수로 주입, 코드에 하드코딩 금지 |

### 인증 필수 vs 공개 경로

**공개 경로 (SecurityConfig에 명시)**:
```
OPTIONS /v1/**            — CORS preflight
/actuator/health/**       — 헬스체크
/swagger-ui/**            — API 문서
/v1/api-docs/**           — OpenAPI 스펙
/v1/connect/**            — OAuth 콜백
```

**새 공개 경로 추가 시**: `SecurityConfig.filterChain()` 의 `requestMatchers` 블록과
`JwtAuthenticationFilter.EXCLUDE_URLS` 리스트 **모두** 업데이트 필요.

### 권한 체크

컨트롤러에서 인증된 사용자 ID 획득:
```java
@GetMapping("/me")
public ResponseEntity<ApiResponse<MemberRes>> getMyInfo(
        @AuthenticationPrincipal AuthDetails authDetails) {
    Long memberId = authDetails.getMemberId();
    ...
}
```

리소스 소유자 검증 필수:
```java
// 다른 사람의 리소스 접근 차단
if (!post.isOwnedBy(authDetails.getMemberId())) {
    throw new PostException(PostErrorType.UNAUTHORIZED_ACCESS);
}
```

## 입력 검증

### DTO 레벨 검증

```java
public record CreatePostReq(
    @NotBlank(message = "제목은 필수입니다.")
    String title,

    @Size(max = 1000, message = "내용은 1000자 이하여야 합니다.")
    String content
) {}
```

- `@Valid` 어노테이션 컨트롤러 메서드 파라미터에 반드시 추가
- 검증 실패 → `ApiControllerAdvice`가 `FAILED_REQUEST_VALIDATION` 반환

### 값 객체 레벨 검증

도메인 규칙 검증은 값 객체 생성자에서 수행 (fail-fast):
```java
public record Email(String address) {
    public Email {
        if (!EMAIL_PATTERN.matcher(address).matches()) {
            throw new GlobalException(GlobalErrorType.EMAIL_INVALID_FORMAT);
        }
    }
}
```

## 비밀번호 처리

- `BCryptPasswordEncoder` 사용 (SecurityConfig에 빈 등록됨)
- 원문 비밀번호는 절대 저장 금지
- 로그에 비밀번호 출력 금지

```java
// 인코딩
String encoded = passwordEncoder.encode(rawPassword);

// 검증
boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);
```

## CORS 정책

```java
// WebMvcConfig — 환경별 origin 분리
@Value("${cors.origin.production}") String prodOrigin
@Value("${cors.origin.development}") String devOrigin
@Value("${cors.origin.test}") String testOrigin
```

- `allowCredentials(true)` — 쿠키/인증 헤더 허용
- 허용 메서드: `GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS`
- Origin은 환경변수로 관리, 코드에 하드코딩 금지

## 민감 정보 관리

### 절대 코드에 하드코딩 금지
- JWT Secret Key
- DB 접속 정보 (URL, username, password)
- Redis 접속 정보
- OAuth Client ID/Secret
- CORS Origin URL

### application.properties 보안
- 민감 값은 `${ENV_VAR_NAME}` 플레이스홀더로 환경변수 참조
- `.gitignore`에 `application-secret.properties` 추가 권장

## SQL Injection 방지

- JPA Repository 메서드 / JPQL 사용 (Native Query 최소화)
- Native Query 불가피 시 반드시 파라미터 바인딩:

```java
@Query("SELECT m FROM Member m WHERE m.email = :email")
Optional<Member> findByEmail(@Param("email") String email);

// 금지
@Query(value = "SELECT * FROM members WHERE email = '" + email + "'", nativeQuery = true)
```

## 소프트 삭제 정책

- 물리 삭제(`repository.delete()`) 금지, `isDeleted = true` 소프트 삭제 사용
- 조회 시 `isDeleted = false` 조건 반드시 포함

```java
// 조회 쿼리에 반드시 포함
@Query("SELECT m FROM Member m WHERE m.id = :id AND m.isDeleted = false")
Optional<Member> findActiveById(@Param("id") Long id);
```

## 에러 응답 보안

- 에러 응답에 스택 트레이스 노출 금지 (코드 + 메시지만 반환)
- 404 vs 403: 리소스 존재 여부를 외부에 노출하지 않을 경우 404로 통일 고려

```json
// 안전한 에러 응답
{
  "result": "ERROR",
  "data": null,
  "error": { "code": "POST_NOT_FOUND", "message": "존재하지 않는 게시글입니다." }
}
```

## Refresh Token 재사용 공격 방지

Refresh Token 처리 흐름:
1. 로그인 → `RT:{memberId}` 키로 Redis에 저장
2. 토큰 재발급 → 기존 RT 삭제 후 새 RT 저장 (Rotation)
3. 로그아웃 → Redis에서 RT 즉시 삭제

```java
// AuthWriterService 구현 시 준수
private static final String RT_PREFIX = "RT:";

// 로그인
memoryMap.setValue(RT_PREFIX + memberId, refreshToken, refreshTokenExpiration);

// 재발급
memoryMap.deleteValue(RT_PREFIX + memberId);  // 기존 삭제 먼저
memoryMap.setValue(RT_PREFIX + memberId, newRefreshToken, refreshTokenExpiration);

// 로그아웃
memoryMap.deleteValue(RT_PREFIX + memberId);
```
