# 주간 계획 v4 — 날짜별 일정 / 드래그 / 기간 반복

## 변경된 사용 방식
- 선택/입력창 밖 클릭은 저장 전 선택을 취소합니다. 입력창 내부 입력은 선택을 유지합니다. Android에서는 다이얼로그 바깥 탭 및 뒤로 가기로 취소합니다(저장 중 제외).
- 시간표 일정명은 ExtraBold, 홈 위젯 일정명은 Bold로 표시합니다.
- 날짜마다 독립적으로 일정을 저장하고 월~일 7일 단위로 조회합니다.
- 이전 주 / 다음 주 / 이번 주 이동. 시간 범위는 07:00~24:00, 오후 시각은 12를 뺀 값(13시 → 1)으로 표시. 시스템 다크 모드 지원.
- 드래그 편집을 켜고 빈 시간대를 세로로 드래그하면 일정 추가창이 열립니다.
- 기존 블록을 드래그하면 날짜·시간 이동, 블록 아래쪽을 드래그하면 종료 시간 조절.
- 드래그는 30분 단위. 손을 놓으면 입력창에서 확인 후 저장하며 취소하면 기존 상태를 유지합니다.
- 드래그한 영역은 입력창이 열린 동안 테두리로 표시됩니다. 저장 성공 시 실제 일정으로 바뀌고, 취소 시 임시 표시만 제거됩니다. 저장 오류 시 선택은 유지됩니다.
- 시간 직접 입력에도 00분/30분만 허용합니다. 기존 비정규 간격 데이터는 자동으로 바꾸지 않으며 다음 수정 저장 때 30분 단위로 입력해야 합니다.
- 드래그 없이 + 일정 / 일정 목록으로도 모든 일정 편집 가능.
- 반복 추가는 시작일~종료일(두 날짜 포함) + 요일 복수 선택 + 시작/종료 시간으로 설정.
- 예: 2026-10-12~2026-10-23, 월·수, 10:00~11:30 → 12·14·19·21일 총 4건.
- 매일 반복하려면 7개 요일을 모두 선택합니다. 반복 범위는 한 번에 최대 730일 차이.
- 반복 일정 수정/이동은 해당 날짜 한 건에만 적용. 삭제는 해당 날짜 / 반복 전체 선택.
- 반복 기간 전체 수정, '이 날짜 이후 모두 수정', 여러 날짜를 가로질러 칠하기는 이 버전에 없음.

## 데이터 구조
ScheduleItem에 `dateEpochDay: Long?`와 `seriesId: String?`를 추가했습니다.
날짜는 java.time.LocalDate.toEpochDay로 저장하며 요일은 날짜와 일치해야 합니다.
반복 등록 시 실제 날짜별 occurrence를 생성하여 같은 seriesId로 묶습니다.
따라서 한 날짜를 수정하거나 삭제해도 다른 날짜는 그대로입니다.
전개 결과가 아닌 반복 규칙 자체를 별도로 저장하는 무한 반복 구조는 아닙니다.

앱 저장소는 Preferences DataStore + JSON입니다.
한 번의 edit 트랜잭션에서 전체 반복 날짜의 충돌 여부를 검사하고 한꺼번에 저장합니다.
어느 한 날짜라도 시간이 겹치면 충돌 날짜를 알려주며 하나도 저장하지 않습니다.
같은 시간이어도 날짜가 다르면 충돌하지 않고, 맞닿은 시간은 허용합니다.

## 구버전 데이터
날짜가 없던 기존 시간표는 최초 v4 실행 주의 해당 요일에 한 번만 배치합니다.
임의의 무기한 반복을 생성하지 않습니다. 강의는 원하는 기간으로 반복 등록해 주세요.
첫 실행 예시 데이터도 최초 주에만 고정되므로 다음 주로 이동해 자동 복제되지 않습니다.
마지막 일정을 삭제하면 빈 목록이 유지됩니다.

## 프로젝트 읽는 순서
1. `data/ScheduleItem.kt`: 날짜 모델, 검증, expandRepeat, validateBatch
2. `ui/WeeklyTimetable.kt`: 7일 시간표, 30분 선택, 이동·종료시간 드래그
3. `data/ScheduleRepository.kt`: 날짜 마이그레이션과 원자적 일괄 저장
4. `ScheduleViewModel.kt`: 저장 상태, 위젯 업데이트 요청
5. `MainActivity.kt`: 주 이동, 드래그 모드, 날짜/기간/요일 폼, 날짜별 예외 편집
6. `widget/TimetableWidget.kt`: 기기 날짜 기준 이번 주 7일 표시
7. `widget/WidgetRefreshWorker.kt`: 위젯 갱신과 제한적 재시도

전체 소스는 STEP_BY_STEP_CODE.md에도 순서대로 들어 있습니다.

