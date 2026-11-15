package com.opsflow.auth;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open event streams of the signed-in pages, so that a page learns at once when its account signs in
 * on another device (see One signed-in device in docs/database/database-design.md).
 *
 * Each stream belongs to one user and one login (the session id of its token). When a login commits, the
 * streams of the same user with another session id get the event {@value #REPLACED} and are closed. The
 * page then shows that it was signed out. The event is only the quick way: a page that misses it is still
 * refused with {@code SESSION_REPLACED} by the token check on its next request.
 *
 * The streams are held in the memory of this server. That is enough for one server; with several, a
 * login on one server must reach the streams on the others, for example through Redis publish and
 * subscribe (see the backend design).
 *
 * A stream is closed after {@link #STREAM_LIFETIME}, and the page opens a new one. Opening it checks the
 * token again, so a stream never outlives an account that was disabled or a token that expired by more
 * than that time. A comment line is sent every {@link #HEARTBEAT} seconds so that no proxy closes a quiet
 * connection, and so that a stream whose page has gone is found and dropped.
 */
@Component
public class SessionEvents {

	static final String REPLACED = "session-replaced";
	static final Duration STREAM_LIFETIME = Duration.ofMinutes(15);
	static final long HEARTBEAT = 25;

	private static final Logger log = LoggerFactory.getLogger(SessionEvents.class);

	private record Stream(String sessionId, SseEmitter emitter) {
	}

	private final Map<Long, Set<Stream>> streams = new ConcurrentHashMap<>();
	private final AuthRepository repository;

	SessionEvents(AuthRepository repository) {
		this.repository = repository;
	}

	/**
	 * Opens the stream of a page whose token was just accepted. A login that committed after the token
	 * check but before the stream was registered sent its event to nobody, so the newest session id is
	 * read once more after the registration, and the event is sent at once when it differs.
	 */
	SseEmitter open(long userId, String sessionId) {
		SseEmitter emitter = new SseEmitter(STREAM_LIFETIME.toMillis());
		Stream stream = new Stream(sessionId, emitter);
		streams.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(stream);
		emitter.onCompletion(() -> remove(userId, stream));
		emitter.onTimeout(() -> remove(userId, stream));
		emitter.onError(failure -> remove(userId, stream));
		try {
			// The first line makes the response start, so the page knows that the stream is open.
			emitter.send(SseEmitter.event().comment("open"));
		}
		catch (IOException gone) {
			remove(userId, stream);
		}
		if (!Objects.equals(repository.sessionId(userId), sessionId)) {
			replace(userId, stream);
		}
		return emitter;
	}

	/** Called after a login of the user committed: every stream of another login of the user is ended. */
	public void loggedIn(long userId, String newSessionId) {
		Set<Stream> ofUser = streams.get(userId);
		if (ofUser == null) {
			return;
		}
		for (Stream stream : ofUser) {
			if (!stream.sessionId().equals(newSessionId)) {
				replace(userId, stream);
			}
		}
	}

	/** The number of open streams, for the tests. */
	int openStreams() {
		return streams.values().stream().mapToInt(Set::size).sum();
	}

	@Scheduled(fixedDelay = HEARTBEAT, initialDelay = HEARTBEAT, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
	void heartbeat() {
		streams.forEach((userId, ofUser) -> {
			for (Stream stream : ofUser) {
				try {
					stream.emitter().send(SseEmitter.event().comment("ping"));
				}
				catch (IOException | IllegalStateException gone) {
					remove(userId, stream);
				}
			}
		});
	}

	private void replace(long userId, Stream stream) {
		remove(userId, stream);
		try {
			stream.emitter().send(SseEmitter.event().name(REPLACED).data("{}"));
			stream.emitter().complete();
		}
		catch (IOException | IllegalStateException gone) {
			// The page has gone already. Its next request, if any, is refused by the token check.
			log.debug("A replaced session stream was already closed");
		}
	}

	private void remove(long userId, Stream stream) {
		streams.computeIfPresent(userId, (id, ofUser) -> {
			ofUser.remove(stream);
			return ofUser.isEmpty() ? null : ofUser;
		});
	}

}
