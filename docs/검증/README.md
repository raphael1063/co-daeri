# 에뮬레이터 확인 기록

코-대리 앱을 Pleos Connect Emulator 2.1.0(Android 14, API 34, 가로 2560 x 1440, 차종 IONIQ 5)에 설치해 확인한 내용입니다. 예약과 종료 확정, 결제 표시, 알림 판단은 시연용 서버가 맡았습니다. 차의 신호는 에뮬레이터의 Virtual Console과 `adb emu geo fix`(위치)로 바꿨습니다.

## 확인한 것

| 항목 | 확인한 내용 | 자료 |
| --- | --- | --- |
| 번호 연결 | 차 화면의 QR 또는 연결 코드로 차와 번호가 연결되고 화면이 홈으로 넘어감 | [con_bind.png](con_bind.png) |
| 예약 인계 | 예약 뒤 15초 안에 차 화면에 배정된 기사님, 목적지, 확정 요금이 뜸 | [../영상/screens/car_handover.png](../영상/screens/car_handover.png) |
| 운행 기록 | 기어가 P를 벗어나면 운행이 시작되고 목적지가 순정 내비게이션으로 넘어감. P단 정차 뒤 다시 출발하면 같은 운행으로 이어짐 | [car_driving.png](../영상/screens/car_driving.png), [car_navi.png](../영상/screens/car_navi.png), [car_stop.png](../영상/screens/car_stop.png) |
| 집 주차 안내 | 위치가 집 반경 안으로 들어오면 안내가 스스로 시작됨 | [car_guide.png](../영상/screens/car_guide.png) |
| 종료와 결제 | 운전석이 비면 차가 종료 신호를 보내고 서버가 종료를 확정, 자동 결제(시연용) 표시 | [car_finish.png](../영상/screens/car_finish.png), [car_final.png](../영상/screens/car_final.png), [web_paid.png](../영상/screens/web_paid.png) |
| 귀가 카드 | 차 화면과 차주 휴대폰 페이지에 도착 시각, 주차 위치, 인계 전후 차 상태가 남음 | [car_card.png](../영상/screens/car_card.png), [web_card.png](../영상/screens/web_card.png) |
| 기사님 미도착 재배정 | 도착 알림 뒤 운전석 착석이 확인되지 않으면 다른 기사님으로 다시 배정되고 차 화면의 기사님도 바뀜 | [verify_reassign.mp4](verify_reassign.mp4), [verify_reassign_car.png](verify_reassign_car.png), [verify_reassign_car_after_switch.png](verify_reassign_car_after_switch.png), [verify_reassign_phone.jpg](verify_reassign_phone.jpg) |
| 경로 이탈 | 위치를 목적지 반대쪽으로 옮기면 차주 페이지에 알림, 돌아오면 정리됨 | [verify_route.mp4](verify_route.mp4), [verify_route_car.png](verify_route_car.png), [verify_route_phone.jpg](verify_route_phone.jpg) |
| 긴 정차 | 목적지가 아닌 곳에 P단으로 5분 넘게 서 있으면 알림, 다시 출발하면 정리됨 | [verify_stop.mp4](verify_stop.mp4), [verify_stop_car.png](verify_stop_car.png), [verify_stop_phone.jpg](verify_stop_phone.jpg) |
| 문 잠금 | 종료 확정 뒤 5분 동안 잠금 신호가 없으면 알림, 잠그면 그 시각을 적고 정리됨 | [verify_unlock.mp4](verify_unlock.mp4), [verify_unlock_car.png](verify_unlock_car.png), [verify_unlock_phone.jpg](verify_unlock_phone.jpg) |
| 목소리로 대리 부르기 | 목적지를 말하면 요금을 확인하고, "불러 줘"라고 답해야 예약이 잡힘. 못 알아들으면 들은 말을 보여 주고 다시 물음 | [car_voice_listen.png](../영상/screens/car_voice_listen.png), [car_voice_confirm.png](../영상/screens/car_voice_confirm.png), [car_voice_reprompt.png](../영상/screens/car_voice_reprompt.png) |
| 심사용 콘솔 | PC 브라우저에서 연결 코드로 연결해 호출, 재배정, 경로 이탈, 긴 정차, 종료, 문 잠금 알림까지 순서대로 진행됨 | 저장소 README의 5분 확인 순서 |
| 그 밖 | 재부팅 뒤 앱을 열지 않아도 예약 알림이 뜸. 화면이 가려져 있어도 서버 조회와 신호 전송이 이어짐. 서명한 release APK의 설치와 실행 | |

확인 녹화(`verify_*.mp4`)는 용량을 줄이려고 720p로 다시 인코딩한 것입니다.

## 확인하지 못한 것

- 안내 음성의 실제 소리. 재생 요청이 나간 기록만 확인했습니다.
- 사람 목소리의 음성 인식. 에뮬레이터에 합성 음성을 넣어 시험했습니다.
- 실제 차량. 시동이 꺼진 상태의 동작, 좌석 착석 값의 방향(에뮬레이터에서는 뒤집혀 읽혀, 앱이 첫 출발 때 방향을 배웁니다), 차량 신호 권한의 승인 범위는 실차와 입점 절차에서 확인해야 합니다.

## 에뮬레이터에서 알게 된 것

- 차종은 IONIQ 5만 제공되어 다른 차종으로 바꿀 수 없었습니다.
- 문 잠금은 네 문이 하나로 묶여 읽힙니다.
- 운행을 시작하면 순정 내비게이션이 앞으로 나와 앱 화면을 가릴 수 있습니다. 앱은 가려져도 계속 동작합니다.
