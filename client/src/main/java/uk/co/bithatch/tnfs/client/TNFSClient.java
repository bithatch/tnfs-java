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
package uk.co.bithatch.tnfs.client;

import java.io.Closeable;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.BufferUnderflowException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.NotDirectoryException;
import java.nio.file.ReadOnlyFileSystemException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.AbstractBuilder;
import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Debug;
import uk.co.bithatch.tnfs.lib.Interrupt;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.Protocol;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.TNFSException;

public final class TNFSClient implements Closeable {
	
	private final static Logger LOG = LoggerFactory.getLogger(TNFSClient.class);
	
	public static class Builder extends AbstractBuilder<Builder> {
		
		private Optional<Duration> timeout  = Optional.of(Duration.ofSeconds(5));
		
		public Builder withTimeout(Duration timeout) {
			this.timeout = Optional.of(timeout);
			return this;
		}

		public Builder withoutTimeout() {
			this.timeout = Optional.empty();
			return this;
		}
		
		/**
		 * Create the client from this builders configuration.
		 * 
		 * @return client
		 */
		public TNFSClient build() throws IOException {
			return new TNFSClient(port, messageSize, protocol, hostname, timeout);
		}
	}
	
	public record MessageResult<RESULT extends Result>(Message mesage, RESULT result) {}
	
	final AbstractSelectableChannel channel;
	final int messageSize;
	final int payloadSize;
	
	private final InetSocketAddress address;
	private final Protocol protocol;
	private final Optional<Duration> timeout;
	private byte seq = 0;
	private final Map<Class<? extends TNFSClientExtension>, TNFSClientExtension> extensions;
	private Object lock = new Object();

	private TNFSClient(Optional<Integer> port, Optional<Integer> messageSize, Protocol protocol,
			Optional<String> hostname, Optional<Duration> timeout)  throws  IOException {
		
		this.protocol = protocol;
		this.timeout = timeout;
		
		address = new InetSocketAddress(hostname.orElse("localhost"), port.orElse(TNFS.DEFAULT_PORT));
		
		if(protocol == Protocol.UDP) {
			this.messageSize = messageSize.orElse(TNFS.DEFAULT_UDP_MESSAGE_SIZE);
			
			var dchannel =  DatagramChannel.open();
			channel = dchannel;
			
			dchannel.setOption(StandardSocketOptions.SO_SNDBUF, this.messageSize);
			dchannel.setOption(StandardSocketOptions.SO_RCVBUF, this.messageSize);
		}
		else {
			this.messageSize = messageSize.orElse(TNFS.DEFAULT_TCP_MESSAGE_SIZE);
			var tchannel = SocketChannel.open(address);
			tchannel.setOption(StandardSocketOptions.SO_SNDBUF, this.messageSize);
			tchannel.setOption(StandardSocketOptions.SO_RCVBUF, this.messageSize);
			channel = tchannel;
		}
		
		payloadSize = this.messageSize - Message.HEADER_SIZE;
		
		extensions = ServiceLoader.load(TNFSClientExtension.class).stream().map(p -> p.get()).peek(ext -> {
			ext.init(this);
		}).collect(Collectors.toMap(TNFSClientExtension::getClass, Function.identity()));
		
	}
	
	@SuppressWarnings("unchecked")
	public <EXT extends TNFSClientExtension> EXT extension(Class<EXT> extension) {
		var ext = extensions.get(extension);
		if(ext == null)
			throw new IllegalArgumentException("No such extension.");
		return (EXT)ext;
	}

	private int nextSeq() {
		try {
			return Byte.toUnsignedInt(seq);
		}
		finally {
			seq++;
			if(seq > 250)
				seq = 0;
		}
	}

	public <RESULT extends Result> RESULT sendMessage(Command<?, RESULT> op, Message pkt) throws IOException {
		return send(op, pkt).result;
	}

	public <RESULT extends Result> RESULT sendMessage(Command<?, RESULT> op, Message pkt, String path) throws IOException {
		return send(op, pkt, path).result;
	}

	public <RESULT extends Result> MessageResult<RESULT> send(Command<?, RESULT> op, Message pkt) throws IOException {
		return send(op, pkt, Optional.empty());
	}

	public <RESULT extends Result> MessageResult<RESULT> send(Command<?, RESULT> op, Message pkt, String path) throws IOException {
		return send(op, pkt, Optional.of(path));
	}
	
