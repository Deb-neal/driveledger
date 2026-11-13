# 차계부 제출용 음성 중계 서버

2026-11-13: Render 배포, 인증/합성 음성 응답, HTTPS release 설치 및 온라인 UI 검사를 완료했다. 실제 마이크 재시험과 최종 APK 공개 게시는 남아 있다. [검증 기록](../docs/cloud-relay-validation-2026-11-13.md). 이 서버는 녹음 최대 8초를 OpenAI로 중계하며 지출 DB는 받지 않는다.

## Render 설정

루트 `render.yaml`에 동일한 설정이 있다. Blueprint 또는 공개 Git 저장소를 선택한 Web Service로 준비한다. 생성 버튼을 누르면 유료 컴퓨트/디스크 과금이 시작될 수 있으므로 최종 견적을 먼저 확인한다.

| 항목 | 값 |
| --- | --- |
| 저장소 / 브랜치 | `https://github.com/Deb-neal/driveledger` / `main` |
| 이름 / 지역 / 언어 | `driveledger-voice` / Singapore / Python 3 |
| Root Directory | 공란(저장소 루트) |
| 인스턴스 | 0.5 CPU / 512MB, `0.5c-512mb` |
| Build Command | `pip install -r server/requirements.txt` |
| Start Command | `gunicorn --config server/gunicorn.conf.py 'server.app:create_app()'` |
| Health Check Path | `/health` |
| Auto Deploy | Off |
| Persistent Disk | 1GB, Mount Path `/var/data` |

환경변수:

- `PYTHON_VERSION=3.14.6`
- `OPENAI_API_KEY`: 서버의 비밀 환경변수에만 입력. APK/저장소/URL에 포함하지 않는다.
- `VOICE_RELAY_TOKEN`: 무작위 64자리 소문자 hex. 서버와 로컬 `voice-release.properties`에 같은 값을 넣는다. 공개 APK에서는 추출 가능하며 개인 계정 인증을 대신하지 않는다.
- `VOICE_USAGE_DB=/var/data/voice-usage.sqlite3`
- `VOICE_TOTAL_LIMIT=1000`, `VOICE_DAILY_LIMIT=200`, `VOICE_MINUTE_LIMIT=10`

컴퓨트 월 $7 + 디스크 1GB 월 $0.25, 기본 월 $7.25 예상. OpenAI, 세금/환율/초과 사용은 별도이며 배포 화면 금액이 우선이다. 무료 인스턴스의 휴면 재시작은 앱 응답 제한과 맞지 않는다. [공식 가격](https://render.com/pricing), [Blueprint](https://render.com/docs/blueprint-spec).

## 제한과 운영

- `POST /transcribe`: `Authorization: Bearer <relay token>`, `Content-Type: audio/wav`. 16kHz/모노/PCM16, 0.25~8초, 최대 300KB. 원본·인식문은 메모리에서만 처리한다.
- 인증/형식/무음 확인 후 SQLite에서 사용량을 원자적으로 예약한 다음 OpenAI를 한 번 호출한다. 실패한 요청도 상한에서 차감하며 자동 재시도하지 않는다.
- 한 프로세스 내 동시 변환 1개. 배포 설정은 worker 1개/instance 1개다. SQLite 총량은 재시작 후에도 유지된다. 일/분 기준은 UTC이며 제한에 도달하면 429로 실패하고 앱에서 직접 입력할 수 있다.
- 디스크/DB를 삭제하거나 경로를 바꾸면 사용량 이력이 사라진다. 심사 중 DB를 초기화하거나 상한을 무심코 올리지 않는다. DB 오류 시 외부 API를 호출하지 않는다.
- 접근 로그를 비활성화하고 예외 응답은 고정 문구로 제한한다. 호스팅 사업자의 네트워크 메타데이터 및 OpenAI의 별도 보관 정책까지 통제하는 것은 아니다.
- 공통 토큰 유출 시 사용량을 소진시키는 서비스 거부가 가능하다. 심사용 소규모 배포의 한계다. 일반 서비스로 확대할 때 사용자별 인증/제한을 도입한다.
- `GET /health`는 서버 프로세스 상태만 확인한다. OpenAI 크레딧·인식 품질·남은 사용량까지 보장하지 않는다. 사용량/토큰을 공개하지 않는다.
- OpenAI 키는 운영자가 Render 설정에서 교체할 수 있다. relay 토큰을 교체하면 APK도 새 토큰으로 다시 빌드해야 한다.

## 앱 연결 및 완료 검사

배포 주소 확정 후, Git 제외 `voice-release.properties`를 저장소 루트에 작성한다. 아래 값은 예시다.

```properties
endpoint=https://YOUR-SERVICE.onrender.com/transcribe
relayToken=YOUR_64_CHARACTER_LOWERCASE_HEX_TOKEN
```

이 파일이 있으면 debug/release가 HTTPS 온라인 빌드가 된다. debug의 `-PlocalOnlineVoice=true`는 Mac 로컬 테스트를 우선한다. 파일이 없으면 기존 오프라인 빌드다. `release-signing.properties`도 준비하고 `./gradlew :app:assembleRelease`를 실행한다. 실제 OpenAI 키를 relayToken에 넣지 않는다.

1. 배포 URL의 `/health` 200 및 인증 없는 `/transcribe` 401 확인.
2. 실제 release APK를 별도 테스트 환경에 설치. 기존 debug 앱을 삭제해서 사용자 기록을 잃지 않는다.
3. Mac relay와 `adb reverse` 없이 동의 → 음성 → 항목/금액 → 직접 저장 → 합계 흐름 확인.
4. 인터넷 연결 실패/상한 소진 안내와 직접 입력 경로 확인. 그 APK만 최종 제출한다.

## 비용 없는 검사

```sh
python3 -m unittest server.test_app -v
python3 -m unittest discover -s scripts -p 'test_*.py'
```

서버 검사는 가짜 상류를 사용한다. 인증 거절·잘못된 WAV·무음·동시 요청·재시작 상한·일/분 갱신·총량 유지·DB 오류·상류 오류와 비밀값 비노출을 확인한다. 실제 API 정확도 검사는 포함하지 않는다.
