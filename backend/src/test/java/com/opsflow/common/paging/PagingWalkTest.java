package com.opsflow.common.paging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Walks a list the way a client does, with the query a repository would run, and checks the rules of
 * paging by key: every row is seen once, the two ends have no cursor, and rows inserted between two page
 * reads neither repeat nor skip a row of the part that was already read (T-45 repeats this against the
 * database for the first real list).
 */
class PagingWalkTest {

	/** A row of a list sorted by a key, with the id as the tie-breaker. */
	record Row(String key, long id) {
		Cursor cursor() {
			return new Cursor(key, id);
		}
	}

	/** What a repository does for a PageQuery: compare with the cursor, sort, and read limit + 1 rows. */
	static List<Row> read(List<Row> table, PageQuery q) {
		Comparator<Row> ascending = Comparator.comparing(Row::key).thenComparingLong(Row::id);
		Comparator<Row> order = q.ascendingRead() ? ascending : ascending.reversed();
		return table.stream()
				.filter(r -> q.cursor() == null || (q.ascendingRead()
						? ascending.compare(r, new Row(q.cursor().key(), q.cursor().id())) > 0
						: ascending.compare(r, new Row(q.cursor().key(), q.cursor().id())) < 0))
				.sorted(order)
				.limit(q.fetchSize())
				.toList();
	}

	static PageResponse<Row> page(List<Row> table, int limit, String cursor, Direction direction) {
		PageQuery query = new PageQuery(limit, cursor == null ? null : CursorCodec.decode(cursor), direction, SortOrder.DESC);
		return PageResponse.of(read(table, query), query, Row::cursor);
	}

	static List<Row> rows(int count) {
		// Keys repeat (three rows share each key) so that the id decides between them.
		return IntStream.rangeClosed(1, count).mapToObj(i -> new Row("k" + String.format("%02d", (i + 2) / 3), i)).toList();
	}

	static List<Long> ids(PageResponse<Row> page) {
		return page.items().stream().map(Row::id).toList();
	}

	@Test
	void walkingForwardShowsEveryRowOnceNewestFirst() {
		List<Row> table = rows(25);
		List<Long> seen = new ArrayList<>();
		PageResponse<Row> page = page(table, 10, null, Direction.NEXT);
		int pages = 1;
		seen.addAll(ids(page));
		while (page.nextCursor() != null) {
			page = page(table, 10, page.nextCursor(), Direction.NEXT);
			seen.addAll(ids(page));
			pages++;
		}

		assertThat(pages).isEqualTo(3);
		assertThat(seen).containsExactlyElementsOf(IntStream.rangeClosed(1, 25).mapToObj(i -> (long) (26 - i)).toList());
	}

	@Test
	void theFirstPageHasNoPreviousCursorAndTheLastHasNoNextCursor() {
		List<Row> table = rows(25);

		PageResponse<Row> first = page(table, 10, null, Direction.NEXT);
		assertThat(first.prevCursor()).isNull();
		assertThat(first.nextCursor()).isNotNull();

		PageResponse<Row> second = page(table, 10, first.nextCursor(), Direction.NEXT);
		assertThat(second.prevCursor()).isNotNull();
		assertThat(second.nextCursor()).isNotNull();

		PageResponse<Row> last = page(table, 10, second.nextCursor(), Direction.NEXT);
		assertThat(last.items()).hasSize(5);
		assertThat(last.nextCursor()).isNull();
		assertThat(last.prevCursor()).isNotNull();
	}

	@Test
	void aListThatFillsExactlyOnePageHasNoNextCursor() {
		PageResponse<Row> only = page(rows(10), 10, null, Direction.NEXT);

		assertThat(only.items()).hasSize(10);
		assertThat(only.nextCursor()).isNull();
		assertThat(only.prevCursor()).isNull();
	}