## 위젯 동작
Glance 위젯은 앱과 같은 날짜별 저장소를 읽되 항상 기기 날짜 기준 이번 주를 표시합니다.
앱에서 탐색한 과거/미래 주가 위젯의 주를 바꾸지는 않습니다.
데이터 저장 후 직접 updateAll과 WorkManager 갱신을 요청합니다.
주가 바뀔 때도 다음 시스템 갱신에서 새 주를 읽도록 updatePeriodMillis=1800000을 지정했습니다.
30분은 요청 간격이며 실제 적용은 OS/런처에 따라 지연될 수 있습니다. 자정 정각 갱신 보장은 없습니다.
Glance UI에서 드래그는 제공하지 않으며 위젯을 누르면 앱을 엽니다.
4×4 목표 크기는 유지했지만 7열에서 긴 제목·짧은 일정의 글자는 잘릴 수 있습니다.
위젯 크기 확대 또는 앱의 일정 목록에서 상세 내용을 확인하세요.

## 빌드와 미리보기
예제 고정 버전: JDK 17 / Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 /
compile·target SDK 35 / min SDK 26 / Compose BOM 2024.12.01 / Glance 1.1.1.
최신 버전 조합을 주장하지 않습니다. Gradle Wrapper 바이너리는 포함하지 않았습니다.

Gradle 8.9 설치 환경에서:

```sh
gradle wrapper --gradle-version 8.9
```

Android Studio에서 루트를 열고 JDK 17, SDK 35를 설정하여 Sync합니다.
SDK 경로가 자동 설정되지 않으면 local.properties에 sdk.dir을 지정하세요.

```sh
./gradlew testDebugUnitTest assembleDebug
```

Windows에서는 gradlew.bat를 사용합니다.
`WeeklyTimetable.kt`의 TimetablePreview를 Split/Design에서 볼 수 있습니다.
실제 저장, 제스처, 홈 위젯은 기기/에뮬레이터에서 확인해야 합니다.

## 검증 상태
현재 작성 환경에는 Android SDK/Gradle이 없어 Kotlin 빌드, JUnit 실행, 기기 검증은 미실시입니다.
날짜별 충돌, 시작일·종료일 포함, 요일 선택, 범위와 ID 검사용 JUnit 소스를 제공했습니다.
XML 파싱, 파일 구성, ZIP 무결성과 대화 미리보기 JavaScript 구문만 정적으로 점검했습니다.
브라우저 실행 엔진도 설치되어 있지 않아 드래그 동작의 자동 실행 검증은 완료하지 못했습니다.

수동 확인 항목:
- 위/아래 방향 빈칸 드래그, 07:00와 24:00 경계, 제스처 취소
- 블록의 날짜 이동 및 아래쪽 길이 조절 → 취소하면 원복, 저장하면 반영
- 월/연도 경계를 넘는 주 이동 및 기기 날짜 변경
- 반복 기간 마지막 날짜 포함, 비선택 요일 제외, 하루짜리 기간
- 한 건 충돌 시 전체 반복 등록 취소
- 반복 한 날짜 수정/삭제 후 다른 날짜 유지, 전체 삭제 범위
- 구버전 설치 데이터의 날짜 마이그레이션과 앱 재실행 시 날짜 유지
- 큰 글꼴, 가로 화면, 7열의 가독성과 기기 터치 영역

## 참고
- When2meet: https://www.when2meet.com/
- todo mate 공식 등록: https://play.google.com/store/apps/details?id=com.undefined.mate
- Compose drag: https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling
- Glance: https://developer.android.com/develop/ui/compose/glance/build-ui

## 앱 아이콘 적용
승인한 크림 배경·세이지 그린 달력 PNG를 그대로 포함했습니다.
Manifest의 icon/roundIcon을 ic_launcher로 연결했고 Android 8 이상용 adaptive icon을 추가했습니다.
원본은 drawable-nodpi/ic_launcher_art.png이며 mipmap-nodpi에는 기본 아이콘을 제공합니다.
어댑티브 전경에 14dp 여백을 적용하여 런처 마스크에 대한 여유를 확보했습니다.
원본이 불투명하므로 전경에 크림 배경도 포함됩니다. 테마용 단색 아이콘은 별도로 제공하지 않습니다.
실제 원형/둥근 사각형 런처에서의 최종 표시와 APK 빌드는 기기 검증이 필요합니다.

## USB로 휴대폰에 설치
1. PC에 Android Studio와 Android SDK 35/JDK 17을 준비합니다.
2. 위 '빌드와 미리보기'의 Gradle Wrapper 준비를 마치고 프로젝트를 열어 Sync합니다.
3. 휴대폰에서 개발자 옵션과 USB 디버깅을 켭니다.
4. 데이터 통신 가능한 USB 케이블로 연결하고 휴대폰의 RSA 디버깅 허용을 승인합니다.
5. Android Studio 실행 대상에서 휴대폰을 선택하고 Run을 누르면 빌드 후 설치됩니다.
Windows에서 기기가 보이지 않으면 제조사의 USB 드라이버가 필요할 수 있습니다.
이 ZIP은 APK가 아니며, 케이블 연결만으로 자동 설치되지는 않습니다.
