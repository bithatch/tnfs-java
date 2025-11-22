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

import java.io.File;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import com.sshtools.jajafx.AbstractTile;
import com.sshtools.jajafx.JajaFXApp.DarkMode;
import com.sshtools.jajafx.PrefBind;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import uk.co.bithatch.tnfs.mountlib.MountConfiguration;
import uk.co.bithatch.tnfs.mountlib.MountConstants;

public class OptionsPage extends AbstractTile<DriveApp>{

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(OptionsPage.class.getName());

	private PrefBind prefBind;

	@FXML
	protected ComboBox<DarkMode> darkMode;
	@FXML
	protected TextField mountPath;
	@FXML
	protected CheckBox discover;
	@FXML
	protected CheckBox automountDiscovered;

	private Configuration config;
	private MountConfiguration mountConfig;

	@Override
	protected void onConfigure() {

		config = getContext().configuration();
		mountConfig = getContext().mountConfiguration();

		/* General */
		darkMode.getItems().addAll(DarkMode.values());
		darkMode.getSelectionModel().select(DarkMode.AUTO);

		darkMode.setConverter(new StringConverter<DarkMode>() {
			@Override
			public String toString(DarkMode object) {
				return RESOURCES.getString("darkMode." + object.name());
			}

			@Override
			public DarkMode fromString(String string) {
				return null;
			}
		});
		
		mountPath.setText(config.document().get(Constants.MOUNT_PATH));
		mountPath.textProperty().addListener((c,o,n) -> config.document().put(Constants.MOUNT_PATH, Configuration.encodeMountPath(n)));

		discover.setSelected(mountConfig.mountConfiguration().getBoolean(MountConstants.DISCOVER_KEY));
		discover.selectedProperty()
				.addListener((c, o, n) -> mountConfig.mountConfiguration().put(MountConstants.DISCOVER_KEY, n));
		
		automountDiscovered.setSelected(mountConfig.mountConfiguration().getBoolean(MountConstants.AUTOMOUNT_DISCOVERED));
		automountDiscovered.selectedProperty()
				.addListener((c, o, n) -> mountConfig.mountConfiguration().put(MountConstants.AUTOMOUNT_DISCOVERED, n));

		/* TODO change to use jini */
		prefBind = new PrefBind(Preferences.userNodeForPackage(OptionsPage.class));
		prefBind.bind(DarkMode.class, darkMode);

	}

	@Override
	public void close() {
		prefBind.close();
	}

	@FXML
	private void back(ActionEvent evt) {
		getTiles().remove(this);

	}

	@FXML
	private void browse(ActionEvent evt) {
		var fileChooser = new DirectoryChooser();
		var initDir = new File(Configuration.decodeMountPath(mountPath.getText()));
		while(initDir != null && !initDir.exists()) {
			initDir = initDir.getParentFile();
		}
		fileChooser.setInitialDirectory(initDir);
		fileChooser.setTitle(RESOURCES.getString("mountPath.browse.title"));;

		var selectedFile = fileChooser.showDialog(getContext().getPrimaryStage());
		if (selectedFile != null) {
			mountPath.setText(Configuration.encodeMountPath(selectedFile.getAbsolutePath()));
		}
	}

}
