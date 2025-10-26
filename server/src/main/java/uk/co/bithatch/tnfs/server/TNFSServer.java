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
package uk.co.bithatch.tnfs.server;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.AbstractBuilder;
import uk.co.bithatch.tnfs.lib.Command.HeaderOnlyResult;
import uk.co.bithatch.tnfs.lib.Debug;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.Protocol;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.TNFSException;
import uk.co.bithatch.tnfs.lib.Version;
import uk.co.bithatch.tnfs.server.TNFSMounts.TNFSMountRef;


public abstract class TNFSServer<CHAN extends Channel> implements Runnable, Closeable {
	
	public final static class Builder extends AbstractBuilder<Builder> {
		private Optional<Integer> backlog = Optional.empty();
		private Optional<TNFSFileSystemService> fileSystemFactory = Optional.empty();
		private Optional<Integer> maxSessions = Optional.empty();
		private List<TNFSMessageProcessor> postProcessors = new ArrayList<>();
		private List<TNFSMessageProcessor> preProcessors = new ArrayList<>();
		private Duration retryTime = Duration.ofSeconds(5);
		private Optional<Supplier<byte[]>> serverKey = Optional.empty();
		
		public TNFSServer<?> build() throws IOException {
			if(protocol == Protocol.TCP) {
				return new TCPTNFSServer(
					backlog, 
					port, 
					messageSize, 
					hostname, 
					fileSystemFactory,
					retryTime,
					maxSessions, preProcessors, postProcessors,
					serverKey
				);
			}
			else {
				return new UDPTNFSServer(
					port, 
					messageSize, 
					hostname, 
					fileSystemFactory,
					retryTime,
					maxSessions, preProcessors, postProcessors,
					serverKey
				);
			}
		}
		
		public Builder withBacklog(int backlog) {
			this.backlog = Optional.of(backlog);
			return this;
		}

		public Builder withFileSystemFactory(TNFSFileSystemService fileSystemFactory) {
			this.fileSystemFactory = Optional.of(fileSystemFactory);
			return this;
		}
		
		public Builder withMaxSessions(int maxSessions) {
			this.maxSessions = Optional.of(maxSessions);
			return this;
		}

		public Builder withPostProcessors(Collection<TNFSMessageProcessor> postProcessors) {
			this.postProcessors.addAll(postProcessors);
			return this;
		}
		
		public Builder withPostProcessors(TNFSMessageProcessor... postProcessors) {
			return withPreProcessors(Arrays.asList(postProcessors));
		}
		
		public Builder withPreProcessors(Collection<TNFSMessageProcessor> preProcessors) {
			this.preProcessors.addAll(preProcessors);
			return this;
		}
		
		public Builder withPreProcessors(TNFSMessageProcessor... preProcessors) {
			return withPreProcessors(Arrays.asList(preProcessors));
		}
		
		public Builder withRetryTime(Duration retryTime) {
			this.retryTime = retryTime;
			return this;
		}
		
		public Builder withRetryTime(long ms) {
			return withRetryTime(Duration.ofMillis(ms));
		}

		public Builder withServerKey(byte[] serverKey) {
			return withServerKey(() -> serverKey);
		}

		public Builder withServerKey(String base64ServerKey) {
			return withServerKey(Base64.getDecoder().decode(base64ServerKey));
		}
		
		public Builder withServerKey(Supplier<byte[]> serverKey) {
			this.serverKey = Optional.of(serverKey);
			return this;
		}
	}
	
	private final static class TCPTNFSServer extends TNFSServer<ServerSocketChannel> {
		private final static Logger LOG = LoggerFactory.getLogger(TCPTNFSServer.class);

		private final ByteBuffer buffer;
		
		private TCPTNFSServer(
				Optional<Integer> backlog, 
				Optional<Integer> port, 
				Optional<Integer> messageSize, 
				Optional<String> hostname,
				Optional<TNFSFileSystemService> fileSystemFactory,
				Duration retryTime,
				Optional<Integer> maxSessions,
				List<TNFSMessageProcessor> preProcessors,
				List<TNFSMessageProcessor> postProcessors,
				Optional<Supplier<byte[]>> serverKey)  throws  IOException {
			super(port, messageSize.orElse(TNFS.DEFAULT_TCP_MESSAGE_SIZE), hostname, fileSystemFactory, retryTime, maxSessions, preProcessors, postProcessors, ServerSocketChannel.open(), serverKey);
			channel().bind(address());
			channel().configureBlocking(false);
			buffer = Message.allocateLE(this.messageSize);
		}

