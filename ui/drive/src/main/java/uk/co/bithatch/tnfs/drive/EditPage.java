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

import java.util.ResourceBundle;

import com.sshtools.jajafx.AbstractTile;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import uk.co.bithatch.tnfs.mountlib.MountConstants;
import uk.co.bithatch.tnfs.mountlib.MountManager.MountSource;
import uk.co.bithatch.tnfs.mountlib.MountManager.Mountable;

public class EditPage extends AbstractTile<DriveApp>  {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(EditPage.class.getName());

	@FXML
	private CheckBox secure;
	@FXML
	private TextField name;
	@FXML
	private TextField path;
	@FXML
	private TextField hostname;

	@Override
	protected void onConfigure() {
	}

	void setup(Mountable mountable) {
		name.setText(mountable.name());
		path.setText(mountable.key().path());
		hostname.setText(mountable.key().hostname());
		secure.setSelected(mountable.configuration().map(cfg -> cfg.getBoolean(MountConstants.SECURE_KEY)).orElse(false));
		
		if(mountable.source() == MountSource.MDNS) {
			name.setDisable(true);
			path.setDisable(true);
			hostname.setDisable(true);
		}
		else {
			name.setDisable(false);
			path.setDisable(false);
			hostname.setDisable(false);
		}
	}

	@FXML
	private void back(ActionEvent evt) { 
		getTiles().pop();
	}
}
