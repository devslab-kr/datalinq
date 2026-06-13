# 변경 이력

[English](CHANGELOG.md) | **한국어**

DataLinq의 주요 변경 사항을 기록합니다.
형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며,
[유의적 버전(SemVer)](https://semver.org/lang/ko/)을 지향합니다.

## [Unreleased]

첫 기능 완성본: TamboUI 프론트엔드를 갖춘 크로스벤더 JDBC 데이터 마이그레이션 도구입니다.
아직 레지스트리에 배포되지 않았으며, 버전은 `0.1.0-SNAPSHOT` 입니다.

### Added (추가)

- **마이그레이션 엔진** - 폴더 스캔 기반 작업 발견(`sql/NN_Name/`), 세 가지 작업 유형
  (ETL 행 복사, 타겟에 SCRIPT 실행, 커스텀 HANDLER), 각 작업을 하나의 타겟 트랜잭션으로 실행
  (성공 시 커밋, 오류 시 롤백), **기본 dry-run**.
- **Handler** - `MigrationHandler` + ServiceLoader(문자열 리플렉션 없음, GraalVM 친화), 마스터/디테일
  분리·생성키 FK용 헬퍼, 그리고 형 변환 `Row` 접근자(`getLong/getInt/getBigDecimal/getBool`).
- **병렬 배치 러너** - 가상 스레드 기반, `max-parallel` 로 동시 실행 수 제한.
- **`application.yml` 설정** - 이름 있는 다중 데이터소스 풀(어느 것이든 소스/타겟 가능),
  `defaults.source/target`, 작업별 `source=`/`target=` 재정의, 옵션
  (`batch-size`, `dry-run-default`, `language`, `max-parallel`, `mask-password`, `sql-dir`).
- **구조화 연결** - `type` + `host`/`port`/`database`(sqlserver / mariadb / postgresql)로
  데이터소스를 설정하고, 직접 쓴 URL을 유지하는 `custom` 타입 제공.
- **JDBC 드라이버** - SQL Server, MariaDB/MySQL, PostgreSQL 번들. 그 외는 `datalinq driver <name>`
  로 Maven Central에서 `~/.datalinq/drivers/` 로 다운로드(oracle / mysql / h2 / sqlite). 외부에서
  로드된 드라이버는 `DriverShim` 으로 등록되어 JDK `DriverManager` 가 사용할 수 있게 함.
- **TamboUI TUI** (MVC; 컨트롤러는 TUI/DB 비의존 + 단위테스트) - 마이그레이션 메뉴(숫자 단축키,
  마우스, 실행/dry-run/전체실행), **DB 연결** 화면(2-패널 데이터소스 목록 / 구조화 입력, 테스트 +
  저장, 기본 소스/타겟, 파생 URL 미리보기), **설정** 화면(옵션 + sql 폴더 선택기, 메뉴 즉시 갱신),
  정보 / 파괴적-확인 다이얼로그. 전반에 CJK 폭 인식 정렬.
- **CLI** - `tui`(기본), `init`, `list`, `config`, `run <index> [--execute]`, `driver`, `i18n`, `logo`.
- **배포** - 기본 리소스를 내장한 단일 드롭형 Shadow fat-jar(`datalinq.jar`), jbang 별칭, 편집용
  기본값을 생성하는 `datalinq init`.
- **i18n** (영어 / 한국어) - 번들 기본값에 외부 `messages_<lang>.properties` 를 덮어쓰는 방식, 외부
  브랜드 로고.

### Fixed (수정)

- Windows에서 드라이버 재다운로드가 난해한 파일 잠금 오류로 실패하던 문제: 외부 드라이버는 이제
  연결을 여는 명령(`tui`/`run`)에서만 로드되어 `driver <name>` 이 이미 받아둔 jar을 갱신할 수 있음.
  다운로드는 HTTP 상태를 명확히 보고하고, 잘린 jar이 남지 않도록 `.part` 임시파일에 받은 뒤 원자적
  이동을 함.
- 메인 화면에서 Esc 입력 시 크래시(`NoClassDefFoundError`).
- DB 연결 화면: CJK 라벨에서의 컬럼 정렬 깨짐, 그리고 도달/편집할 수 없던 입력 필드(이제 좌우 2-패널
  탐색 모델).

### Notes (참고)

- `application.yml` 은 자격 증명을 담고 있어 gitignore됩니다. `application.example.yml` 을 복사하세요.
- Java 21 바이트코드를 타겟(Gradle 툴체인)하며 JDK 21+ 어디서나 동작합니다.