		@Override
		public Protocol protocol() {
			return Protocol.TCP;
		}
		
		private void read(SocketChannel channel) throws IOException {
			buffer.clear();
			var rd = channel.read(buffer);
			if(rd == -1) 
				throw new EOFException();
			else if(rd > 0) {
				buffer.flip();
				decodeAndHandle(buffer, channel, channel.getRemoteAddress());
			}
		}
		
		@Override
		protected void doRun() throws Exception {
			
			var serverChannel = channel();
			
			var selector = Selector.open(); // selector is open here
			var ops = serverChannel.validOps();
	
			serverChannel.register(selector, ops, null);
			
			while (serverChannel.isOpen()) {
				if(LOG.isTraceEnabled()) {
					LOG.trace("Waiting for data or connection");
				}
				selector.select();

				var keys = selector.selectedKeys();
				var keyIt = keys.iterator();
				while (keyIt.hasNext()) {
					var key = keyIt.next();

					if (key.isAcceptable()) {
						
						var clnt = serverChannel.accept();
						clnt.configureBlocking(false);
						clnt.register(selector, SelectionKey.OP_READ);
						
						LOG.info("Connection Accepted: {} from {}", clnt.getLocalAddress(), clnt.getRemoteAddress());
						
					} else if (key.isReadable()) {
						var sckt = (SocketChannel) key.channel();
						try {
							/* TODO put responses on a queue (each thread with own buffer) */
							read(sckt);
			                key.interestOps(SelectionKey.OP_WRITE);
						}
						catch (/* EOF */Exception eof) {
							if(eof instanceof EOFException) {
								LOG.info("Session {} closed cleanly.", sckt.getRemoteAddress());
							}
							else {
								if(LOG.isDebugEnabled() || eof.getMessage() == null)
									LOG.warn("Session {} closed abrubptly.", sckt.getRemoteAddress(), eof);
								else
									LOG.warn("Session {} closed abrubptly. {}", sckt.getRemoteAddress(), eof.getMessage());
							}
							try {
								onClose(sckt);
							}
							catch(Exception e) {
								LOG.error("Failed to close socket.", e);
							}
						}
					} else if (key.isWritable()) {
		                key.interestOps(SelectionKey.OP_READ);
					}
					keyIt.remove();
				}
			}
		}

		@Override
		protected void onClose(SocketChannel tnfsPeer) throws IOException {
			tnfsPeer.close();
		}

		@Override
		protected void write(ByteBuffer outBuffer, SocketChannel tnfsPeer, SocketAddress addr) throws IOException {
			var written = tnfsPeer.write(outBuffer);

			if(LOG.isTraceEnabled()) {
				LOG.trace("Written {} bytes to {}", written, addr);
			}
		}
	}

	private final static class UDPTNFSServer extends TNFSServer<DatagramChannel> {
		private final static Logger LOG = LoggerFactory.getLogger(UDPTNFSServer.class);

		private UDPTNFSServer(
				Optional<Integer> port, 
				Optional<Integer> messageSize, 
				Optional<String> hostname,
				Optional<TNFSFileSystemService> fileSystemFactory,
				Duration retryTime,
				Optional<Integer> maxSessions,
				List<TNFSMessageProcessor> preProcessors,
				List<TNFSMessageProcessor> postProcessors,
				Optional<Supplier<byte[]>> serverKey)  throws  IOException {
			super(port, messageSize.orElse(TNFS.DEFAULT_UDP_MESSAGE_SIZE), hostname, fileSystemFactory, retryTime, maxSessions, preProcessors, postProcessors, DatagramChannel.open(), serverKey);
			channel().socket().bind(address());
		}

		@Override
		public Protocol protocol() {
			return Protocol.UDP;
		}

		@Override
		protected void doRun() throws Exception {
			/* TODO put responses on a queue (each thread with own buffer) */
			var buf = Message.allocateLE(messageSize);
			
			while(channel().isOpen()) {
				buf.clear();
				
				var addr = channel().receive(buf);
				if(addr == null)
					throw new EOFException();
				buf.flip();
				
				try {
					decodeAndHandle(buf, null, addr);
				}
				catch(Exception e) {
					LOG.error("UDP message failed.", e);
				}
			}
			
		}

		@Override
		protected void write(ByteBuffer outBuffer,  SocketChannel channel, SocketAddress addr) throws IOException {
			var written = channel().send(outBuffer, addr);
			
			if(LOG.isTraceEnabled()) {
				LOG.trace("Written {} bytes to {}", written, addr);
			}
		}
	}
	private final static Logger LOG = LoggerFactory.getLogger(TNFSServer.class);
	private final InetSocketAddress address;