	public <RESULT extends Result> MessageResult<RESULT> send(Command<?, RESULT> op, Message pkt, Optional<String> path) throws IOException {
//		System.out.println("MG: " + op.name());
		synchronized(lock) {
			pkt = pkt.withSeq(nextSeq());
			var reply = write(pkt);
			RESULT res = reply.resultPayload();
			if(res.result().isOk()) {
				return new MessageResult<>(reply, res);
			}
			else if(res.result() == ResultCode.EOF) {
				throw new EOFException();
			}
			else if(res.result() == ResultCode.IO) {
				throw new IOException(path.map( s-> "I/O Error on " + s).orElse("I/O Error."));
			}
			else if(res.result() == ResultCode.NOENT) {
				throw new FileNotFoundException(path.orElse("Path Unknown"));
			}
			else if(res.result() == ResultCode.EXIST) {
				throw new FileAlreadyExistsException(path.orElse("Path Unknown"));
			}
			else if(res.result() == ResultCode.INVAL) {
				throw new IllegalArgumentException("Invalid argument");
			}
			else if(res.result() == ResultCode.NOBUFS) {
				throw new BufferUnderflowException();
			}
			else if(res.result() == ResultCode.LOOP) {
				throw new FileSystemLoopException(path.orElse("Path Unknown"));
			}
			else if(res.result() == ResultCode.ACCESS) {
				throw new AccessDeniedException(path.orElse("Path Unknown"));
			}
			else if(res.result() == ResultCode.NOMEM) {
				throw new OutOfMemoryError("Server reported out of memory.");
			}
			else if(res.result() == ResultCode.ROFS) {
				throw new ReadOnlyFileSystemException();
			}
			else if(res.result() == ResultCode.NOTDIR) {
				throw new NotDirectoryException(path.orElse("Path Unknown"));
			}
			else if(res.result() == ResultCode.NOSYS) {
				throw new UnsupportedOperationException("The server does not implement this function.");
			}
			else
				throw new TNFSException(res.result(), String.format("Unexpected result code 0x%04x (%d) [%s].", res.result().value(), res.result().value(), res.result().name()));
		}
	}

	Message write(Message pkt) throws IOException, EOFException {
		var buf = Message.allocateLE(messageSize);
		pkt.encode(buf);
		buf.flip();
		
		if(LOG.isDebugEnabled()) {
			LOG.debug(">: [{}] {}", buf.remaining(), Debug.dump(buf));
		}
		
		if(channel instanceof DatagramChannel dchannel) {
			var wrtn = dchannel.send(buf, address);
			
			if(LOG.isDebugEnabled()) {
				LOG.debug("Written {}", wrtn);
			}
			
			buf.clear();
			
			if(timeout.isPresent())
				Interrupt.ioInterrupt(() -> dchannel.receive(buf), timeout.get());
			else
				dchannel.receive(buf);
			
			buf.flip();
			var msg = Message.decode(buf);
			if(msg.seq() == pkt.seq()) {
				return msg;
			}
			else {
				throw new IllegalStateException(String.format("Out of sequence (newer) response, lost a message. Expected %d, got %d", pkt.seq(), msg.seq()));
			}
		}
		else if(channel instanceof SocketChannel tchannel) {
			tchannel.write(buf);
			buf.clear();
			
			while(true) {
				
				var rd = timeout.isPresent() 
						? Interrupt.ioCall(() -> tchannel.read(buf), timeout.get())
						: tchannel.read(buf);
				if(rd == -1)
					throw new EOFException();
				
				if(rd == 0)
					Thread.yield();
				else {
				
					buf.flip();

					if(LOG.isDebugEnabled()) {
						LOG.debug("<: [{}] {}", buf.remaining(), Debug.dump(buf));
					}
					
					var msg = Message.decode(buf);
					if(msg.seq() == pkt.seq()) {
						return msg;
					}
					else {
						throw new IllegalStateException(String.format("Out of sequence (newer) response, lost a message. Expected %d, got %d", pkt.seq(), msg.seq()));
					}
				}
			}
		}
		else {
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Create a new mount builder for the default mount.
	 * 
	 * @return mount builder
	 */
	public DefaultTNFSMount.Builder mount() {
		return mount("");
	}
	
	/**
	 * Create a new mount builder that may be used to mount a path.
	 * 
	 * @param path path of mount
	 * @return mount builder
	 */
	public DefaultTNFSMount.Builder mount(String path) {
		return new DefaultTNFSMount.Builder(path, this);
	}

	@Override
	public void close() throws IOException {
		try {
			for(var x : extensions.values()) {
				x.close();
			}
		}
		finally {
			channel.close();
		}
	}

	public int messageSize() {
		return messageSize;
	}
	
	public Protocol protocol() {
		return protocol;
	}

	public InetSocketAddress address() {
		return address;
	}	
}
