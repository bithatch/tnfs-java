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
import uk.co.bithatch.tnfs.web.elfinder.command.ArchiveCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.DimCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.DuplicateCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.ElfinderCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.ExtractCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.FileCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.GetCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.LsCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.MkdirCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.MkfileCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.OpenCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.ParentsCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.PasteCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.PutCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.RenameCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.RmCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.SearchCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.SizeCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.TmbCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.TreeCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.UploadCommand;

open module uk.co.bithatch.tnfs.daemon {
	requires transitive uk.co.bithatch.tnfs.client;
	requires transitive uk.co.bithatch.tnfs.client.extensions;
	requires info.picocli;
	requires org.slf4j.simple;
	requires jul.to.slf4j;
	requires org.slf4j;
	requires com.sshtools.uhttpd;
	requires transitive org.apache.commons.compress;
	requires org.json;
	requires java.desktop;
	requires java.sql;
	requires org.apache.commons.io;
	requires org.apache.tika.core;
	requires jakarta.xml.bind;
	requires transitive com.sshtools.jini.config;
	requires javax.jmdns;
	requires com.sshtools.porter;
	requires uk.co.bithatch.tnfs.daemonlib;
	
	uses ElfinderCommand;
	
	provides ElfinderCommand with ArchiveCommand, DimCommand, DuplicateCommand,
					ExtractCommand, FileCommand, GetCommand, LsCommand, 
					MkdirCommand, MkfileCommand, OpenCommand, ParentsCommand,
					PasteCommand, PutCommand, RenameCommand, RmCommand,
					SearchCommand, SizeCommand, TmbCommand, TreeCommand,
					UploadCommand;
}