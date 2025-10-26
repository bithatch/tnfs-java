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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Command<CODEC extends Encodeable, RESULT extends Command.Result> { 

	
	public interface Result extends Encodeable {

		@Override
		default ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)result().value());
			encodeResult(buf);
			return buf;
		}

		static ResultCode decodeResult(ByteBuffer buf) {
			return ResultCode.fromValue(Byte.toUnsignedInt(buf.get()));
		}
		
		ByteBuffer encodeResult(ByteBuffer buf);

		ResultCode result();
	}
	
	public enum SeekType {
		SEEK_SET, SEEK_CUR, SEEK_END
	}
	
	public record Entry(DirEntryFlag[] flags, long size, FileTime mtime, FileTime ctime, String name) implements Encodeable {

		public static Entry forCwd(Path path) {
			return forPath(".", path, DirEntryFlag.SPECIAL, DirEntryFlag.DIR);
		}
		
		public static Entry forParent(Path parent) {
			return forPath("..", parent, DirEntryFlag.SPECIAL, DirEntryFlag.DIR);
		}
		
		public static Entry forPath(Path path) {
			return forPath(path.getFileName().toString(), path, DirEntryFlag.forPath(path));
		}
		
		private static Entry forPath(String name, Path path, DirEntryFlag... flgs) {
			try {
				var exists = Files.exists(path);
				return new Entry(
					flgs,
					exists ? Files.size(path) : 0,
					exists ? Files.getLastModifiedTime(path) : FileTime.fromMillis(0),
					exists ? Files.getLastModifiedTime(path) : FileTime.fromMillis(0),
					name
				);
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}

		public static Entry decode(ByteBuffer buf) {
			return new Entry(
				DirEntryFlag.decode(Byte.toUnsignedInt(buf.get())),
				Integer.toUnsignedLong(buf.getInt()),
				FileTime.fromMillis(buf.getInt() * 1000l),
				FileTime.fromMillis(buf.getInt() * 1000l),
				Encodeable.cString(buf)
			);
		}
		
		public int encodedSize() {
			return 14 + name.length();
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)DirEntryFlag.encode(flags));
			buf.putInt((int)size);
			buf.putInt((int)(mtime.toMillis() / 1000));
			buf.putInt((int)(ctime.toMillis() / 1000));
			Encodeable.cString(name, buf);
			return buf;
		}
	}
	
	public record HeaderOnly() implements Encodeable {

		public static HeaderOnly decode(ByteBuffer buf) {
			return new HeaderOnly();
		}
		
		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			return buf;
		}
	}

	public record HeaderOnlyResult(ResultCode result) implements Result {
		
		public final static HeaderOnlyResult SUCCESS = new HeaderOnlyResult(ResultCode.SUCCESS);
		
		public static HeaderOnlyResult decode(ByteBuffer buf) {
			return new HeaderOnlyResult(Result.decodeResult(buf));
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			return buf;
		}

	}
	
	public record Open(OpenFlag[] flags, ModeFlag[] mode, String path) implements Encodeable {
		public static Open decode(ByteBuffer buf) {
			return new Open(
					OpenFlag.decode(Short.toUnsignedInt(buf.getShort())),
					ModeFlag.decode(Short.toUnsignedInt(buf.getShort())),
					Encodeable.cString(buf));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.putShort((short)OpenFlag.encode(flags.length == 0 ? new OpenFlag[] { OpenFlag.READ } : flags));
			buf.putShort((short)ModeFlag.encode(mode));
			Encodeable.cString(path, buf);
			return buf;
		}
	}
	
	public record StatResult(
			ResultCode result, 
			ModeFlag[] mode, 
			int uid, 
			int gid, 
			long size, 
			FileTime atime, 
			FileTime mtime, 
			FileTime ctime, 
			String uidString, 
			String gidString
		) implements Result  {

		public Entry toEntry(String name) {
			return new Entry(ModeFlag.toDirEntryFlags(name, mode), size, mtime, ctime, name);
		}
		
		public static StatResult decode(ByteBuffer buf) {
			var res =  Result.decodeResult(buf);
			if(res.isError()) {
				return new StatResult(res, null, 0, 0, 0, null, null, null, "", "");
			}
			else {
				return new StatResult(
					res, 
					ModeFlag.decode(Short.toUnsignedInt(buf.getShort())), 
					Short.toUnsignedInt(buf.getShort()), 
					Short.toUnsignedInt(buf.getShort()), 
					Integer.toUnsignedLong(buf.getInt()),
					FileTime.fromMillis(Integer.toUnsignedLong(buf.getInt()) * 1000),
					FileTime.fromMillis(Integer.toUnsignedLong(buf.getInt()) * 1000),
					FileTime.fromMillis(Integer.toUnsignedLong(buf.getInt()) * 1000),
					Encodeable.cString(buf),
					Encodeable.cString(buf)
				);
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.putShort((short)ModeFlag.encode(mode));
			buf.putShort((short)uid);
			buf.putShort((short)gid);
			buf.putInt((int)size);
			buf.putInt((int)(atime.toMillis() / 1000));
			buf.putInt((int)(mtime.toMillis() / 1000));
			buf.putInt((int)(ctime.toMillis() / 1000));
			Encodeable.cString(uidString, buf);
			Encodeable.cString(gidString, buf);
			return buf;
		}

		public boolean isDirectory() {
			return Arrays.asList(mode).contains(ModeFlag.IFDIR);
		}
	}
	
	public record Chmod(ModeFlag[] mode, String path) implements Encodeable {
		public static Chmod decode(ByteBuffer buf) {
			return new Chmod(
				ModeFlag.decode(Short.toUnsignedInt(buf.getShort())),
				Encodeable.cString(buf)
			);
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.putShort((short)ModeFlag.encode(mode));
			Encodeable.cString(path, buf);
			return buf;
		}
	}
	
	public record Rename(String path, String targetPath) implements Encodeable {
		public static Rename decode(ByteBuffer buf) {
			return new Rename(
				Encodeable.cString(buf),
				Encodeable.cString(buf)
			);
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			Encodeable.cString(path, buf);
			Encodeable.cString(targetPath, buf);
			return buf;
		}
	}
	
	public record Stat(String path) implements Encodeable {
		public static Stat decode(ByteBuffer buf) {
			return new Stat(Encodeable.cString(buf));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			Encodeable.cString(path, buf);
			return buf;
		}
	}
	
	public record Unlink(String path) implements Encodeable {
		public static Unlink decode(ByteBuffer buf) {
			return new Unlink(Encodeable.cString(buf));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			Encodeable.cString(path, buf);
			return buf;
		}
	}
	
	public record Write(int handle, ByteBuffer data) implements Encodeable {
		public static Write decode(ByteBuffer buf) {
			return new Write(
				Byte.toUnsignedInt(buf.get()),
				Encodeable.shortLPByteBuffer(buf)
			);
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)handle);
			Encodeable.shortLPByteBuffer(data, buf);
			return buf;
		}
	}
	
	public record WriteResult(ResultCode result, int written) implements Result  {
		public static WriteResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			if(res.isError()) {
				return new WriteResult(res, 0);
			}
			else {
				return new WriteResult(res, Short.toUnsignedInt(buf.getShort()));
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.putShort((short)written);
			return buf;
		}
	}
	
	public record ReadResult(ResultCode result, int read, ByteBuffer data) implements Result  {
		public static ReadResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			if(res.isError()) {
				return new ReadResult(res, 0, ByteBuffer.allocate(0));
			}
			else {
				var data = Encodeable.shortLPByteBuffer(buf);
				return new ReadResult(res, data.remaining(), data);
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			Encodeable.shortLPByteBuffer(data, buf);
			return buf;
		}
	}
	
	public record Read(int handle, int size) implements Encodeable {
		public static Read decode(ByteBuffer buf) {
			return new Read(
					Byte.toUnsignedInt(buf.get()),
					Short.toUnsignedInt(buf.getShort()));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)handle);
			buf.putShort((short)size);
			return buf;
		}
	}
	
	public record LSeekResult(ResultCode result, Optional<Long> position) implements Result  {
		public static LSeekResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			if(res.isError()) {
				return new LSeekResult(res, Optional.empty());
			}
			else {
				if(buf.hasRemaining()) {
					/* TODO need better way of deal with protocol version */
					return new LSeekResult(res, Optional.of(Integer.toUnsignedLong(buf.getInt())));
				}
				else
					return new LSeekResult(res, Optional.empty());
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.putInt((int)position.orElse(0l).longValue());
			return buf;
		}
	}
	
	public record LSeek(int handle, SeekType seekType, long position) implements Encodeable {
		public static LSeek decode(ByteBuffer buf) {
			return new LSeek(
					Byte.toUnsignedInt(buf.get()),
					SeekType.values()[Byte.toUnsignedInt(buf.get())],
					Integer.toUnsignedLong(buf.getInt()));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)handle);
			buf.put((byte)seekType.ordinal());
			buf.putInt((int)position);
			return buf;
		}
	}
	
	public record HandleResult(ResultCode result, int handle) implements Result  {
		public static HandleResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			if(res.isError()) {
				return new HandleResult(res, 0);
			}
			else {
				return new HandleResult(res, Byte.toUnsignedInt(buf.get()));
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.put((byte)handle);
			return buf;
		}
	}
	
	public record OpenDirXResult(ResultCode result, int handle, int entries) implements Result  {
		public static OpenDirXResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			if(res.isError()) {
				return new OpenDirXResult(res, 0, 0);
			}
			else {
				return new OpenDirXResult(res, Byte.toUnsignedInt(buf.get()), Short.toUnsignedInt(buf.getShort()));
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.put((byte)handle);
			buf.putShort((short)entries);
			return buf;
		}
	}
	
	public record MkDir(String path) implements Encodeable {
		public static MkDir decode(ByteBuffer buf) {
			return new MkDir(Encodeable.cString(buf));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			Encodeable.cString(path, buf);
			return buf;
		}
	}
	
	public record RmDir(String path) implements Encodeable {
		public static RmDir decode(ByteBuffer buf) {
			return new RmDir(Encodeable.cString(buf));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			Encodeable.cString(path, buf);
			return buf;
		}
	}
	
	public record OpenDir(String path) implements Encodeable {
		public static OpenDir decode(ByteBuffer buf) {
			return new OpenDir(Encodeable.cString(buf));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			Encodeable.cString(path, buf);
			return buf;
		}
	}
	
	public record OpenDirX(DirOptionFlag[] dirOptions, DirSortFlag[] sortOptions, int maxResults, String wildcard, String path) implements Encodeable {
		public static OpenDirX decode(ByteBuffer buf) {
			return new OpenDirX(
					DirOptionFlag.decode(Byte.toUnsignedInt(buf.get())),
					DirSortFlag.decode(Byte.toUnsignedInt(buf.get())),
					Short.toUnsignedInt(buf.getShort()),
					Encodeable.cString(buf),
					Encodeable.cString(buf));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)DirOptionFlag.encode(dirOptions));
			buf.put((byte)DirSortFlag.encode(sortOptions));
			buf.putShort((short)maxResults);
			Encodeable.cString(wildcard, buf);
			Encodeable.cString(path, buf);
			return buf;
		}
	}
	
	public record TellDir(int handle) implements Encodeable {
		public static TellDir decode(ByteBuffer buf) {
			return new TellDir(Byte.toUnsignedInt(buf.get()));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)handle);
			return buf;
		}
	}
	
	public record SeekDir(int handle, long position) implements Encodeable {
		public static SeekDir decode(ByteBuffer buf) {
			return new SeekDir(
				Byte.toUnsignedInt(buf.get()),
				Integer.toUnsignedLong(buf.getInt())
			);
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)handle);
			buf.putInt((int)position);
			return buf;
		}
	}
	
	public record TellDirResult(ResultCode result, long position) implements Result  {
		public static TellDirResult decode(ByteBuffer buf) {
			return new TellDirResult(
				Result.decodeResult(buf), 
				Integer.toUnsignedLong(buf.getInt())
			);
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.putInt((int)position);
			return buf;
		}
	}
	
	public record ReadDir(int handle) implements Encodeable {
		public static ReadDir decode(ByteBuffer buf) {
			return new ReadDir(Byte.toUnsignedInt(buf.get()));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)handle);
			return buf;
		}
	}
	
	public record ReadDirX(int handle, int entries) implements Encodeable {
		public static ReadDirX decode(ByteBuffer buf) {
			return new ReadDirX(
					Byte.toUnsignedInt(buf.get()),
					Byte.toUnsignedInt(buf.get()));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)handle);
			buf.put((byte)entries);
			return buf;
		}
	}
	
	public record ReadDirResult(ResultCode result, String entry) implements Result  {
		public static ReadDirResult decode(ByteBuffer buf) {
			return new ReadDirResult(Result.decodeResult(buf), Encodeable.cString(buf));
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			Encodeable.cString(entry, buf);
			return buf;
		}
	}
	
	public record ReadDirXResult(ResultCode result, int noEntries, int status, int dirpos, Entry... entries) implements Result  {
		public static ReadDirXResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			if(res.isOk()) {
				var noEntries = Byte.toUnsignedInt(buf.get()); 
				return new ReadDirXResult(
					res, 
					noEntries,
					Byte.toUnsignedInt(buf.get()),
					Short.toUnsignedInt(buf.getShort()),
					decodeEntries(noEntries, buf)
				);
			}
			else
				return new ReadDirXResult(res, 0, 0, 0);
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.put((byte)noEntries);
			buf.put((byte)status);
			buf.putShort((short)dirpos);
			for(var entry : entries) {
				entry.encode(buf);
			}
			return buf;
		}

		private static Entry[] decodeEntries(int noEntries, ByteBuffer buf) {
			var l = new ArrayList<Entry>();
			for(var i = 0 ; i < noEntries ; i++) {
				l.add(Entry.decode(buf));
			}
			return l.toArray(new Entry[0]);
		}
	}

	public record Size() implements Encodeable {
		public static Size decode(ByteBuffer buf) {
			return new Size();
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			return buf;
		}
	}

	public record SizeResult(ResultCode result, long size) implements Result {

		public static SizeResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			if(res.isError()) {
				return new SizeResult(res, 0);
			}
			else {
				return new SizeResult(res, Integer.toUnsignedLong(buf.getInt()));
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.putInt((int)size);
			return buf;
		}
	}

	public record Free() implements Encodeable {
		public static Free decode(ByteBuffer buf) {
			return new Free();
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			return buf;
		}
	}

	public record FreeResult(ResultCode result, long free) implements Result {

		public static FreeResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			if(res.isError()) {
				return new FreeResult(res, 0);
			}
			else {
				return new FreeResult(res, Integer.toUnsignedLong(buf.getInt()));
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			buf.putInt((int)free);
			return buf;
		}
	}

	public record CloseHandle(int handle) implements Encodeable {
		public static CloseHandle decode(ByteBuffer buf) {
			return new CloseHandle(Byte.toUnsignedInt(buf.get()));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			buf.put((byte)handle);
			return buf;
		}
	}

	public record MountResult(ResultCode result, Version version, Duration retryTime) implements Result  {
		
		public MountResult(ResultCode result, Duration retryTime) {
			this(result, TNFS.PROTOCOL_VERSION, retryTime);
		}

		public MountResult(ResultCode result) {
			this(result, TNFS.PROTOCOL_VERSION);
		}

		public MountResult(ResultCode result, Version version) {
			this(result, version, Duration.ofMillis(0));
		}
		
		public static MountResult decode(ByteBuffer buf) {
			var res = Result.decodeResult(buf);
			var ver = Version.decode(buf);
			if(res.isOk()) {
				return new MountResult(
					res,
					ver,
					Duration.ofMillis(Short.toUnsignedInt(buf.getShort()))
				);
			}
			else {
				return new MountResult(
					res,
					ver);
			}
		}

		@Override
		public ByteBuffer encodeResult(ByteBuffer buf) {
			version.encode(buf);
			buf.putShort((short)retryTime.toMillis());
			return buf;
		}
	}

	public record Mount(Version version, String path, Optional<String> userId, Optional<char[]> password) implements Encodeable {
		
		public Mount(String mountLocation, Optional<String> userId, Optional<char[]> password) {
			this(TNFS.PROTOCOL_VERSION, mountLocation, userId, password);
		}
		
		public static Mount decode(ByteBuffer buf) {
			return new Mount(
					Version.decode(buf),
					Encodeable.cString(buf),
					Encodeable.emptyIfBlank(Encodeable.cString(buf)),
					Encodeable.emptyIfBlank(Encodeable.cString(buf)).map(String::toCharArray));
		}

		@Override
		public ByteBuffer encode(ByteBuffer buf) {
			version.encode(buf);
			Encodeable.cString(path, buf);
			Encodeable.cString(userId.orElse(""), buf);
			Encodeable.cString(password.map(String::new).orElse(""), buf);
			return buf;
		}
		
		public String normalizedPath() {
			return path.equals("") ? "/" : path;
		}
	}
	
	private final static Map<Byte, Command<? extends Encodeable, ?>> actions = new HashMap<>();
	
	public final static Command<Mount, MountResult> MOUNT = new Command<>(0, "MOUNT", Mount::decode, Mount::encode, MountResult::decode);
	public final static Command<HeaderOnly, HeaderOnlyResult> UMOUNT = new Command<>(1, "UMOUNT", HeaderOnly::decode, HeaderOnly::encode, HeaderOnlyResult::decode);
	public final static Command<OpenDir, HandleResult> OPENDIR = new Command<>(0x10, "OPENDIR", OpenDir::decode, OpenDir::encode, HandleResult::decode);
	public final static Command<ReadDir,ReadDirResult> READDIR = new Command<>(0x11, "READDIR", ReadDir::decode, ReadDir::encode, ReadDirResult::decode);
	public final static Command<ReadDirX,ReadDirXResult> READDIRX = new Command<>(0x18, "READDIRX", ReadDirX::decode, ReadDirX::encode, ReadDirXResult::decode);
	public final static Command<CloseHandle,HeaderOnlyResult> CLOSEDIR= new Command<>(0x12, "CLOSEDIR", CloseHandle::decode, CloseHandle::encode, HeaderOnlyResult::decode);
	public final static Command<CloseHandle,HeaderOnlyResult> CLOSE = new Command<>(0x23, "CLOSE", CloseHandle::decode, CloseHandle::encode, HeaderOnlyResult::decode);
	public final static Command<OpenDirX, OpenDirXResult> OPENDIRX = new Command<>(0x17, "OPENDIRX", OpenDirX::decode, OpenDirX::encode, OpenDirXResult::decode);
	public final static Command<Open,HandleResult> OPEN = new Command<>(0x29, "OPEN", Open::decode, Open::encode, HandleResult::decode);
	public final static Command<Read,ReadResult> READ = new Command<>(0x21, "READ", Read::decode, Read::encode, ReadResult::decode);
	public final static Command<Write,WriteResult> WRITE = new Command<>(0x22, "WRITE", Write::decode, Write::encode, WriteResult::decode);
	public final static Command<Size,SizeResult> SIZE = new Command<>(0x30, "SIZE", Size::decode, Size::encode, SizeResult::decode);
	public final static Command<Free,FreeResult> FREE = new Command<>(0x31, "FREE", Free::decode, Free::encode, FreeResult::decode);
	public final static Command<Unlink,HeaderOnlyResult> UNLINK = new Command<>(0x26, "UNLINK", Unlink::decode, Unlink::encode, HeaderOnlyResult::decode);
	public final static Command<MkDir,HeaderOnlyResult> MKDIR = new Command<>(0x13, "MKDIR", MkDir::decode, MkDir::encode, HeaderOnlyResult::decode);
	public final static Command<RmDir,HeaderOnlyResult> RMDIR = new Command<>(0x14, "RMMDIR", RmDir::decode, RmDir::encode, HeaderOnlyResult::decode);
	public final static Command<TellDir,TellDirResult> TELLDIR = new Command<>(0x15, "TELLDIR", TellDir::decode, TellDir::encode, TellDirResult::decode);
	public final static Command<SeekDir,HeaderOnlyResult> SEEKDIR = new Command<>(0x16, "SEEKDIR", SeekDir::decode, SeekDir::encode, HeaderOnlyResult::decode);
	public final static Command<Stat,StatResult> STAT = new Command<>(0x24, "STAT", Stat::decode, Stat::encode, StatResult::decode);
	public final static Command<LSeek,LSeekResult> LSEEK = new Command<>(0x25, "LSEEK", LSeek::decode, LSeek::encode, LSeekResult::decode);
	public final static Command<Chmod,HeaderOnlyResult> CHMOD = new Command<>(0x27, "CHMOD", Chmod::decode, Chmod::encode, HeaderOnlyResult::decode);
	public final static Command<Rename,HeaderOnlyResult> RENAME = new Command<>(0x28, "RENAME", Rename::decode, Rename::encode, HeaderOnlyResult::decode);
	
	@SuppressWarnings("unchecked")
	public static <CODEC> Command<? extends CODEC, ?> get(byte code) {
		var op = (Command<? extends CODEC, ?>) actions.get(code);
		if(op == null)
			throw new IllegalArgumentException("No op with code " + code);
		return op;
	}
	
	private final byte command;
	private final String name;
	private final Function<ByteBuffer, CODEC> decoder;
	private final BiFunction<CODEC, ByteBuffer, ByteBuffer> encoder;
	private final Function<ByteBuffer, RESULT> resultDecoder;
	
	public Command(
			int code, 
			String name, 
			Function<ByteBuffer, CODEC> decoder, 
			BiFunction<CODEC, ByteBuffer, ByteBuffer> encoder, 
			Function<ByteBuffer, RESULT> resultDecoder) {
		if(actions.containsKey((byte)code)) {
			throw new IllegalArgumentException("Conflicting Op code " + code);
		}
		actions.put((byte)code, this);
		this.name = name;
		this.command = (byte)code;
		this.decoder = decoder;
		this.encoder = encoder;
		this.resultDecoder = resultDecoder;
	}
	
	public byte code() {
		return command;
	}
	
	public CODEC decode(ByteBuffer data) {
		if(decoder == null)
			return null;
		else
			return decoder.apply(data);
	}
	
	public RESULT decodeResult(ByteBuffer data) {
		if(resultDecoder == null)
			return null;
		else
			return resultDecoder.apply(data);
	}
	
	public ByteBuffer encode(CODEC data, ByteBuffer buf) {
		if(encoder != null)
			encoder.apply(data, buf);
		return buf;
	}
	
	public String name() {
		return name;
	}
	
	@Override
	public String toString() {
		return "Op [code=" + command + ", name=" + name + "]";
	}

}
