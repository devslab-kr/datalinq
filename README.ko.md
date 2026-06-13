# DataLinq

[English](README.md) | **한국어**

TUI 기반 데이터 마이그레이션 도구입니다([TamboUI](https://github.com/tamboui/tamboui)로 제작).
**기본 메뉴는 코드로 고정**되고, **마이그레이션 메뉴는 `sql/` 폴더를 스캔해 자동 발견**됩니다 —
폴더 하나 떨구면 메뉴가 됩니다.

**임의의 두 JDBC 데이터베이스 사이**로 행을 옮깁니다(크로스벤더, 소스 + 타겟). SQL Server,
MariaDB/MySQL, PostgreSQL 드라이버는 **번들**되어 있고, 그 외(Oracle, H2, SQLite, ...)는 `driver`
명령 한 줄로 내려받습니다. 연결은 **DB 종류 + host/port/database**(또는 raw URL)로 설정하며,
`application.yml` 에 적거나 DB 연결 화면에서 바로 편집·저장할 수 있습니다.

> **DevsLab Co., Ltd.** (주식회사 데브스랩) 제작 · https://devslab.kr · Apache-2.0

## 마이그레이션 추가 = 폴더 떨구기

```
sql/
├── 01_Approval_Lines/       # ETL: source.sql(별칭 SELECT) -> 타겟 테이블
│   ├── source.sql
│   └── operation.properties     ->  type=etl, table=approval_lines
├── 03_Reset_Base_Data/      # SCRIPT: .sql 을 타겟에 실행(초기화/삭제)
│   ├── 01_reset.sql
│   └── operation.properties     ->  type=script, destructive=true
└── 05_Orders_With_Items/    # HANDLER: 커스텀 코드(마스터/디테일 분리)
    ├── source.sql
    └── operation.properties     ->  handler=orders
```

- 폴더 `NN_Some_Name` -> 순서 `NN`, 메뉴 라벨 `Some Name`.
- `operation.properties` 는 선택사항입니다. `source.sql` 이 있으면 기본 ETL, 없으면 SCRIPT.

## 세 가지 작업 유형

| 유형 | 폴더 구성 | 하는 일 | 코드? |
|------|-----------|---------|-------|
| **etl** | `source.sql` + `table=<table>` | 소스를 읽어 타겟에 자동 INSERT. SELECT의 **컬럼 별칭 = 타겟 컬럼**이라 INSERT문을 따로 쓰지 않음. | 없음 |
| **script** | `.sql` 하나 이상 | **타겟**에 대해 실행(초기화/삭제). | 없음 |
| **handler** | `source.sql` + `handler=<name>` | `MigrationHandler`(변환 / 마스터-디테일 / 생성키 FK), ServiceLoader로 발견. | ~20줄 |

### Handler (하나의 결과셋이 여러 테이블이 될 때)

```java
public class OrdersMigration extends MigrationHandler {
    public String name() { return "orders"; }     // handler=orders 로 참조
    public void migrate() throws Exception {
        var rows = query("source.sql");            // 평면 결과셋
        // order_no 로 묶어 마스터(orders) insert -> 생성된 id 받아 디테일 insert
        long orderId = insert("orders", values("order_no", ..., "customer_id", ...));
        insert("order_items", values("order_id", orderId, "product_id", ..., "qty", ...));
    }
}
```

Handler는 문자열 리플렉션이 아니라 **ServiceLoader**로 해석됩니다
(`META-INF/services/kr.devslab.datalinq.engine.MigrationHandler` 에 등록) — 그래서 GraalVM
네이티브 이미지 가능성이 열려 있습니다.

## 설정 (`application.yml`)

여러 개의 **이름 있는 데이터소스** — 어느 것이든 소스나 타겟이 될 수 있습니다. 작업은 이름으로
고르고, 없으면 `defaults` 로 폴백합니다:

```yaml
datasources:
  # 구조화 타입은 host/port/database 로 JDBC URL을 만듭니다:
  legacy-erp:                      # 소스
    type: sqlserver                # sqlserver | mariadb | postgresql
    host: SRC_HOST
    port: 1433
    database: SRC_DB
    username: sa
    password: ""
  new-core:                        # 타겟 (mariadb 드라이버는 MySQL 서버에도 접속)
    type: mariadb
    host: TGT_HOST
    port: 3306
    database: TGT_DB
    username: root
    password: ""
  # custom 타입은 직접 쓴 URL을 그대로 사용(내려받은 드라이버에 사용):
  # warehouse:
  #   type: custom
  #   url: jdbc:oracle:thin:@HOST:1521:ORCL
  #   username: app
  #   password: ""
defaults:
  source: legacy-erp               # 작업이 source= 를 지정하지 않을 때 사용
  target: new-core                 # 작업이 target= 을 지정하지 않을 때 사용
options:
  batch-size: 1000
  dry-run-default: true
  language: en                     # en | ko  (빈 값 = 시스템 로캘)
  max-parallel: 4                  # 동시 실행 작업 수(DB 연결 수를 제한)
  mask-password: true              # DB 연결 화면: 비밀번호 마스킹(false = 평문 표시)
  # sql-dir: /path/to/sql          # 외부 마이그레이션 폴더(빈 값 = ./sql)
```

작업은 operation.properties의 `source=<name>` / `target=<name>` 으로 실행마다 재정의할 수 있습니다.
`application.example.yml` 을 `application.yml`(gitignore됨)로 복사하세요. 앱 안(DB 연결 화면)에서
편집·저장할 수도 있습니다.

## 데이터베이스 드라이버

- **번들**(항상 동작): SQL Server, MariaDB / MySQL, PostgreSQL.
- **다운로드** — Maven Central에서 `~/.datalinq/drivers/` 로 받아 다음 실행 시 로드:

  ```bash
  datalinq driver               # 받을 수 있는 목록
  datalinq driver oracle        # 하나 받기 (oracle | mysql | h2 | sqlite | postgresql)
  ```

  해당 폴더에 JDBC 드라이버 `.jar` 을 손으로 떨궈도 됩니다. 외부에서 로드된 드라이버는
  `DriverShim` 을 통해 등록됩니다(그렇지 않으면 JDK의 `DriverManager` 가 외부 클래스로더의
  드라이버를 무시함). **custom** 타입 + 직접 쓴 URL 로 사용하세요.

## 안전장치

- **기본이 dry-run** — 타겟 트랜잭션을 롤백하므로 아무것도 쓰지 않습니다.
- `destructive=true` 작업은 실행 전 명시적 확인을 요구합니다.
- 각 작업은 **하나의 타겟 트랜잭션** 안에서 실행: 성공 시 커밋, 오류 시 롤백.

## 실행

기본 명령은 **TUI** 이고, 모든 기능은 CLI로도 스크립트화할 수 있습니다.

```bash
./gradlew shadowJar                             # 자기완결형 jar 빌드
java -jar build/libs/datalinq.jar               # TUI 메뉴 (마이그레이션 / DB 연결 / 설정 / 정보)

# 또는 CLI로:
java -jar build/libs/datalinq.jar init          # 편집용 기본값 생성 (i18n/, branding/, 예시 설정, sql/)
java -jar build/libs/datalinq.jar list          # 발견된 작업 목록
java -jar build/libs/datalinq.jar config        # 해석된 설정 표시(비밀번호 마스킹)
java -jar build/libs/datalinq.jar run 0         # 0번 작업 dry-run
java -jar build/libs/datalinq.jar run 0 --execute   # 실제 쓰기
```

jar는 단일 드롭형 산출물입니다(Shadow fat-jar, `jbang-catalog.json` 에 [jbang](https://www.jbang.dev/)
별칭 포함). 개발 중에는 `./gradlew run --args="..."` 도 동작합니다.

## 상태

엔진(스캐너 / ETL / script / handler / 트랜잭션 / dry-run), `application.yml` 설정,
번들 + 다운로드 JDBC 드라이버, CLI, 그리고 **TamboUI TUI**(마이그레이션, DB 연결, 설정, 정보 —
모두 동일한 `MigrationEngine` 호출)가 구현·검증되었습니다.
