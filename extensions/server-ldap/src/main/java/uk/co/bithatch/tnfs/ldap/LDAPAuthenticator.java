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
package uk.co.bithatch.tnfs.ldap;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.AuthenticationException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import uk.co.bithatch.tnfs.lib.TNFSFileAccess;
import uk.co.bithatch.tnfs.server.TNFSAuthenticator;

import java.security.Principal;
import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.Optional;

/**
 * Simple LDAP authenticator with a Builder.
 *
 * Usage:
 *
 *   LDAPAuthenticator authenticator = new LDAPAuthenticator.Builder()
 *           .host("ldap.example.com")
 *           .port(389)
 *           .useSsl(false)
 *           .baseDn("dc=example,dc=com")
 *           .bindDn("cn=service,dc=example,dc=com")        // optional
 *           .bindPassword("servicePassword")              // optional
 *           // .userSearchFilter("(sAMAccountName={0})")  // optional, default "(uid={0})"
 *           .build();
 *
 *   Principal p = authenticator.authenticate("jbloggs", "userPassword");
 *   if (p != null) {
 *       System.out.println("Authenticated as: " + p.getName());
 *   } else {
 *       System.out.println("Invalid credentials");
 *   }
 */
public class LDAPAuthenticator implements TNFSAuthenticator {

    private final String host;
    private final int port;
    private final boolean useSsl;
    private final boolean ignoreSslErrors;
    private final String baseDn;
    private final Optional<String> bindDn;        // optional
    private final Optional<char[]> bindPassword;  // optional
    private final String userSearchFilter;

    private LDAPAuthenticator(Builder builder) {
        this.host = builder.host;
        this.useSsl = builder.useSsl;
        this.ignoreSslErrors = builder.ignoreSslErrors;
        this.port = builder.port.orElseGet(() -> useSsl ? 636 : 389);
        this.baseDn = builder.baseDn;
        this.bindDn = builder.bindDn;
        this.bindPassword = builder.bindPassword;
        this.userSearchFilter = builder.userSearchFilter;
    }

    /**
     * Authenticate the given username and password.
     *
     * If a bind account is configured:
     *   - username is treated as a "short" name (e.g. uid, sAMAccountName);
     *   - we first bind as the service account, look up the user's DN, then bind as the user.
     *
     * If no bind account is configured:
     *   - username is expected to be the full DN (e.g. "uid=jbloggs,ou=People,dc=example,dc=com").
     *
     * @param username short username or DN (see above)
     * @param password user's password
     * @return a Principal on success, or empty if credentials are invalid
     * @throws NamingException on LDAP errors (connection, search issues, etc.)
     */
    @Override
    public Optional<Principal> authenticate(TNFSFileAccess fs, Optional<String> username, Optional<char[]> password) {
        if (username.isEmpty() || password.isEmpty()) {
        	return Optional.empty();
        }

        try {
	        String userDn;
	
	        if (bindDn.isPresent()) {
	            // 1. Bind as service account and search for user's DN by short username
	            DirContext serviceCtx = null;
	            try {
	                serviceCtx = createContext(bindDn.get(), bindPassword.get());
	                userDn = findUserDn(serviceCtx, username.get());
	                if (userDn == null) {
	                    // User not found
	                    return null;
	                }
	            } finally {
	                closeQuietly(serviceCtx);
	            }
	        } else {
	            // No bind account: treat username as full DN
	            userDn = username.get();
	        }
	
	        // 2. Try to bind as the user with the provided password
	        DirContext userCtx = null;
	        try {
	            userCtx = createContext(userDn, password.get());
	            // If we get here, authentication succeeded
	            final String principalName = userDn;
	            return Optional.of(new Principal() {
	                @Override
	                public String getName() {
	                    return principalName;
	                }
	
	                @Override
	                public String toString() {
	                    return getName();
	                }
	            });
	        } catch (AuthenticationException e) {
	            // Invalid credentials
	            return Optional.empty();
	        } finally {
	            closeQuietly(userCtx);
	        }
        }
        catch(NamingException ne) {
        	throw new IllegalStateException("Failed to authenticate.", ne);
        }
    }

