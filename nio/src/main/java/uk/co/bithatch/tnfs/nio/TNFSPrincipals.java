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
package uk.co.bithatch.tnfs.nio;

import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.util.Objects;
import java.util.Optional;

public class TNFSPrincipals {
	

	public final static class TNFSGroup implements GroupPrincipal {

		private final String bestGroupName;
		private final Optional<String> group;
		private final Optional<Integer> gid;

		TNFSGroup(String bestUsername, Optional<String> group, Optional<Integer> uid) {
			super();
			this.bestGroupName = bestUsername;
			this.group = group;
			this.gid = uid;
		}

		@Override
		public String getName() {
			return bestGroupName;
		}

		public int uid() {
			return gid.orElse(-1);
		}

		public Optional<Integer> uidOr() {
			return gid;
		}

		public String group() {
			return group.orElse(null);
		}

		public Optional<String> groupOr() {
			return group;
		}

		@Override
		public int hashCode() {
			return gid.isPresent() ? Objects.hashCode(gid.get()) : bestGroupName.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof TNFSGroup ? 
				( gid.isPresent() && ((TNFSGroup)obj).gid.isPresent() ? Objects.equals(gid.get(), ((TNFSGroup)obj).gid.get()) : Objects.equals(bestGroupName, ((TNFSGroup)obj).bestGroupName) ) :
				false;
		}
	}

	public final static class TNFSUser implements UserPrincipal {

		private final String bestUsername;
		private final Optional<String> username;
		private final Optional<Integer> uid;

		TNFSUser(String bestUsername, Optional<String> username, Optional<Integer> uid) {
			super();
			this.bestUsername = bestUsername;
			this.username = username;
			this.uid = uid;
		}

		@Override
		public String getName() {
			return bestUsername;
		}

		public int uid() {
			return uid.orElse(-1);
		}

		public Optional<Integer> uidOr() {
			return uid;
		}

		public String username() {
			return username.orElse(null);
		}

		public Optional<String> usernameOr() {
			return username;
		}

		@Override
		public int hashCode() {
			return uid.isPresent() ? Objects.hashCode(uid.get()) : bestUsername.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof TNFSUser ? 
				( uid.isPresent() && ((TNFSUser)obj).uid.isPresent() ? Objects.equals(uid.get(), ((TNFSUser)obj).uid.get()) : Objects.equals(bestUsername, ((TNFSUser)obj).bestUsername) ) :
				false;
		}
	}
}
