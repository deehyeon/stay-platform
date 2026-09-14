# 코드 스타일 가이드라인

## Java / Spring Boot 스타일

### Lombok 사용 원칙
- `@Getter` — 필드 getter 자동 생성 (setter는 사용 금지, 도메인 메서드로 상태 변경)
- `@RequiredArgsConstructor` — `final` 필드 DI용 생성자 (Spring 컴포넌트의 기본)
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` — JPA 엔티티에 필수
- `@Slf4j` — 로깅 (직접 Logger 선언 금지)

```java
// 올바른 예
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberWriterService {
    private final MemberRepository memberRepository;
}

// 금지
@Service
public class MemberWriterService {
    @Autowired
    private MemberRepository memberRepository; // 필드 주입 금지
}
```

### 엔티티 설계 규칙

1. **생성자 접근 제한**: `protected` 또는 `private`, 정적 팩토리 메서드 제공
2. **setter 금지**: `updateXxx()` 형태의 비즈니스 메서드로 상태 변경
3. **기본 클래스 상속 필수**:
   - ID + 감사 필드 필요 → `extends AbstractEntity`
   - 감사 필드만 필요 → `extends BaseEntity`

```java
@Entity
@Table(name = "table_name")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SomeDomain extends AbstractEntity {

    @Column(nullable = false)
    private String field;

    private SomeDomain(String field) { this.field = field; }

    public static SomeDomain create(String field) {
        return new SomeDomain(field);
    }

    public void updateField(String newValue) {
        this.field = newValue;
    }
}
```

### DTO 설계 규칙

Record 타입 사용:

```java
import java.time.Instant;

// 요청 DTO
public record CreatePostReq(
        @NotBlank String title,
        @NotBlank String content
) {
}

// 응답 DTO
public record PostRes(
        Long id,
        String title,
        Instant createdAt
) {
    public static PostRes from(Post post) { ...}
}
```

### 예외 처리 규칙

새 도메인 예외 추가 시 반드시:
1. `{Domain}ErrorType` enum 생성 (implements `ErrorType`)
2. `{Domain}Exception` 클래스 생성 (extends `GlobalException`)

```java
// 1. ErrorType enum
@Getter
@RequiredArgsConstructor
public enum PostErrorType implements ErrorType {
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다."),
    UNAUTHORIZED_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String message;
}

// 2. Exception class
public class PostException extends GlobalException {
    public PostException(ErrorType errorType) {
        super(errorType);
    }
}

// 3. 사용
throw new PostException(PostErrorType.POST_NOT_FOUND);
```

### 컨트롤러 규칙

```java
@RestController
@RequestMapping("/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostReaderService postReaderService;

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostRes>> getPost(@PathVariable Long postId) {
        PostRes result = postReaderService.getPost(postId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> createPost(
            @AuthenticationPrincipal AuthDetails authDetails,
            @RequestBody @Valid CreatePostReq req) {
        postWriterService.create(authDetails.getMemberId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success());
    }
}
```

**API 경로**: 반드시 `/v1/` 접두사 사용

### 로깅 규칙

```java
log.info("...");   // 정상 흐름 (인증 성공, 주요 비즈니스 이벤트)
log.warn("...");   // 예상 가능한 오류 (토큰 만료 등)
log.error("...");  // 예상치 못한 오류 (예외 발생 시 반드시 로깅)
```

### import 규칙

- 와일드카드 import 금지 (`import java.util.*` X)
- 정적 import는 상수/팩토리 메서드에 한해 허용

### 어노테이션 순서 (클래스 레벨)

```java
@Slf4j              // Lombok 로거
@Entity / @Component / @Service / @Repository / @RestController / @Configuration
@Table(...)         // JPA 테이블 설정
@Getter / @RequiredArgsConstructor / @NoArgsConstructor  // Lombok
@RequestMapping     // Spring MVC
```

## 패키지 구조 — 새 도메인 추가 시 체크리스트

```
{newDomain}/
├── domain/
│   ├── {NewDomain}.java              # 엔티티 (extends AbstractEntity)
│   └── {NewDomain}Status.java        # 상태 열거형 (필요 시)
├── application/
│   ├── {NewDomain}WriterService.java # 쓰기 서비스
│   ├── {NewDomain}ReaderService.java # 읽기 서비스
│   ├── provided/
│   │   ├── {NewDomain}Writer.java    # 쓰기 포트
│   │   └── {NewDomain}Reader.java    # 읽기 포트
│   ├── required/
│   │   └── {NewDomain}Repository.java
│   └── dto/
│       ├── {NewDomain}Req.java
│       └── {NewDomain}Res.java
├── adapter/
│   └── {NewDomain}Controller.java    # REST 컨트롤러
└── exception/
    ├── {NewDomain}ErrorType.java
    └── {NewDomain}Exception.java
```

## 어댑터 계층 설계 원칙

공급사별 요청/응답 형식은 **어댑터 계층 안에서만** 알아야 한다.  
도메인·애플리케이션 레이어는 공급사 구분 없이 통합 모델만 다룬다.

신규 공급사 추가 시 변경이 필요한 범위:
1. 새 어댑터 클래스 (요청/응답 변환 로직)
2. 공급사 등록 (설정, DI)
3. 도메인·애플리케이션 레이어는 **변경 없음**

```
supplier/
├── SupplierAdapter.java          # 공급사 어댑터 공통 인터페이스
├── suppliername/
│   ├── {SupplierName}Adapter.java    # WebClient 호출 + 응답 변환
│   └── dto/                          # 공급사 전용 요청/응답 DTO (도메인 밖으로 노출 금지)
│       ├── {SupplierName}HotelRes.java
│       └── {SupplierName}AvailabilityRes.java
```
