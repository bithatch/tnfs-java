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
import java.text.MessageFormat;
import java.util.ResourceBundle;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import com.sshtools.jajafx.AbstractTile;
import com.sshtools.jajafx.PageTransition;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import uk.co.bithatch.tnfs.client.TNFSMount.Flag;
import uk.co.bithatch.tnfs.mountlib.MountManager;
import uk.co.bithatch.tnfs.mountlib.MountManager.MountListener;
import uk.co.bithatch.tnfs.mountlib.MountManager.MountSource;
import uk.co.bithatch.tnfs.mountlib.MountManager.Mountable;

public class MountsPage extends AbstractTile<DriveApp> implements MountListener {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(MountsPage.class.getName());

	@FXML
	private BorderPane container;
	@FXML
	private ListView<Mountable> mounts;
	@FXML
	private MenuItem mount;
	@FXML
	private MenuItem unmount;
	@FXML
	private MenuItem remove;
	@FXML
	private MenuItem open;
	@FXML
	private MenuItem edit;

	private MountManager mgr;
	private LocalFileSystemManager localMgr;
	private ObservableList<Mountable> mountList;

	@Override
	protected void onConfigure() {
		mounts.setCellFactory(lv -> new MountableCell());
		localMgr = getContext().localFileSystemManager();
		mgr = getContext().mountManager();
		mgr.addListener(this);
		mountList = FXCollections.observableArrayList(mgr.mounts());
		mounts.setItems(mountList);
		mounts.getSelectionModel().selectedItemProperty().addListener((c,o,n) -> updateMenus());
		mgr.start();
		updateMenus();
	}

	private final class MountableCell extends ListCell<Mountable> {

		private final MountableCellController defaultController = new MountableCellController();
		private final Node defaultView = defaultController.getView();

		@Override
		protected void updateItem(Mountable item, boolean empty) {
			super.updateItem(item, empty);
			if (empty) {
				setGraphic(null);
			} else {
				defaultController.setItem(item);
				setGraphic(defaultView);
			}
		}
	}

	private final class MountableCellController {
		private BorderPane view = new BorderPane();
		private VBox textBox = new VBox();

		private Label name = new Label();
		private Label id = new Label();
		private Label path = new Label();
		private Label proto = new Label();
		private FontIcon icon = new FontIcon();
		private FontIcon badge = new FontIcon();
		private StackPane stack = new StackPane();
		private Mountable item;

		{
			id.setAlignment(Pos.CENTER_RIGHT);
			id.getStyleClass().add("muted");

			name.setAlignment(Pos.CENTER_LEFT);
			name.getStyleClass().add("strong");

			path.setAlignment(Pos.CENTER_LEFT);
			
			proto.setAlignment(Pos.CENTER_RIGHT);
			proto.getStyleClass().add("muted");

			var top = new BorderPane();
			top.setLeft(name);
			BorderPane.setAlignment(name, Pos.CENTER_LEFT);
			top.setRight(id);
			BorderPane.setAlignment(id, Pos.CENTER_RIGHT);

			var bottom = new BorderPane();
			bottom.setLeft(path);
			BorderPane.setAlignment(path, Pos.CENTER_LEFT);
			bottom.setRight(proto);
			BorderPane.setAlignment(proto, Pos.CENTER_RIGHT);

			textBox.getStyleClass().addAll("spaced", "lpad");
			textBox.getChildren().addAll(top, bottom);
			
			icon.setIconSize(48);
			icon.setIconCode(FontAwesomeSolid.HDD);
			
			badge.setIconSize(24);
			badge.setIconCode(FontAwesomeSolid.USER);
			badge.setTextAlignment(TextAlignment.RIGHT);
			
			var badgeAnchor = new AnchorPane(badge);
			AnchorPane.setRightAnchor(badge, 0d);
			AnchorPane.setBottomAnchor(badge, 0d);

//			stack.getStyleClass().add("muted");
			stack.getChildren().add(icon);
			stack.getChildren().add(badgeAnchor);
			stack.setOpacity(0.25);
			
			view.setLeft(stack);
			view.setCenter(textBox);
		}

		public Node getView() {
			return view;
		}