	@Test
	void anEmptyListHasNoCursors() {
		PageResponse<Row> none = page(List.of(), 10, null, Direction.NEXT);

		assertThat(none.items()).isEmpty();
		assertThat(none.nextCursor()).isNull();
		assertThat(none.prevCursor()).isNull();
	}

	@Test
	void goingBackShowsTheSameRowsInTheSameOrder() {
		List<Row> table = rows(25);
		PageResponse<Row> first = page(table, 10, null, Direction.NEXT);
		PageResponse<Row> second = page(table, 10, first.nextCursor(), Direction.NEXT);
		PageResponse<Row> third = page(table, 10, second.nextCursor(), Direction.NEXT);

		PageResponse<Row> backToSecond = page(table, 10, third.prevCursor(), Direction.PREV);
		assertThat(ids(backToSecond)).isEqualTo(ids(second));
		assertThat(backToSecond.nextCursor()).isNotNull();
		assertThat(backToSecond.prevCursor()).isNotNull();

		PageResponse<Row> backToFirst = page(table, 10, backToSecond.prevCursor(), Direction.PREV);
		assertThat(ids(backToFirst)).isEqualTo(ids(first));
		assertThat(backToFirst.prevCursor()).isNull();
		assertThat(backToFirst.nextCursor()).isNotNull();
	}

	@Test
	void rowsInsertedAtTheTopBetweenTwoReadsNeitherRepeatNorSkipARow() {
		List<Row> table = new ArrayList<>(rows(25));
		PageResponse<Row> first = page(table, 10, null, Direction.NEXT);

		// New rows arrive on top of the list (the newest sort first), after the client read page one.
		table.add(new Row("k99", 26));
		table.add(new Row("k99", 27));
		PageResponse<Row> second = page(table, 10, first.nextCursor(), Direction.NEXT);

		// An offset would have shown two rows of page one again. A cursor continues right after the last row seen.
		assertThat(ids(second)).containsExactlyElementsOf(IntStream.rangeClosed(1, 10).mapToObj(i -> (long) (16 - i)).toList());
	}

	@Test
	void rowsInsertedInsideTheAlreadyReadPartDoNotShiftTheNextPage() {
		List<Row> table = new ArrayList<>(rows(25));
		PageResponse<Row> first = page(table, 10, null, Direction.NEXT);

		table.add(new Row("k08", 100)); // sorts inside the part page one already showed
		PageResponse<Row> second = page(table, 10, first.nextCursor(), Direction.NEXT);

		assertThat(ids(second)).containsExactlyElementsOf(IntStream.rangeClosed(1, 10).mapToObj(i -> (long) (16 - i)).toList());
	}

	@Test
	void aRowDeletedBetweenTwoReadsDoesNotDisturbTheNextPage() {
		List<Row> table = new ArrayList<>(rows(25));
		PageResponse<Row> first = page(table, 10, null, Direction.NEXT);
		Row lastShown = first.items().get(first.items().size() - 1);

		table.removeIf(r -> r.id() == lastShown.id()); // the cursor row itself is gone
		PageResponse<Row> second = page(table, 10, first.nextCursor(), Direction.NEXT);

		assertThat(ids(second)).containsExactlyElementsOf(IntStream.rangeClosed(1, 10).mapToObj(i -> (long) (16 - i)).toList());
	}

	@Test
	void rowsWithTheSameKeyAreOrderedByIdAndNoneIsLostAtAPageEdge() {
		List<Row> table = IntStream.rangeClosed(1, 7).mapToObj(i -> new Row("same", i)).toList();
		List<Long> seen = new ArrayList<>();
		PageResponse<Row> page = page(table, 3, null, Direction.NEXT);
		seen.addAll(ids(page));
		while (page.nextCursor() != null) {
			page = page(table, 3, page.nextCursor(), Direction.NEXT);
			seen.addAll(ids(page));
		}

		assertThat(seen).containsExactly(7L, 6L, 5L, 4L, 3L, 2L, 1L);
	}

}
