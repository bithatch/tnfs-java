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
package uk.co.bithatch.tnfs.drive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class AboutPane extends StackPane {
	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(AboutPane.class.getName());

	@FXML
	private Label version;

	@FXML
	private Label copyright;

	private final DriveApp app;

	public AboutPane(DriveApp app) {
		this.app = app;
		
		var loader = new FXMLLoader(getClass().getResource("AboutPane.fxml"));
		loader.setController(this);
		loader.setRoot(this);
		loader.setResources(RESOURCES);
		try {
			loader.load();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
		version.setText(MessageFormat.format(RESOURCES.getString("version"), String.join(" ", Drive.version())));
		copyright.setText(
				MessageFormat.format(RESOURCES.getString("copyright"), String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));

	}
	
	@FXML
	private void evtHomePage() {
		app.getHostServices().showDocument("https://bithatch.co.uk");
	}

}
