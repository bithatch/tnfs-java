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

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

public class Dialogs {

	public static void error(Stage stage, String title, String header, Exception error) {
		error(stage, title, header, null, error);
	}

	public static void error(Stage stage, String title, String header, String content) {
		error(stage, title, header, content, null);
		
	}

	public static void error(Stage stage, String title, String header, String content, Exception error) {
		var alert = alert(stage, Alert.AlertType.ERROR, title, header,
				content == null ? error.getLocalizedMessage() : ( error == null ? content : content + " " + error.getLocalizedMessage() ));
		alert.showAndWait();
	}

	public static void info(Stage stage, String title, String header, String content) {
		var alert = alert(stage, Alert.AlertType.INFORMATION, title, header, content);
		alert.showAndWait();
	}

	public static boolean confirm(Stage stage, String title, String header, String content) {
		var alert = alert(stage, Alert.AlertType.CONFIRMATION, title, header, content);
		var res = alert.showAndWait();
		if (res.isPresent() && res.get() == ButtonType.OK) {
			return true;
		} else {
			return false;
		}
	}

	private static Alert alert(Stage stage, Alert.AlertType type, String title, String header, String content) {
		var alert = new Alert(type);
		alert.initOwner(stage);
		alert.setTitle(title);
		alert.setContentText(content);
		alert.setHeaderText(header);
		return alert;
	}
}