	private final TNFSFileSystemService fileSystemService;
	
	private final int maxSessions;

	private final List<TNFSMessageProcessor> postProcessors;
	private final List<TNFSMessageProcessor> preProcessors;
	private final Duration retryTime;
	private final CHAN socketChannel;
	private final Optional<Supplier<byte[]>> serverKey;
	private final Map<Integer, TNFSSession> sessions = Collections.synchronizedMap(new HashMap<>());
	private final Map<Integer, TNFSMessageHandler> handlers = Collections.synchronizedMap(new HashMap<>());
	private short sessionId;
	
//	protected final Map<SocketAddress, TNFSPeer> peers = new HashMap<>();
	
	final int messageSize;
	
	private TNFSServer(
			Optional<Integer> port, 
			int messageSize, 
			Optional<String> hostname,
			Optional<TNFSFileSystemService> fileSystemService,
			Duration retryTime,
			Optional<Integer> maxSessions,
			List<TNFSMessageProcessor> preProcessors,
			List<TNFSMessageProcessor> postProcessors,
			CHAN channel,
			Optional<Supplier<byte[]>> serverKey)  throws  IOException {

		this.serverKey = serverKey;
		this.preProcessors = Collections.unmodifiableList(new ArrayList<>(preProcessors));
		this.postProcessors = Collections.unmodifiableList(new ArrayList<>(postProcessors));
		this.messageSize = messageSize;
		this.maxSessions = maxSessions.orElse(TNFS.DEFAULT_MAX_SESSIONS);
		this.retryTime = retryTime;
		this.fileSystemService = fileSystemService.orElseGet(new Supplier<TNFSFileSystemService>() {
			
			TNFSInMemoryFileSystem defaultMount = new TNFSInMemoryFileSystem("/");
			TNFSMountRef ref = new TNFSMountRef(defaultMount, Optional.empty());
			
			@Override
			public TNFSFileSystemService get() {
				return new TNFSFileSystemService() {
					
					@Override
					public TNFSUserMount createMount(String path, Optional<? extends Principal> user) {
						if(path.equals("/")) {
							return new TNFSUserMount(defaultMount, TNFSMounts.GUEST);
						}
						else
							throw new IllegalArgumentException("No such mount.");
					}
					
					@Override
					public TNFSMountRef mountDetails(String path) {
						if(path.equals("/")) {
							return ref;
						}
						else
							throw new IllegalArgumentException("No such mount.");
					}
					
					@Override
					public Collection<TNFSMountRef> mounts() {
						return Arrays.asList(ref);
					}
				};
			}
		});
		
		address = new InetSocketAddress(hostname.orElse("localhost"), port.orElse(TNFS.DEFAULT_PORT));
		
		this.socketChannel = channel;
		ServiceLoader.load(TNFSMessageHandler.class).forEach(hndlr -> {
			handlers.put(Byte.toUnsignedInt(hndlr.command().code()), hndlr);
		});
	}
	
	public final InetSocketAddress address() {
		return address;
	}
	
	public final CHAN channel() {
		return socketChannel;
	}

	@Override
	public void close() throws IOException {
		socketChannel.close();
	}

	public TNFSFileSystemService fileSystemService() {
		return fileSystemService;
	}
	
	public final int maxSessions() {
		return maxSessions;
	}

	public final int messageSize() {
		return messageSize;
	}

	public abstract Protocol protocol();


	public Duration retryTime() {
		return retryTime;
	}

	@Override
	public void run() {
		LOG.info("Listening on {} {}:{}", address().getHostName(), address().getPort(), protocol());
		try {
			doRun();
		} catch(RuntimeException re) {
			throw re;
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		} catch(Exception e) {
			throw new RuntimeException(e);
		} finally {
			LOG.info("{} server exited.", protocol());
		}
	}
	
	public byte[] serverKey() {
		return serverKey.map(Supplier::get).orElseThrow(() -> new IllegalStateException("No server key is available."));
	}

	public Map<Integer, TNFSSession> sessions() {
		return Collections.unmodifiableMap(sessions);
	}
	
