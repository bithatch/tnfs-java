/*
 * Copyright © 2025 Bithatch (bithatch@bithatch.co.uk)
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
package uk.co.bithatch.tnfs.web;

import java.text.MessageFormat;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import uk.co.bithatch.tnfs.web.elfinder.command.ElfinderCommand;
import uk.co.bithatch.tnfs.web.elfinder.command.ElfinderCommandFactory;

public class ServiceCommandFactory implements ElfinderCommandFactory {

	private Map<String, ElfinderCommand> commands = new ConcurrentHashMap<>();
	
	public ServiceCommandFactory() {
		ServiceLoader.load(ElfinderCommand.class).forEach(fc -> {
			var name = fc.getClass().getSimpleName();
			if(!name.endsWith("Command")) {
				throw new IllegalArgumentException(MessageFormat.format("Command implementation {0} class name must end literally with `Command`", name));
			}
			commands.put(name.substring(0, name.length() - 7).toLowerCase(), fc);
		});
	}

	@Override
	public ElfinderCommand get(String commandName) {
		var cmd = commands.get(commandName);
		if(cmd == null)
			throw new IllegalArgumentException(MessageFormat.format("No command implementation for {0}", commandName));
		return cmd;
	}
}
