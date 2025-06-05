## 0.3.4b - 2025-06-05
### 새로운 기능
+ 알파 -> 베타 전환
+ Kotlin 2.1.21로 업데이트
+ `Language.eval` API에 options 파라미터 추가
+ RhinoJS의 eval 파라미터에 `allowJavaAccess`, `allowApiAccess`, `optimizationLevel`, `languageVersion` 파라미터 추가
+ `Language.compile`에 project가 제공되지 않아도 일부 API를 주입하도록 개선
+ API2의 Bot API가 디버그룸에서 동작하도록 개선

### 버그 수정 / 개선 사항
+ 코드 최적화 개선
+ 코드 에디터의 파일 트리가 폴더를 우선 정렬하도록 수정
+ 코드 에디터의 세션 탭이 올바르게 동작하도록 수정
+ 코드 에디터의 세션 탭에 전체 파일 경로가 아닌 파일명만 표시되도록 수정
+ 하단 알림이 일부 기기에서 제스쳐 바에 가려지는 현상 수정
+ 코드 생성기 최적화 개선

## 0.3.3a - 2025-01-19
### 새로운 기능
+ `Api.markAsReadOnID`, `Api.sendToID` 구현 추가 `(도리도리ㅎ)`
+ 에디터 파일 트리에 하위 파일 생성 / 파일 이름 변경 기능 추가

### 버그 수정 / 개선 사항
+ timeout 관련 API 사용시 Thread Starvation이 생기던 문제 수정 `(Yellu)`
+ 업데이트 다운로드 실패 시 유저에게 표시되도록 개선
+ 로그가 일정 개수 이상 쌓일 시 앱이 종료되던 문제 수정 `(shaper, bgyooPtr, hahamini)`
+ 에디터 파일 트리 팝업 사용성 개선
+ 일부 로그의 로그 레벨 변경

## 0.3.2a - 2024-12-06
### 버그 수정 / 개선 사항
+ 후원 링크 변경 (토스아이디 -> Buy me a coffee)
+ 레거시 API 오타 수정 `(Ample)`
+ 일부 최신 기기에서 Edge to edge 기능 활성화 `(Ample)`

## 0.3.2a - 2024-08-22
### 버그 수정 / 개선 사항
+ 초기 설정에서 권한이 제대로 요청되지 않는 문제 수정 `(Ample, 미노)`
+ 초기 설정이 반복되어 표시되는 문제 수정 `(Ample, 미노)`
+ 레거시 `response` 이벤트의 `replier`가 올바르게 동작하도록 구현 수정 `(thfzm)`

## 0.3.1a - 2024-08-15
### 버그 수정 / 개선 사항
+ 프로젝트 설정 화면의 값이 제대로 동기화되지 않는 문제 수정 `(삼플)`

## 0.3.0a - 2024-08-14
### 주요 변경 사항
+ Rhino 엔진 최신 버전 적용 (`1.7.15`)
  + Promise, BigInt 등 ES6 구현 추가
+ 전반적 디자인 개선
  + 탭 별 설정 직관성 개선
  + `프로젝트`/`플러그인` 목록 디자인 개선
  + 색 대비, 직관성 개선
  + 로그 디자인 변경
  + 초기 설정 화면 재디자인
  + 로고 화면 재디자인
+ `업데이트 확인` 메뉴 개선
+ 업데이트 완료 후 변경 사항 표시 추가
+ `Language`의 `callFunction` 함수가 반환값을 가지도록 변경
+ 오리지널 API 이름 변경
  + `LanguageManager` -> `Languages`
  + `ProjectManager` -> `Projects`
  + `PluginManager` -> `Plugins`
+ 기존 `Env` API -> `Platform` API로 변경
+ `Env` API 추가
  + 프로젝트 폴더의 `.env` 파일을 로드할 수 있습니다.
+ `Api.replyToID` 메소드 추가 `(도리도리ㅎ)`
  + 방 ID를 이용해 메세지 전송 가능

### 버그 수정 / 개선 사항
+ 팝업 통일성 개선
+ `STARTUP.info` 파일이 완전하지 않을 때 앱이 시작되지 않던 문제 수정
+ 프로젝트 목록 최적화 개선
+ 프로젝트 삭제 시 에러가 발생하던 문제 수정 `(AlphaDo, Yellu)`
+ 메세지 파싱 과정 최적화 개선
+ 일부 설정이 올바르게 로드되지 않던 문제 수정
+ 설정값이 설정 UI에 올바르게 반영되지 않던 문제 수정
+ 플러그인 아이콘이 올바르게 로드되지 않던 문제 수정
+ Legacy API 구현 개선
+ 기본 메세지 스펙에서 전송자가 올바르게 표시되지 않던 문제 수정
+ 프로젝트 아이콘 로딩 개선
+ 프로젝트 ID 해시 충돌 문제 수정 `(Yellu)`

### 도움 주신 분들
#### Contributors
+ [Ample(naijun0403)](https://github.com/naijun0403)
+ [shaper(shaper1234w)](https://github.com/shaper12340w)

`Yellu` - 버그 제보  
`AlphaDo` - 버그 제보  
`Ample` - PR 제공  
`NoMik` - 버그 제보, 후원  
`완두콩완두` - 개선 사항 제공, 후원  
`도리도리ㅎ` - 개선 사항 제공, 후원  
`개밟자` - 후원  
`시계톡톡` - 후원  
`삼플` - 버그 제보  
`미노` - 버그 제보  
`thfzm` - 버그 제보  
`shaper` - PR 제공, 후원  
`bgyooPtr` - 버그 제보  
`hahamini` - 버그 제보

도움 주신 모든 분들 감사합니다.
