package io.hhplus.tdd.point.service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import io.hhplus.tdd.point.exception.PointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PointServiceTest {

    @InjectMocks
    private PointService pointService;

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    private long id;
    private long currentPoint;

    // 공통 설정
    @BeforeEach
    void setUp() {
        id = 1L; // 기본 ID
        currentPoint = 1000; // 기본 포인트
    }

    // 포인트 조회
    private void pointInquiry(long id) {
        UserPoint userPoint = new UserPoint(id, currentPoint, System.currentTimeMillis());
        when(userPointTable.selectById(id)).thenReturn(userPoint);
    }

    // 사용자가 존재하지 않음
    private void userNotExist(long id) {
        when(userPointTable.selectById(id)).thenReturn(null);
    }

    @Test
    @DisplayName("존재하지 않는 ID인 경우")
    void notExsistIdwhs() {
        // given : 입력한 ID가 존재하지 않다면
        long nonExistingId = 999999999999L;
        userNotExist(nonExistingId);

        // when, then : 예외 발생
        assertThatThrownBy(() -> pointService.selectPointById(999999999999L))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("입력한 ID가 존재하지 않습니다.");
    }

    @Test
    @DisplayName("유효하지 않은 패턴의 ID가 입력된 경우")
    void invalidIdFormat() {
        // then : 예외 발생
        assertThatThrownBy(() -> pointService.selectPointById(-1L))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("ID는 0 이상의 숫자여야 합니다.");
    }

    @Test
    @DisplayName("0을 충전하려는 경우")
    void chargeZeroPoint() {
        // when, then : 0원을 충전하려고 한다면 예외 발생
        assertThatThrownBy(() -> pointService.charge(id, 0))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("충전 금액은 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("1000 포인트 충전 시 포인트 증가 확인")
    void chargeThousandPoint() {
        // given : 사용자 ID가 1L이고 기존 포인트가 2000인 상태에서 1000 충전
        long chargePoint = 1000;
        long totalPoint = currentPoint + chargePoint;

        pointInquiry(id);

        // when :  1000원 충전
        pointService.charge(id, chargePoint);

        // 메서드 호출 검증(verify)
        verify(userPointTable).insertOrUpdate(id, totalPoint);
        UserPoint updateUserPoint = new UserPoint(id, totalPoint, System.currentTimeMillis());
        assertThat(updateUserPoint.point()).isEqualTo(totalPoint);
    }

    @Test
    @DisplayName("포인트 사용하려고 하는데 아이디가 없는 경우")
    void cannotUsePointsIfIdDoesNotExist() {
        // given : 존재하지 않는 않는 ID로 포인트를 사용하려고 하는 경우
        long notExistId = 99999999L;
        int usePoint = 1000;

        userNotExist(notExistId);

        //when, then : 예외 발생
        assertThatThrownBy(() -> pointService.use(notExistId, usePoint))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("입력한 ID가 존재하지 않습니다.");
    }

    @Test
    @DisplayName("잔여 포인트보다 더 많이 사용하고자 하는 경우")
    void exceededPoint() {
        // given : 사용자의 포인트가 1000인데 10000을 사용하고자 하는 경우
        int wantUsePoint = 10000;

        pointInquiry(id);

        // When, then : 예외 발생
        assertThatThrownBy(() -> pointService.use(id, wantUsePoint))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("잔여 포인트가 부족합니다.");
    }

    @Test
    @DisplayName("정상적으로 포인트를 사용한 경우")
    void usePointSuccessfully() {
        // given : 500 사용
        long usePoint = 500;
        long remainPoint = currentPoint - usePoint;

        pointInquiry(id);

        // when : 포인트 사용
        pointService.use(id, usePoint);

        // then : 포인트 차감 확인
        verify(userPointTable).insertOrUpdate(id, remainPoint);
    }

    @Test
    @DisplayName("포인트 사용 시 포인트 내역에 기록되는지 확인")
    void useAndRecordPointHistory() {
        // given : 500 포인트 사용
        long usePoint = 500;
        pointInquiry(id);

        // when : 포인트 사용
        pointService.use(id, usePoint);

        // then : 포인트 내역에 사용 기록이 추가되는지 확인
        verify(userPointTable).insertOrUpdate(eq(id), eq(currentPoint - usePoint));
        verify(pointHistoryTable).insert(eq(id), eq(usePoint), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("음수를 포인트로 사용하려는 경우")
    void usingNegativePoint() {
        // given : 음수를 사용

        pointInquiry(id);

        // when, then : 예외 발생
        assertThatThrownBy(() -> pointService.use(id, -100))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("사용할 포인트는 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("정상적으로 포인트를 충전하는 경우")
    void chargePointSuccessfully() {
        // given : 정상적인 충전 포인트가 입력되었을 때
        long chargePoint = 200;
        long totalPoint = currentPoint + chargePoint;

        pointInquiry(id);

        // when : 포인트 충전
        pointService.charge(id, chargePoint);

        // then : 포인트가 정상적으로 충전되었는지 확인
        verify(userPointTable).insertOrUpdate(id, totalPoint);
    }

    @Test
    @DisplayName("포인트 충전 시 포인트 내역이 기록되는지 확인")
    void chargePointRecordHistory() {
        long chargePoint = 1000;
        pointInquiry(id);

        pointService.charge(id, chargePoint);

        // then : 포인트 내역에 충전 기록이 추가 되는지 확인
        verify(userPointTable).insertOrUpdate(eq(id), eq(currentPoint + chargePoint));
        verify(pointHistoryTable).insert(eq(id), eq(chargePoint), eq(TransactionType.CHARGE), anyLong());
    }
}
