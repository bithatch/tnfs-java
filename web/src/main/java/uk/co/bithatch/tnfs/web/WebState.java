/*
 * Copyright © 2025 Bithatch (brett@bithatch.co.uk)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package uk.co.bithatch.tnfs.web;

import java.io.Closeable;
import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.sshtools.uhttpd.UHTTPD.Session;

public final class WebState  {

	private final static String LAST_ACCESS = "webstate.lastAccess";
	private final static String EXPIRY_TIMER = "webstate.expiryTimer";
	private final static String USER = "webstate.user";
	private final static String LOCALE = "webstate.locale";
	
	private final static Map<Session, WebState> map = new ConcurrentHashMap<>();
	
	private final static ScheduledExecutorService expiryTimer = Executors.newSingleThreadScheduledExecutor();
	private static final long DEFAULT_EXPIRY_MINUTES = Long.parseLong(System.getProperty("tnfsjd-web.sessionTimeoutMins", "15"));
	
	private final Session session;
	private final Map<String, Object> env = new ConcurrentHashMap<String, Object>();
	
	public static WebState get() {
		return get(true).orElseThrow(() -> new IllegalStateException("No session."));
	}
	
	public static Optional<WebState> get(boolean create) {
		return Session.get(create).map(s -> get(s));
	}
	
	public static WebState get(Session session) {
		var state = map.get(session);
		if(state == null) {
			state = new WebState(session);
			map.put(session, state);
		}
		state.set(LAST_ACCESS, Instant.now());
		
		var fstate = state;
		
		updateExpiryTimer(fstate, Duration.ofMinutes(DEFAULT_EXPIRY_MINUTES));
		
		return state;
	}

	private static void updateExpiryTimer(WebState fstate, Duration duration) {
		var timer = fstate.set(EXPIRY_TIMER, expiryTimer.schedule(() -> {
			fstate.invalidate();
		}, 60, TimeUnit.SECONDS));
		
		if(timer != null) {
			timer.cancel(false);
		}
	}
	
	WebState(Session session) {
		this.session = session;
	}
	
	public Instant lastAccess() {
		return get(LAST_ACCESS);
	}
	
	public void timeout(Duration duration) {
		updateExpiryTimer(this, duration);
	}
	
	public Optional<Principal> user() {
		return Optional.ofNullable(get(USER));
	}
	
	public Optional<Locale> localeOr() {
		return Optional.ofNullable(get(LOCALE));
	}
	
	public Locale locale() {
		return localeOr().orElse(Locale.getDefault());
	}
	
	@SuppressWarnings("unchecked")
	public <V> V get(String key) {
		return (V)env.get(key);
	}
	
	@SuppressWarnings("unchecked")
	public <V> V remove(String key) {
		return (V)env.remove(key);
	}
	
	@SuppressWarnings("unchecked")
	public <V> V get(String key, V defaultValue) {
		return env.containsKey(key) ? (V)env.get(key) : defaultValue;
	}
	

	@SuppressWarnings("unchecked")
	public <V> V get(String key, Supplier<V> defaultValue) {
		return env.containsKey(key) ? (V)env.get(key) : defaultValue.get();
	}
	
	@SuppressWarnings("unchecked")
	public <V> V set(String key, V val) {
		return (V)env.put(key, val);
	}
	
	public Map<String, Object> env() {
		return env;
	}
	
	public WebState locale(Locale locale) {
		env.put(LOCALE, locale);
		return this;
	}
	
	@SuppressWarnings("unused")
	public void authenticate(Principal user) {
		user().ifPresentOrElse(u -> {
			throw new IllegalStateException("Already authenticated.");
		}, () -> set(USER, user));
	}
	
	public Session session() {
		return session;
	}

	public void invalidate() {
		ScheduledFuture<?> timer = remove(EXPIRY_TIMER);
		if(timer != null) {
			timer.cancel(false);
		}
		
		env.values().forEach(v -> {
			if(v instanceof Closeable closeable) {
				try {
					closeable.close();
				} catch (IOException e) {
				}
			}
		});
		env.clear();
		map.remove(session);
	}

	public boolean authenticated() {
		return user().isPresent();
	}
}
