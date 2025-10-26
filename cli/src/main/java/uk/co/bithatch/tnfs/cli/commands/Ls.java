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
package uk.co.bithatch.tnfs.cli.commands;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.jline.builtins.Styles;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.TNFSTP;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;
import uk.co.bithatch.tnfs.lib.DirEntryFlag;
import uk.co.bithatch.tnfs.lib.DirOptionFlag;
import uk.co.bithatch.tnfs.lib.DirSortFlag;
import uk.co.bithatch.tnfs.lib.Util;

/**
 * List directory command.
 */
@Command(name = "ls", aliases = { "cat", "dir" }, mixinStandardHelpOptions = true, description = "List directory.")
public class Ls extends TNFSTPCommand implements Callable<Integer> {
	
	public enum Sort {
		NONE, NAME, SIZE, MODIFIED
	}
	
	@Option(names = {"-l", "--long"}, description = "Show long format.")
	private boolean longFormat;
	
	@Option(names = {"-a", "--all"}, description = "Show all files.")
	private boolean all;
	
	@Option(names = {"-r", "--reverse"}, description = "Reverse sort order.")
	private boolean reverse;
	
	@Option(names = {"-e", "--basic"}, description = "Display basic listing, no other options are supported.")
	private boolean basic;
	
	@Option(names = {"-s", "--case-sensitive"}, description = "Case sensitive sorting.")
	private boolean caseSensitive;
	
	@Option(names = {"-D", "--match-directories"}, description = "Match directories as well when a wildcard pattern is used.")
	private boolean dirPattern;
	
	@Option(names = {"-S", "--sort"}, description = "Field to sort by.")
	private Sort sort = Sort.NAME;
	
	@Option(names = {"-M", "--max-results"}, description = "Maximum number of results, 0 for all.")
	private int maxResults;
	
	@Option(names = {"-x", "--exact-sizes"}, description = "Use exact bytes for sizes instead of rough sizes.")
	private boolean exactSizes;

	@Parameters(arity = "0..1", description = "Path of remote directory, or wildcard to match returned entries with.")
	private Optional<String> path;

	public Ls() {
		super(FilenameCompletionMode.DIRECTORIES_REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		var container = getContainer();
		var wtr = container.getTerminal().writer();

		var dirOptions = new ArrayList<DirOptionFlag>();
		var dirSort = new ArrayList<DirSortFlag>();
		var apath = path.orElseGet(container::getCwd);
		var base = Util.basename(apath);
		var wildcard = "";
        var resolver = Styles.lsStyle();
		
		if(base.contains("*") || base.contains("?")) {
			wildcard = base;
			apath = Util.dirname(apath);
		}
		
		if(!basic) {
			
			if(all) {
				dirOptions.add(DirOptionFlag.NO_SKIPHIDDEN);
				dirOptions.add(DirOptionFlag.NO_SKIPSPECIAL);
			}
			
			if(dirPattern) {
				dirOptions.add(DirOptionFlag.DIR_PATTERN);
			}
			
			if(reverse) {
				dirSort.add(DirSortFlag.DESCENDING);
			}
			
			if(caseSensitive) {
				dirSort.add(DirSortFlag.CASE);
			}
			
			switch(sort) {
			case MODIFIED:
				dirSort.add(DirSortFlag.MODIFIED);
				break;
			case SIZE:
				dirSort.add(DirSortFlag.SIZE);
				break;
			case NONE:
				dirSort.add(DirSortFlag.NONE);
				break;
			default:
				break;
			}
			
			var width = container.getTerminal().getWidth();
			if(width == 0)
				width = 132;
			
			var longNameWidth = width - 30;
			
			try(var dir = container.getMount().directory(
					maxResults,
					apath,
					wildcard,
					dirOptions.toArray(new DirOptionFlag[0]),
					dirSort.toArray(new DirSortFlag[0])
				)) {
				var stream = dir.stream();
				stream.forEach(file -> {
					var flags = Arrays.asList(file.flags());
					
					if(longFormat) {
						wtr.println(String.format("%1s %s %8s %15s",
								new Object[] { 
										flags.contains(DirEntryFlag.DIR) ? "D" : ( flags.contains(DirEntryFlag.SPECIAL) ? "S" : "-"),
												TNFSTP.getDisplay(getContainer().getTerminal(), file, resolver, longNameWidth, getContainer().getSeparator(), false), 
										exactSizes ? String.valueOf(file.size()) : Util.formatSize(file.size()),
										DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(file.mtime().toInstant().atZone(ZoneId.systemDefault()))
								}));
					}
					else {
						wtr.println(TNFSTP.getDisplay(getContainer().getTerminal(), file, resolver, 0, getContainer().getSeparator(), true));
					}
				});
			}
		}
		else {
			if(longFormat || all || reverse || caseSensitive || sort != Sort.NAME || maxResults > 0) {
				throw new IllegalArgumentException("Options not supported with basic listing.");
			}
			try(var stream = container.getMount().list(apath)) {
				stream.forEach(wtr::println);
			}
		}
		wtr.flush();
		return 0;
	}

	
}