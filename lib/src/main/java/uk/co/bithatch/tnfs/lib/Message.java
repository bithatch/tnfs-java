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
package uk.co.bithatch.tnfs.lib;

import java.nio.ByteBuffer;
import java.util.Optional;

public final class Message {

	public static final int HEADER_SIZE = 4;

	public final static class Builder {
		private int connectionId;
		private int seq;
		private Command<? extends Encodeable, ?> command;
		private Optional<ByteBuffer> payload = Optional.empty();
		private Optional<Encodeable> encodeable = Optional.empty();

		public Builder() {
			seq = -1;
		}
		
		private Builder(int seq) {
			this.seq = seq;
		}
		
		public Builder withSessionId(int connectionId) {
			this.connectionId = connectionId;
			return this;
		}
		
		public Builder withCommand(Command<?, ?> op) {
			assert op != null;
			this.command = op;
			return this;
		}
		
		public Builder withPayload(byte[] payload) {
			return withPayload(ByteBuffer.wrap(payload));
		}
		
		public Builder withPayload(ByteBuffer payload) {
			this.payload = Optional.of(payload);
			return this;
		}

		public Builder withPayload(Encodeable encodeable) {
			this.encodeable = Optional.of(encodeable);
			return this;
		}

		public Message build() {
			return new Message(this);
		}

		private Builder remainingAsPayload(ByteBuffer buffer) {
			var buf = buffer.isDirect() ? ByteBuffer.allocateDirect(buffer.remaining()) : ByteBuffer.allocate(buffer.remaining());
			buf.order(buffer.order());
			buf.put(buffer);
			buf.flip();
			return withPayload(buf);
		}

	}

	private final Command<? extends Encodeable, ?> command;
	private final Optional<ByteBuffer> payload;
	private final Optional<Encodeable> encodeable;
	private final int connectionId;
	private final int seq;

	private Message(Builder bldr) {
		this.command = bldr.command;
		this.seq = bldr.seq;
		this.payload = bldr.payload;
		this.encodeable = bldr.encodeable;
		this.connectionId = bldr.connectionId;
	}
	
	Message(Message message, int seq) {
		this.command = message.command;
		this.payload = message.payload;
		this.encodeable = message.encodeable;
		this.connectionId = message.connectionId;
		this.seq = seq;
	}

	public int connectionId() {
		return connectionId;
	}
	
	public int seq() {
		if(seq == -1)
			throw new IllegalStateException("No sequece number yet.");
		return seq;
	}

	public int size() {
		return HEADER_SIZE + payload.map(ByteBuffer::remaining).orElse(0);
	}
	
	public Command<?, ?> command() {
		return command;
	}

	@SuppressWarnings("unchecked")
	public <E extends Encodeable> E payload() {
		return (E)encodeable.orElseGet(() -> command.decode(payload.orElse(null)));
	}

	@SuppressWarnings("unchecked")
	public <R extends Command.Result> R resultPayload() {
		return (R) command.decodeResult(payload.orElseThrow(() -> new IllegalStateException("Result must have payload")));
	}

	public <E extends Encodeable> E payload(Class<E> clz) {
		return payload();
	}

	@Override
	public String toString() {
		return "Message [command=" + command + ", connectionId=" + connectionId + ", seq=" + seq + "]";
	}

	public static Message decode(ByteBuffer buffer) {
		var sessionId = Short.toUnsignedInt(buffer.getShort());
		return new Builder(Byte.toUnsignedInt(buffer.get())).
				withSessionId(sessionId).
				withCommand(Command.get(buffer.get())).
				remainingAsPayload(buffer).
				build();
	}

	@SuppressWarnings("unchecked")
	public ByteBuffer encode(ByteBuffer buffer) {
		buffer.putShort((short)connectionId);
		buffer.put((byte)seq);
		buffer.put(command.code());		
		payload.ifPresentOrElse(pl -> {
			buffer.put(pl); 
		}, () -> {
			encodeable.ifPresent(enc -> {
				((Command<Encodeable, ?>)command).encode(enc, buffer);
				
			});
		}); 
		return buffer;
	}

	public ByteBuffer encodeResult(ByteBuffer buffer) {
		buffer.putShort((short)connectionId);
		buffer.put((byte)seq);
		buffer.put(command.code());		
		payload.ifPresentOrElse(pl -> {
			buffer.put(pl); 
		}, () -> {
			encodeable.ifPresent(enc -> {
				enc.encode(buffer);
			});
		}); 
		return buffer;
	}

	public static <E extends Encodeable> Message of(Command<?, ?> op, E enc) {
		return new Builder().
				withCommand(op).
				withPayload(enc).
				build();
	}
	
	public static <E extends Encodeable> Message of(int sessionId, Command<?, ?> op, E enc) {
		return of(-1, sessionId, op, enc);
	}

	public static <E extends Encodeable> Message of(int seq, int sessionId, Command<?, ?> op, E enc) {
		return new Builder(seq).
				withCommand(op).
				withSessionId(sessionId).
				withPayload(enc).
				build();
	}

	public Message withSeq(int nextSeq) {
		return new Message(this, nextSeq);
	}
	

}
