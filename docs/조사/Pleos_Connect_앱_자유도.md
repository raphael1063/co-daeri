# Pleos Connect가 외부 앱에 주는 자유도 (2026-11-10 조사)

공식 개발 문서(document.pleos.ai)의 설계 가이드, 디자인 가이드, 심사 지침, FAQ와 SDK 샘플의 선언 파일을 읽고 정리했다. 이 문서를 쓴 시점에는 에뮬레이터로 직접 확인한 것이 없었다. 그 뒤 에뮬레이터에서 확인한 내용은 [검증](../검증/README.md)에 있다. "문서에 없음"은 금지가 아니라 근거를 찾지 못했다는 뜻이다.

## 1. 한눈에

| 하고 싶은 것 | 가능 여부 | 근거 |
|---|---|---|
| 내 앱 화면을 앱 영역에 띄우기 | 가능 | 앱 영역에 창 1~2개. 세로형(창 1개)과 가로형(창 2개 폭) |
| 다른 앱(내비) 옆에 나란히 있기 | 가능 | 앱 2개 동시 표시. 단 새 앱이 실행되면 내 앱이 밀려나 화면에서 사라질 수 있음 |
| 다른 앱 위에 떠 있는 창(오버레이) | 문서에 없음 | 외부 앱용 오버레이 API나 허용 문장을 찾지 못함. 시스템 UI(상태 표시줄, 하단 바 패널, 키보드)만 "앱보다 상위 레이어"라고 적혀 있음 |
| 운전 정보 영역(Driving view)에 표시 | 불가 | "앱이 임의로 가리거나 종료할 수 없습니다" |
| 화면에 없을 때 소리로 안내 | 가능 | 화면에서 사라져도 종료되지 않고 백그라운드에 남음. 오디오 포커스 정책을 따라야 함 |
| 화면에 없을 때 알림 띄우기 | 가능해 보임(미확인) | 디자인 가이드에 시스템 알림의 "App icon: 앱의 알림을 받을 때" 유형과 상태 표시줄의 알림 센터가 있음. 주행 중 화면에 튀어나오는지는 미확인 |
| 홈 화면 위젯 | 문서에 없음 | 위젯 디자인 컴포넌트는 있으나 외부 앱이 위젯을 등록하는 방법은 공개 문서에 없음 |
| 백그라운드에서 계속 실행 | 제한적 가능 | "백그라운드에서도 지속할 작업은 실행 조건과 중단·재개 정책을 정의". 상주, 타이머, 폴링은 최소화. 주행 중 CPU 5% 미만(내비 제외), RAM 400MB 이하 |
| 시동을 끈 뒤 실행 | 사실상 불가 | 시동 OFF 뒤 Suspend(저전력) 상태. "일반 서비스는 중단", 앱은 "언제든 중단되고 이후 다시 시작될 수 있는 상태"로 간주 |
| 차에 타면 자동 실행 | 문서에 없음 | 재탑승 시 기본 화면은 내비(와이드 화면은 내비 + 마지막 미디어 앱). 외부 앱 자동 시작 언급 없음 |
| 음성 비서가 내 앱 기능 호출 | 가능(승인 필요) | Gleo App Schema. 기능 최대 3개, 스키마 검토·승인 뒤 활성화 |
| 주행 중 화면 사용 | 선언해야 가능 | 앱이 주의 분산 최적화(DO)를 선언하지 않으면 주행 중 "사용 불가". 선언해도 키보드, 설정, 긴 글은 주행 중 제한 |
| 차량 신호 읽기 | 넓게 가능 | 기어, 좌석, 문, 창문, 조명, 경고등, 타이어, 연료·배터리, 공조, 주행거리, 조향, 내비 경로 |
| 차량 제어 | 가능(권한 승인) | 공조, 시트, 창문, 문, 조명, 미러 등 제어 권한이 샘플 선언 파일에 있음. 사전 승인 필요 |
| 실제 차속 | 가능 | FAQ: 안드로이드 차량 API(CarPropertyManager)의 차속 값을 읽는 방법 안내. 샘플에 속도 권한도 있음. 이전에 "속도는 없다"고 한 것은 Vehicle SDK 도메인 기준이었고 정정함 |
| 전화, 문자, 주소록, 일부 센서 | 제한될 수 있음 | "모바일용 API가 제공되지 않거나 제한될 수 있습니다" |
| 차량 하드웨어 직접 접근 | 불가 | SDK의 표준 API로만 |
| 차 밖에서 차량 상태 조회(클라우드) | 별도 API 있음 | Vehicle Data API: 문 잠금, 시동 상태, 누적 주행거리, 위치를 서버에서 조회. 차주 본인은 API 키 발급, 사업자는 차주 동의 필요 |