	private void handle(ByteBuffer outBuffer, Message message, SocketChannel channel, SocketAddress addr) throws IOException {
		var cmd = message.command();
		
		if(LOG.isTraceEnabled()) {
			LOG.trace("Handling {} for {}", cmd.name(), address());
		}
		
		var msgContext = new TNFSMessageProcessor.MessageContext() {
			
			@Override
			public TNFSServer<?> server() {
				return TNFSServer.this;
			}
		};
		
		for(var pp : preProcessors) {
			message = pp.apply(msgContext, message);
		}
		
		var code = Byte.toUnsignedInt(message.command().code());
		var nh = handlers.get(code);
		if(nh == null)
			throw new IllegalArgumentException("No handler for message with code " + message.command().code() + ".");
		
		var session = sessions.get(message.connectionId());
		if(session == null && nh.needsSession()) {
			LOG.error("No session {}", message.connectionId());
			write(outBuffer, Message.of(message.seq(), message.command(), new HeaderOnlyResult(ResultCode.INVALID)), channel, addr);
			return;
		}
		
		if(session != null && nh.needsAuthentication() && !session.authenticated()) {
			LOG.error("Session {} not authenticated for message {}.", message.connectionId(), message.command().name());
			write(outBuffer, Message.of(message.seq(), message.command(), new HeaderOnlyResult(ResultCode.INVALID)), channel, addr);
			return;
		}

		var newSession = new AtomicInteger(session == null ? 0 : session.id());
		
		var result = nh.handle(message, new TNFSMessageHandler.HandlerContext() {
			@Override
			public Map<Integer, AbstractDirHandle<?>> dirHandles() {
				checkSession(session);
				return session.dirHandles;
			}
			
			@Override
			public Map<Integer, FileHandle> fileHandles() {
				checkSession(session);
				return session.fileHandles;
			}

			@Override
			public TNFSSession newSession(TNFSUserMount userMount, Version version) {
				if(sessions().size() == server().maxSessions()) {
					LOG.error("Too many mounts for connection {}.", address());
					throw new TNFSException(ResultCode.USERS);
				}
				var mountSessionId = nextSessionId();
				var session = new TNFSSession(userMount, mountSessionId, TNFSServer.this, version);
				sessions.put(mountSessionId, session);
				newSession.set(mountSessionId);
				LOG.info("New mount with id of {} [{}] to {} by {} in connection {}", mountSessionId, String.format("%04x", mountSessionId), userMount.fileSystem().mountPath(), userMount.user().getName(), address());
				return session;
			}
			
			@Override
			public int nextDirHandle() {
				checkSession(session);
				return session.nextDirHandle();
			}

			@Override
			public int nextFileHandle() {
				checkSession(session);
				return session.nextFileHandle();
			}

			@Override
			public TNFSServer<?> server() {
				return TNFSServer.this;
			}

			@Override
			public TNFSSession session() {
				checkSession(session);
				return session;
			}

			private void checkSession(TNFSSession session) {
				if(session == null)
					throw new IllegalStateException("No session.");
			}
		});
		
		var reply = Message.of(message.seq(), newSession.get(), message.command(), result);
		for(var pp : postProcessors) {
			reply = pp.apply(msgContext, reply);
		}
		write(outBuffer, reply, channel, addr);
	}
	
	private int nextSessionId() {
		if(sessions.size() >= maxSessions()) {
			throw new IllegalStateException("Exhausted sessions.");
		}
		var id = 0;
		do {
			id = Short.toUnsignedInt(++sessionId);
		} while(id == 0 || sessions.containsKey(id));
		return id;
	}

	private void write(ByteBuffer outBuffer, Message packet, SocketChannel channel, SocketAddress addr) throws IOException {
		outBuffer.clear();
		packet.encodeResult(outBuffer);
		outBuffer.flip();
		
		if(LOG.isTraceEnabled()) {
			LOG.trace("Writing {} bytes to {}", outBuffer.remaining(), address());
			LOG.trace("  " + Debug.dump(outBuffer));
		}
		write(outBuffer, channel, addr);
	}
	
	protected abstract void doRun() throws Exception;
	
	protected void onClose(SocketChannel tnfsPeer) throws IOException {}
	
	protected void decodeAndHandle(ByteBuffer inBuffer, SocketChannel channel, SocketAddress addr) throws IOException {
		
		if(LOG.isTraceEnabled()) {
			LOG.trace("Read {} bytes from {}", inBuffer.remaining(), address());
			LOG.trace("  " + Debug.dump(inBuffer));
		}
		
		handle(inBuffer, Message.decode(inBuffer), channel, addr);
	}
	
	protected abstract void write(ByteBuffer outBuffer, SocketChannel tnfsPeer, SocketAddress addr) throws IOException;
	
	void close(TNFSSession tnfsSession) {
		sessions.remove(tnfsSession.id());
	}

	List<TNFSMessageProcessor> postProcessors() {
		return postProcessors;
	}

	List<TNFSMessageProcessor> preProcessors() {
		return preProcessors;
	}

}
