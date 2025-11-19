package uk.co.bithatch.tnfs.server.windows;

import java.util.Optional;

import uk.co.bithatch.tnfs.server.AuthenticationType;
import uk.co.bithatch.tnfs.server.TNFSAuthenticator;
import uk.co.bithatch.tnfs.server.TNFSAuthenticatorFactory;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

public class WaffleTNFSAuthenticatorFactory implements TNFSAuthenticatorFactory {
	
	private final WindowsAuthProviderImpl waffle;

	public WaffleTNFSAuthenticatorFactory() {
		waffle = new WindowsAuthProviderImpl();
	}

	@Override
	public String name() {
		return "Windows System Users";
	}

	@Override
	public Optional<TNFSAuthenticator> createAuthenticator(String path, AuthenticationType... authTypes) {
		return Optional.of(new WaffleTNFSAuthenticator(waffle));
	}

}
