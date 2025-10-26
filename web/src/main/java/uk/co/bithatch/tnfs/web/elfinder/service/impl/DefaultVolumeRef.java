package uk.co.bithatch.tnfs.web.elfinder.service.impl;

import java.util.Locale;
import java.util.Optional;

import uk.co.bithatch.tnfs.web.elfinder.core.Volume;

public final class DefaultVolumeRef {
	private final String id;
	private final Volume volume;
	private final Optional<Locale> locale;

	public DefaultVolumeRef(String id, Volume volume) {
		this(id, volume, Optional.empty());
	}
	
	public DefaultVolumeRef(String id, Volume volume, Optional<Locale> locale) {
		super();
		this.id = id;
		this.volume = volume;
		this.locale = locale;
	}
	
	public String getId() {
		return id;
	}
	
	public Volume getVolume() {
		return volume;
	}

	public Optional<Locale> getLocale() {
		return locale;
	}
	
	
}
