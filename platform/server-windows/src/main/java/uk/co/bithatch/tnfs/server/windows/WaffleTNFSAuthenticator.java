package uk.co.bithatch.tnfs.server.windows;

import java.security.Principal;
import java.util.Optional;

import com.sun.jna.platform.win32.Win32Exception;

import uk.co.bithatch.tnfs.lib.TNFSFileAccess;
import uk.co.bithatch.tnfs.server.TNFSAuthenticator;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

public class WaffleTNFSAuthenticator implements TNFSAuthenticator {
	
	private WindowsAuthProviderImpl provider;

	public WaffleTNFSAuthenticator(WindowsAuthProviderImpl provider) {
		this.provider = provider;
	}

	@Override
	public Optional<Principal> authenticate(TNFSFileAccess fs, Optional<String> username, Optional<char[]> password) {
		if(username.isPresent()) {
			var uname = username.get();
			try {
				provider.logonUser(uname, new String(password.get()));
				return Optional.of(new Principal() {
					@Override
					public String getName() {
						return uname;
					}
				});
			}
			catch(Win32Exception w32e) {
			}
		}
		return Optional.empty();
	}

}