    /**
     * Creates a DirContext bound as the given user DN.
     */
    private DirContext createContext(String userDn, char[] password) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        String protocol = useSsl ? "ldaps" : "ldap";
        String providerUrl = protocol + "://" + host + ":" + port;

        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, providerUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, userDn);
        env.put(Context.SECURITY_CREDENTIALS, new String(password));
        
        if(ignoreSslErrors) {
            env.put("java.naming.ldap.factory.socket", DummySSLSocketFactory.class.getName());
        }

        // Optional but recommended timeouts (milliseconds)
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "10000");

        // For ldaps you may need proper truststore configuration at JVM level
        // (javax.net.ssl.trustStore, etc.) – not handled here.

        return new InitialDirContext(env);
    }

    /**
     * Searches for the full DN of the given username using the configured baseDn and userSearchFilter.
     */
    private String findUserDn(DirContext ctx, String shortUsername) throws NamingException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setCountLimit(1); // We only need one match

        String filter = MessageFormat.format(userSearchFilter, escapeSearchFilterValue(shortUsername));

        NamingEnumeration<SearchResult> results =
                ctx.search(baseDn, filter, controls);

        try {
            if (results.hasMore()) {
                SearchResult sr = results.next();
                return sr.getNameInNamespace();
            } else {
                return null;
            }
        } finally {
            if (results != null) {
                results.close();
            }
        }
    }

    /**
     * Escape special characters in LDAP search filter value as per RFC 4515.
     */
    private static String escapeSearchFilterValue(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\':
                    sb.append("\\5c");
                    break;
                case '*':
                    sb.append("\\2a");
                    break;
                case '(':
                    sb.append("\\28");
                    break;
                case ')':
                    sb.append("\\29");
                    break;
                case '\0':
                    sb.append("\\00");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void closeQuietly(DirContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException ignored) {
                // ignore
            }
        }
    }

    /**
     * Builder for LdapAuthenticator.
     */
    public static class Builder {
        private String host;
        private Optional<Integer> port = Optional.empty();
        private boolean useSsl = false;
        private boolean ignoreSslErrors = false;
        private String baseDn;
        private Optional<String> bindDn = Optional.empty();
        private Optional<char[]> bindPassword = Optional.empty();
        private String userSearchFilter = "(uid={0})";

        /**
         * LDAP server hostname or IP.
         * 
         * @param host host
         * @return this for chaining
         */
        public Builder withHost(String host) {
            this.host = host;
            return this;
        }

        /**
         * LDAP server port. Defaults to 389.
         * 
         * @param port port
         * @return this for chaining
         */
        public Builder withPort(int port) {
            this.port = Optional.of(port);
            return this;
        }

        /**
         * Use SSL (ldaps)
         * 
         * @return this for chaining
         */
        public Builder withSsl() {
            return withSsl(true);
        }

        /**
         * Use SSL (ldaps) if true, plain (ldap) if false. Defaults to false.
         * 
         * @param useSsl use SSL
         * @return this for chaining
         */
        public Builder withSsl(boolean useSsl) {
            this.useSsl = useSsl;
            return this;
        }

        /**
         * Ignore SSL errors
         * 
         * @return this for chaining
         */
        public Builder withIgnoreSslErrors() {
            return withIgnoreSslErrors(true);
        }

        /**
         * Ignore SSL errors if true.
         * 
         * @param ignoreSslErrors ignore SSL errors
         * @return this for chaining
         */
        public Builder withIgnoreSslErrors(boolean ignoreSslErrors) {
            this.ignoreSslErrors = ignoreSslErrors;
            return this;
        }

        /**
         * Base DN under which users are searched, e.g. "dc=example,dc=com".
         * 
         * @param baseDn base DN
         * @return this for chaining
         */
        public Builder withBaseDn(String baseDn) {
            this.baseDn = baseDn;
            return this;
        }

        /**
         * Optional bind DN for a service account used to look up users by short username.
         * If not set, authenticate() will treat the username as a full DN.
         * 
         * @param bindDn bind DN
         * @return this for chaining
         */
        public Builder withBindDn(String bindDn) {
            this.bindDn = Optional.of(bindDn);
            return this;
        }

        /**
         * Password for the bind DN (service account).
         * 
         * @param bindPassword bind password
         * @return this for chaining
         */
        public Builder withBindPassword(String bindPassword) {
            return withBindPassword(bindPassword.toCharArray());
        }

        /**
         * Password for the bind DN (service account).
         * 
         * @param bindPassword bind password
         * @return this for chaining
         */
        public Builder withBindPassword(char[] bindPassword) {
            this.bindPassword = Optional.of(bindPassword);
            return this;
        }

        /**
         * Optional search filter for resolving short usernames to a DN.
         * Use {0} as a placeholder for the escaped username.
         *
         * Examples:
         *   "(uid={0})"              – typical for OpenLDAP
         *   "(sAMAccountName={0})"   – typical for Active Directory
         *   
         * @param userSearchFilter user search filter
         * @return this for chaining
         */
        public Builder withUserSearchFilter(String userSearchFilter) {
            this.userSearchFilter = userSearchFilter;
            return this;
        }

        /**
         * Build the authenticator.
         * 
         * @return authenticator
         */
        public LDAPAuthenticator build() {
            if (host == null || host.isEmpty()) {
                throw new IllegalStateException("host must be set");
            }
            if (baseDn == null || baseDn.isEmpty()) {
                throw new IllegalStateException("baseDn must be set");
            }
            if ((bindDn == null) != (bindPassword == null)) {
                throw new IllegalStateException("bindDn and bindPassword must both be set or both be null");
            }
            if (userSearchFilter == null || !userSearchFilter.contains("{0}")) {
                throw new IllegalStateException("userSearchFilter must contain {0} placeholder");
            }
            return new LDAPAuthenticator(this);
        }
    }
}
