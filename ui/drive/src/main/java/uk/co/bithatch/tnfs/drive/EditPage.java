/**
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
import java.util.ResourceBundle;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.sshtools.jajafx.AbstractTile;
import com.sshtools.jini.INI.Section;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.extensions.Mounts;
import uk.co.bithatch.tnfs.lib.Protocol;
import uk.co.bithatch.tnfs.mountlib.MountConstants;
import uk.co.bithatch.tnfs.mountlib.MountManager.MountSource;
import uk.co.bithatch.tnfs.mountlib.MountManager.Mountable;

public class EditPage extends AbstractTile<DriveApp> {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(EditPage.class.getName());

	@FXML
	private CheckBox secure;
	@FXML
	private CheckBox readOnly;
	@FXML
	private TextField name;
	@FXML
	private ComboBox<String> path;
	@FXML
	private ComboBox<Protocol> protocol;
	@FXML
	private TextField hostname;
	@FXML
	private Spinner<Integer> packetSize;
	@FXML
	private Spinner<Integer> port;

	private ScheduledFuture<?> reloadTask;

	private Mountable mountable;

	@Override
	protected void onConfigure() {
		hostname.textProperty().addListener((c, o, n) -> {
			triggerPathLoad();
		});
		packetSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(256, 32768));
		port.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 65535));
		protocol.setItems(FXCollections.observableArrayList(Protocol.values()));
	}

	void setup(Mountable mountable) {
		this.mountable = mountable;

		name.setText(mountable.name());
		path.getSelectionModel().select(mountable.key().path());
		hostname.setText(mountable.key().hostname());
		secure.setSelected(
				mountable.configuration().map(cfg -> cfg.getBoolean(MountConstants.SECURE_KEY)).orElse(false));
		protocol.getSelectionModel().select(mountable.key().protocol());
		packetSize.getValueFactory().setValue(mountable.configuration()
				.map(cfg -> cfg.getIntOr(MountConstants.PACKET_SIZE).
					orElseGet(() -> protocol.getSelectionModel().getSelectedItem().defaultMessageSize())).
					orElseGet(() -> protocol.getSelectionModel().getSelectedItem().defaultMessageSize()));

		if (mountable.source() == MountSource.MDNS) {
			if (name.getText().equals(mountable.originalName()))
				name.setText(mountable.name());
			name.setPromptText(mountable.name());
			path.setDisable(true);
			hostname.setDisable(true);
			port.setDisable(true);
			protocol.setDisable(true);
		} else {
			name.setPromptText("");
			path.setDisable(false);
			hostname.setDisable(false);
			port.setDisable(false);
			protocol.setDisable(false);
		}

		triggerPathLoad();
	}

	private void triggerPathLoad() {
		if (reloadTask != null) {
			reloadTask.cancel(false);
		}
		reloadTask = getContext().getContainer().getScheduler().schedule(() -> {
			loadPaths();
		}, 1, TimeUnit.SECONDS);
	}

	private void loadPaths() {
		try (var clnt = new TNFSClient.Builder().withHostname(mountable.key().hostname())
				.withPort(mountable.key().port()).withProtocol(mountable.key().protocol()).build()) {
			var pl = clnt.extension(Mounts.class).mounts().toList();
			Platform.runLater(() -> path.getItems().setAll(pl));
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	@FXML
	private void back(ActionEvent evt) {
		getTiles().pop();
	}

	@FXML
	private void save(ActionEvent evt) {
		mountable.configuration().ifPresentOrElse(cfg -> {
			if (mountable.source() == MountSource.CONFIGURATION) {
				updateBasicAttributes(cfg);
			}
			updateCommonAttributes(cfg);
		}, () -> {
			Section sec;
			if (mountable.source() == MountSource.MDNS) {
				sec = getContext().mountConfiguration().newMDNSMount(mountable.key().id());
			} else {
				sec = getContext().mountConfiguration().newMount(name.getText());
				updateBasicAttributes(sec);
			}
			updateCommonAttributes(sec);
			mountable.configure(sec);
		});
		getTiles().pop();
	}

	private void updateCommonAttributes(Section cfg) {
		cfg.put(MountConstants.SECURE_KEY, secure.isSelected());
		cfg.put(MountConstants.PACKET_SIZE, packetSize.getValue());
		if (mountable.source() == MountSource.MDNS && name.getText().equals(mountable.originalName()))
			cfg.remove(MountConstants.NAME_KEY);
		else
			cfg.put(MountConstants.NAME_KEY, name.getText());
	}

	private void updateBasicAttributes(Section cfg) {
		cfg.putEnum(MountConstants.PROTOCOL_KEY, protocol.getSelectionModel().getSelectedItem());
		cfg.put(MountConstants.HOSTNAME_KEY, hostname.getText());
		var portValue = port.getValue().intValue();
		if (portValue == 0) {
			cfg.remove(MountConstants.PORT_KEY);
		} else {
			cfg.put(MountConstants.PORT_KEY, portValue);
		}
		cfg.put(MountConstants.PATH_KEY, path.getSelectionModel().getSelectedItem());
	}
}