		public void setItem(Mountable item) {
			this.item = item;

//			var count = item.count();
//			if(count == 1)
//				versionCount.setText(RESOURCES.getString("singleVersion"));
//			else
//				versionCount.setText(MessageFormat.format(RESOURCES.getString("versionCount"), count));
//			
//			versionCount.setVisible(count > 0);

			name.setText(item.name());
			id.setText(item.key().hostname());
			proto.setText(String.format("%s %d", item.key().protocol(), item.key().port()));
			item.mountOr().ifPresentOrElse(mnt -> {

				icon.getStyleClass().setAll("icon-success");
				stack.setOpacity(1);
				if(mnt.flags().contains(Flag.ENCRYPTED)) {
					if(mnt.flags().contains(Flag.AUTHENTICATED)) {
						badge.setIconCode(FontAwesomeSolid.USER_SHIELD);
					}
					else {
						badge.setIconCode(FontAwesomeSolid.SHIELD_ALT);
					}
					badge.setVisible(true);
				}
				else {
					if(mnt.flags().contains(Flag.AUTHENTICATED)) {
						badge.setIconCode(FontAwesomeSolid.USER);
						badge.setVisible(true);
					}
					else {
						badge.setVisible(false);
					}
				}
				
			}, () -> {
				
				item.errorOr().ifPresentOrElse(err -> {
					path.setText(item.key().path() + " - "+ err.getMessage());
					icon.getStyleClass().setAll("icon-danger");
					badge.setIconCode(FontAwesomeSolid.EXCLAMATION_CIRCLE);
					badge.setVisible(true);
					stack.setOpacity(1);
				}, () -> {
					path.setText(item.key().path());
					icon.getStyleClass().setAll();
					badge.setVisible(false);
					stack.setOpacity(0.25);
				});
				
			});
			

		}

	}

	@Override
	public void mountAdded(Mountable mountable) {
		Platform.runLater(() -> {
			mountList.add(mountable);
			updateMenus();
		});
	}

	@Override
	public void mountRemoved(Mountable mountable) {
		Platform.runLater(() -> {
			mountList.remove(mountable);
			updateMenus();
		});
	}

	@Override
	public void mounted(Mountable mountable) {
		Platform.runLater(() -> {
			mounts.refresh();
			updateMenus();
		});
	}

	@Override
	public void unmounted(Mountable mountable) {
		Platform.runLater(() -> {
			mounts.refresh();
			updateMenus();
		});
	}

	@Override
	public void mountFailed(Mountable mountable, Exception error) {
		Platform.runLater(() -> {
			mounts.refresh();
			updateMenus();
		});
	}
	
	@Override
	public void shown() {
		mounts.refresh();
		updateMenus();
	}

	@FXML
	private void mount(ActionEvent aevt) {
		getContext().getContainer().getScheduler().submit(() -> {
			mgr.mount(mounts.getSelectionModel().getSelectedItem());
		});
	}
	
	@FXML
	private void edit(ActionEvent aevt) {
		getTiles().popup(EditPage.class, PageTransition.FROM_RIGHT).setup(mounts.getSelectionModel().getSelectedItem());
	}
	
	@FXML
	private void unmount(ActionEvent aevt) throws IOException {
		getContext().getContainer().getScheduler().submit(() -> {
			mgr.unmount(mounts.getSelectionModel().getSelectedItem());
		});
	}
	
	@FXML
	private void remove(ActionEvent aevt) {
		var sel = mounts.getSelectionModel().getSelectedItem();
		var alert = new Alert(AlertType.CONFIRMATION);
		alert.initOwner(getScene().getWindow());
		alert.setTitle(RESOURCES.getString("confirmRemove.title"));
		alert.setContentText(MessageFormat.format(RESOURCES.getString("confirmRemove.content"), sel.name()));
		alert.setHeaderText(RESOURCES.getString("confirmRemove.header"));
		var res = alert.showAndWait();
		if (res.isPresent() && res.get() == ButtonType.OK) {
			getContext().getContainer().getScheduler().submit(() -> {
				mgr.remove(sel);
			});
		} 
	}
	
	@FXML
	private void open(ActionEvent aevt) {
		getContext().getHostServices().showDocument(localMgr.mountPount(mounts.getSelectionModel().getSelectedItem().key()).toUri().toString());
	}
	
	private void updateMenus() {
		var sel = mounts.getSelectionModel().getSelectedItem();
		open.setDisable(sel == null || !sel.isMounted());
		mount.setDisable(sel == null || sel.isMounted());
		unmount.setDisable(sel == null || !sel.isMounted());
		remove.setDisable(sel == null || sel.source() == MountSource.MDNS);
	}
}
