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
import java.util.Optional;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.HeaderOnlyResult;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Encodeable;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.Version;

public class Extensions {
	
	public enum Checksum {
		CRC32, MD2, MD5, SHA, SHA256, SHA384, SHA512
	}

	public final static Command<Sum,SumResult> SUM = new Command<>(0x90, "SUM", Sum::decode, Sum::encode, SumResult::decode);
	public final static Command<Copy,HeaderOnlyResult> COPY = new Command<>(0x91, "COPY", Copy::decode, Copy::encode, HeaderOnlyResult::decode);
	public final static Command<Mounts,MountsResult> MOUNTS = new Command<>(0x92, "MOUNTS", Mounts::decode, Mounts::encode, MountsResult::decode);
	public final static Command<PktSize,PktSizeResult> PKTSZ = new Command<>(0x93, "PKTSZ", PktSize::decode, PktSize::encode, PktSizeResult::decode);
	public final static Command<SecureMount,SecureMountResult> SECMNT= new Command<>(0x94, "STRTENCR", SecureMount::decode, SecureMount::encode, SecureMountResult::decode);
	public final static Command<AuthenticateMount,HeaderOnlyResult> AUTHMNT = new Command<>(0x95, "AUTHMNT", AuthenticateMount::decode, AuthenticateMount::encode, HeaderOnlyResult::decode);

	public record ServerCaps() implements Encodeable {
		
		public static ServerCaps decode(ByteBuffer buf) {
			return new ServerCaps();
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
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
	
	public record SecureMount(Version version, int derivedKeyBits, int blockSize, byte[] key) implements Encodeable {
		public static SecureMount decode(ByteBuffer buf) {
			return new SecureMount(
				Version.decode(buf),
				Short.toUnsignedInt(buf.getShort()),
				Short.toUnsignedInt(buf.getShort()),
				Encodeable.byteArray(buf)
			);
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			version.encode(buf);
			buf.putShort((short)derivedKeyBits);
			buf.putShort((short)blockSize);
			Encodeable.byteArray(buf, key);
			return buf;
		}
	}

	public record SecureMountResult(ResultCode result, Version version, Duration retryTime, byte[] key) implements Result  {

		public SecureMountResult(ResultCode result, Version version) {
			this(result, version, Duration.ofMillis(0), new byte[0]);
		}
		
		public static SecureMountResult decode(ByteBuffer buf) {

			var res = Result.decodeResult(buf);
			var ver = Version.decode(buf);
			if(res.isOk()) {
				return new SecureMountResult(
					res,
					ver,
					Duration.ofMillis(Short.toUnsignedInt(buf.getShort())),
					Encodeable.byteArray(buf)
				);
			}
			else {
				return new SecureMountResult(
					res,
					ver);
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			version.encode(buf);
			buf.putShort((short)retryTime.toMillis());
			Encodeable.byteArray(buf, key);
			return buf;
		}
	}

	public record AuthenticateMount(Optional<String> userId, Optional<char[]> password) implements Encodeable {
		
		public static AuthenticateMount decode(ByteBuffer buf) {
			return new AuthenticateMount(
					Encodeable.emptyIfBlank(Encodeable.cString(buf)),
					Encodeable.emptyIfBlank(Encodeable.cString(buf)).map(String::toCharArray));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			Encodeable.cString(userId.orElse(""), buf);
			Encodeable.cString(password.map(String::new).orElse(""), buf);
			return buf;
		}
		
	}
}
