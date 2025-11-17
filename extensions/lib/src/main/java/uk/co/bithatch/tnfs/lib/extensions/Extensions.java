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
package uk.co.bithatch.tnfs.lib.extensions;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.HeaderOnlyResult;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Encodeable;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.Version;

public class Extensions {
	
	public enum Checksum {
		CRC32, MD2, MD5, SHA, SHA256, SHA384, SHA512
	}

	public final static Command<ServerCaps,ServerCapsResult> SRVRCAPS = new Command<>(0x80, "SRVRCAPS", ServerCaps::decode, ServerCaps::encode, ServerCapsResult::decode);
	public final static Command<ClientFirst,ServerFirst> CLNTFRST = new Command<>(0x81, "CLNTFRST", ClientFirst::decode, ClientFirst::encode, ServerFirst::decode);
	public final static Command<ClientFinal,ServerFinal> CLNTFINL = new Command<>(0x82, "CLNTFINL", ClientFinal::decode, ClientFinal::encode, ServerFinal::decode);
	public final static Command<Sum,SumResult> SUM = new Command<>(0x90, "SUM", Sum::decode, Sum::encode, SumResult::decode);
	public final static Command<Copy,HeaderOnlyResult> COPY = new Command<>(0x91, "COPY", Copy::decode, Copy::encode, HeaderOnlyResult::decode);
	public final static Command<Mounts,MountsResult> MOUNTS = new Command<>(0x92, "MOUNTS", Mounts::decode, Mounts::encode, MountsResult::decode);
	public final static Command<PktSize,PktSizeResult> PKTSZ = new Command<>(0x93, "PKTSZ", PktSize::decode, PktSize::encode, PktSizeResult::decode);

	public record ServerCaps() implements Encodeable {
		
		public static ServerCaps decode(ByteBuffer buf) {
			return new ServerCaps();
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			return buf;
		}
	}

	public record ServerCapsResult(ResultCode result, byte[] srvKey, String... hashAlgos) implements Result  {
		
		public static ServerCapsResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			var srvKey = new byte[Byte.toUnsignedInt(buf.get())];
			buf.get(srvKey);
			var algos = new String[Byte.toUnsignedInt(buf.get())];
			for(int i = 0 ; i < algos.length; i++) {
				algos[i] = Encodeable.cString(buf);
			}
			return new ServerCapsResult(
				res, 
				srvKey,
				algos
			);
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.put((byte)srvKey.length);
			buf.put(srvKey);
			buf.put((byte)hashAlgos.length);
			for(int i = 0 ; i < hashAlgos.length; i++) {
				Encodeable.cString(hashAlgos[i], buf);
			}
			return buf;
		}
	}
	public record ClientFirst(Version version, String path, String clientFirstMessage) implements Encodeable {
		
		public ClientFirst(String path, String clientFirstMessage) {
			this(TNFS.PROTOCOL_VERSION, path, clientFirstMessage);
		}
		
		public static ClientFirst decode(ByteBuffer buf) {
			return new ClientFirst(
					Version.decode(buf),
					Encodeable.cString(buf),
					Encodeable.cString(buf));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			version.encode(buf);
			Encodeable.cString(path, buf);
			Encodeable.cString(clientFirstMessage, buf);
			return buf;
		}
		
		public String normalizedPath() {
			return path.equals("") ? "/" : path;
		}
	}
	
	public record ClientFinal(String clientFinalMessage) implements Encodeable {
		
		public static ClientFinal decode(ByteBuffer buf) {
			return new ClientFinal(Encodeable.cString(buf));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			Encodeable.cString(clientFinalMessage, buf);
			return buf;
		}
	}
	
	public record ServerFinal(ResultCode result, String serverFinalMessage) implements Result  {
		
		public ServerFinal(ResultCode result) {
			this(result, "");
		}

		public static ServerFinal decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			if(res.isOk()) {
				return new ServerFinal(
					res, Encodeable.cString(buf));
			}
			else {
				return new ServerFinal(res, "");
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			Encodeable.cString(serverFinalMessage, buf);
			return buf;
		}
	}
	
	
	public record ServerFirst(ResultCode result, Version version, Duration retryTime, String serverFirstMessage) implements Result  {
		
		public ServerFirst(ResultCode result, Duration retryTime, String clientFirstMessage) {
			this(result, TNFS.PROTOCOL_VERSION, retryTime, clientFirstMessage);
		}

		public ServerFirst(ResultCode result, String serverFirstMessage) {
			this(result, TNFS.PROTOCOL_VERSION, serverFirstMessage);
		}

		public ServerFirst(ResultCode result, Version version, String serverFirstMessage) {
			this(result, version, Duration.ofMillis(0), serverFirstMessage);
		}

		public ServerFirst(ResultCode result, Version version) {
			this(result, version, Duration.ofMillis(0), "");
		}

		public ServerFirst(ResultCode result) {
			this(result, TNFS.PROTOCOL_VERSION);
		}
		
		public static ServerFirst decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			var ver = Version.decode(buf);
			if(res.isOk()) {
				return new ServerFirst(
					res,
					ver,
					Duration.ofMillis(Short.toUnsignedInt(buf.getShort())),
					Encodeable.cString(buf)
				);
			}
			else {
				return new ServerFirst(res, ver, "");
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			version.encode(buf);
			if(result.isOk()) {
				buf.putShort((short)retryTime.toMillis());			
				Encodeable.cString(serverFirstMessage, buf);
			}
			return buf;
		}
	}
	
	public record SumResult(ResultCode result, String sum) implements Result  {
		public static SumResult decode(ByteBuffer buf) {
			return new SumResult(
				Result.decodeResult(buf), 
				Encodeable.cString(buf)
			);
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			Encodeable.cString(sum, buf);
			return buf;
		}
	}
	
	public record Mounts() implements Encodeable {
		
		public static Mounts decode(ByteBuffer buf) {
			return new Mounts();
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			return buf;
		}
	}

	public record MountsResult(ResultCode result, String... mounts) implements Result  {
		
		public static MountsResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			var mounts = new ArrayList<String>();
			var sz = Byte.toUnsignedInt(buf.get());
			for(var i = 0 ; i < sz; i++) {
				mounts.add(Encodeable.cString(buf));
			}
			return new MountsResult(
				res,
				mounts.toArray(new String[0])
			);
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.put((byte)mounts.length);
			for(var m : mounts) {
				Encodeable.cString(m, buf);
			}
			return buf;
		}
	}
	
	public record Sum(Checksum type, String path) implements Encodeable {
		
		public static Sum decode(ByteBuffer buf) {
			return new Sum(
				 Checksum.values()[Byte.toUnsignedInt(buf.get())],
				 Encodeable.cString(buf)
			);
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)type.ordinal());
			Encodeable.cString(path, buf);
			return buf;
		}
	}
	
	public record Copy(String path, String targetPath, CopyFlag... flags) implements Encodeable {
		public static Copy decode(ByteBuffer buf) {
			return new Copy(
				Encodeable.cString(buf),
				Encodeable.cString(buf),
				CopyFlag.decode(Byte.toUnsignedInt(buf.get()))
			);
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			Encodeable.cString(path, buf);
			Encodeable.cString(targetPath, buf);
			buf.put((byte)CopyFlag.encode(flags));
			return buf;
		}
	}
	
	public record PktSize(int size) implements Encodeable {
		public static PktSize decode(ByteBuffer buf) {
			return new PktSize(
				Short.toUnsignedInt(buf.getShort())
			);
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.putShort((short)size);
			return buf;
		}
	}

	public record PktSizeResult(ResultCode result, int size) implements Result  {
		
		public static PktSizeResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			return new PktSizeResult(
				res, 
				Short.toUnsignedInt(buf.getShort())
			);
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.putShort((short)size);
			return buf;
		}
	}
}
