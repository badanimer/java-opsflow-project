package com.opsflow.common.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

class PageQueryFactoryTest {

	private final PageQueryFactory factory = new PageQueryFactory(
			new OpsflowProperties(new OpsflowProperties.Retry(3, List.of(Duration.ofMillis(50))), new OpsflowProperties.Paging(10), null, null, null, null, null, null, null));

	@Test
	void theDefaultsAreTheFirstPageOfTenNewestFirst() {
		PageQuery query = factory.create(null, null, null, null, false);

		assertThat(query).isEqualTo(new PageQuery(10, null, Direction.NEXT, SortOrder.DESC));
	}

	@Test
	void theParametersAreReadWithoutRegardToCase() {
		String cursor = CursorCodec.encode(new Cursor("k", 5));

		PageQuery query = factory.create(4, cursor, "PREV", "Asc", true);

		assertThat(query).isEqualTo(new PageQuery(4, new Cursor("k", 5), Direction.PREV, SortOrder.ASC));
	}

	@Test
	void theLimitIsAtMostThePageSize() {
		assertThat(factory.create(10, null, null, null, false).limit()).isEqualTo(10);
		assertRefused(() -> factory.create(11, null, null, null, false));
		assertRefused(() -> factory.create(0, null, null, null, false));
		assertRefused(() -> factory.create(-1, null, null, null, false));
	}

	@Test
	void anUnknownDirectionOrOrderIsRefused() {
		assertRefused(() -> factory.create(null, null, "sideways", null, true));
		assertRefused(() -> factory.create(null, null, null, "random", true));
	}

	@Test
	void orderIsRefusedOnAListWhoseOrderIsFixed() {
		assertRefused(() -> factory.create(null, null, null, "asc", false));
	}

	@Test
	void goingBackNeedsACursor() {
		assertRefused(() -> factory.create(null, null, "prev", null, false));
	}

	@Test
	void aBrokenCursorIsRefused() {
		assertRefused(() -> factory.create(null, "garbage!", null, null, false));
	}

	@Test
	void theReadDirectionCombinesTheOrderWithTheDirectionOfTravel() {
		assertThat(new PageQuery(10, null, Direction.NEXT, SortOrder.DESC).ascendingRead()).isFalse();
		assertThat(new PageQuery(10, null, Direction.PREV, SortOrder.DESC).ascendingRead()).isTrue();
		assertThat(new PageQuery(10, null, Direction.NEXT, SortOrder.ASC).ascendingRead()).isTrue();
		assertThat(new PageQuery(10, null, Direction.PREV, SortOrder.ASC).ascendingRead()).isFalse();
	}

	private static void assertRefused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	void aListWithAFixedOrderOtherThanTheDefaultReadsInItsOrderAndRefusesAnOrderParameter() {
		PageQuery query = factory.createInOrder(null, null, null, null, SortOrder.ASC);
		assertThat(query.order()).isEqualTo(SortOrder.ASC);
		assertThat(query.ascendingRead()).isTrue();
		assertThat(factory.createInOrder(null, CursorCodec.encode(new Cursor("bob", 3)), "prev", null, SortOrder.ASC).ascendingRead()).isFalse();
		assertRefused(() -> factory.createInOrder(null, null, null, "asc", SortOrder.ASC));
		assertRefused(() -> factory.createInOrder(null, null, "prev", null, SortOrder.ASC));
	}

}