## 2. 화면 모델

- 화면은 상태 표시줄, 운전 정보 영역, 하단 바(앱 서랍, 즐겨찾기, 공조), 앱 영역으로 나뉜다.
- 표준 화면(Connect-S)은 앱 2개일 때 1:1, 와이드(Connect-W)는 2:1 또는 1:2.
- 새 앱은 주 창에 뜬다. 표준 화면에서 세로형 앱 2개가 떠 있으면 새 앱이 주 창을 차지한다. 와이드에서는 새 앱이 주 창에 뜨고 기존 주 창 앱은 보조 창으로, 보조 창 앱은 사라진다.
- 사용자는 세 손가락 쓸기나 창 막대로 위치·크기를 바꾸고 앱을 닫는다. 내비는 닫을 수 없다.
- 선언 파일에서 화면 방향을 지정하지 않으면 세로형(창 1개)으로 뜬다.

## 3. 코-대리 설계에 주는 의미

1. **내비 옆 세로형 창이 기본 자리다.** 기사가 순정 내비를 쓰면 내비 + 코-대리로 나란히 놓인다. 다른 앱 위에 띄우는 방식은 근거가 없으므로 기획에 넣지 않는다.
2. **화면에서 밀려나도 음성 안내는 계속된다.** 주차 안내의 본체가 음성인 현재 설계와 맞는다. 화면이 사라졌을 때 다시 불러오는 수단으로 알림을 쓸 수 있는지는 에뮬레이터에서 확인한다.
3. **주행 중 사용 선언(DO)이 필수다.** 선언하지 않으면 주행 중 화면이 막혀 "다음 안내" 버튼을 쓸 수 없다. 구현에 반영 요청.
4. **시동을 끄면 앱이 멈춘다.** 귀가 카드는 시동을 끄기 전에 올려야 한다. 올라갔는지 아닌지를 화면에 그대로 적는다.
5. **차에 타면 저절로 카드가 뜨는 동작은 약속하지 않는다.**
6. **다음 단계 후보: Vehicle Data API.** 서버가 차 밖에서 문 잠금과 시동 상태를 읽을 수 있다. "마무리 뒤 잠겼는지"와 "시동을 끈 뒤 상태"를 차량 앱 없이 채울 수 있는 경로다. 차주별 API 키 발급 또는 사업자 계약이 필요해 이번 구현에는 넣지 않고 확대 계획에만 적는다.

## 4. 조사 시점에 확인하지 못한 것

- 다른 앱 위에 그리기 권한이 주어지는지.
- 앱 알림이 주행 중 화면에 표시되는지, 누르면 앱이 앞으로 오는지.
- 부팅 완료 시 자동 시작이 되는지.
- 시동 OFF 신호 직후 네트워크 전송에 주어지는 시간.
- 좌석별 화면(조수석, 뒷좌석 화면)에 외부 앱이 뜨는지. 새 플랫폼 SDK 문서에 "조수석 앱 패널"이라는 값이 있으나 공모전 SDK 범위 밖이다.

## 5. 출처

- 앱 구조 및 동작 모델: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model
- 화면 구성, 창 정책: https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout , https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/window
- 성능·리소스: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt
- 하드웨어 접근 정책: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture
- 주행 규제: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
- Suspend 모드: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/suspend-mode-audio-focus
- FAQ(차속 등): https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq
- 시스템 알림, 위젯 컴포넌트: https://document.pleos.ai/docs/connect/guide/docs-design/components/notification/system-notification , https://document.pleos.ai/docs/connect/guide/docs-design/components/widgets
- Vehicle Data API: https://document.pleos.ai/api-reference/vehicle-data-api/intro
- Gleo App Schema: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema
