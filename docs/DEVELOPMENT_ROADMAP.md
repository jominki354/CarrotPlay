# CarrotPlay 개발 로드맵

## 현재 상태 (2025-11-28)

### ✅ 완료된 기능
- [x] VirtualDisplay 생성 및 앱 실행
- [x] 실시간 터치 주입 (System API)
- [x] Back/Home/Recent 키 이벤트
- [x] TaskStackListener 기반 task 모니터링
- [x] VirtualDisplay 플래그 최적화 (TRUSTED 포함)

### 🔧 현재 작업 중
- [ ] 터치 성능 최적화 (Option A)

---

## 성능 최적화 계획

### Phase 1: 터치 성능 개선 ✅ (현재)

| 항목 | 변경 전 | 변경 후 | 효과 |
|-----|--------|--------|-----|
| syncInput (MOVE 전) | 매번 호출 | 호출 안 함 | IPC 90% 감소 |
| Throttle 간격 | 4ms | 8ms | 이벤트 50% 감소 |
| 터치 시각화 | 항상 | 옵션화 (TODO) | 리빌드 감소 |

**참고:** sync 제거로 인한 터치 정확도 영향 없음 (고정 레이아웃 기준)

---

## 향후 개발 계획

### Phase 2: PIP 크기/DPI 조절 기능

원본 앱 참조: `z7/q.java`, `w7/g.java`

```kotlin
// 구현 예정
fun resizeVirtualDisplay(displayId: Int, width: Int, height: Int, dpi: Int) {
    virtualDisplay.resize(width, height, dpi)
    windowManager.syncInputTransactions(true)  // resize 후 1회만 sync
}
```

**주의사항:**
- 크기 변경 직후 `syncInputTransactions(true)` 1회 호출 필요
- 드래그 이동 중에는 매 프레임 sync 필요

### Phase 3: PIP 창 드래그/이동

- 터치로 PIP 창 위치 변경
- 이동 중 sync 호출 필요 (Phase 1 최적화와 별개)

### Phase 4: 핀치 줌 (크기 조절)

- 두 손가락으로 PIP 크기 실시간 조절
- 줌 중 sync 호출 필요

### Phase 5: 앱 전환 애니메이션

- display 간 앱 이동 시 부드러운 전환
- 원본 앱의 애니메이션 로직 참조

---

## 기술 부채

### 현재 구조의 한계
```
Flutter Listener → Dart → MethodChannel → Kotlin → InputManager
```

원본 앱은 Kotlin에서 직접 터치를 받아 처리하여 더 빠름.

### 향후 개선 가능
- Native Touch Handler 구현 (Flutter 우회)
- BasicMessageChannel로 전환 (약간의 성능 향상)

---

## 참조 코드 (원본 앱)

| 기능 | 원본 파일 | 설명 |
|-----|----------|-----|
| 터치 주입 | `z7/q.java` | InputManager.injectInputEvent |
| VirtualDisplay | `z7/g.java`, `w7/g.java` | 생성/리사이즈/위치 |
| Task 관리 | `z7/m.java`, `z7/l.java` | TaskStackListener |
| 앱 실행 | `z7/m.java` n() | PendingIntent + ActivityOptions |
