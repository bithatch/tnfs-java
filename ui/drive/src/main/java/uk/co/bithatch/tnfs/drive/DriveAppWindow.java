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

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jajafx.JajaFXAppWindow;
import com.sshtools.jajafx.PageTransition;
import com.sshtools.jajafx.TitleBar;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import uk.co.bithatch.nativeimage.annotations.Bundle;

@Bundle
public class DriveAppWindow extends JajaFXAppWindow<DriveApp> {

	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(DriveAppWindow.class.getName());

	static Logger LOG = LoggerFactory.getLogger(DriveAppWindow.class);

	private FontIcon mainMenu;
	private FontIcon add;

	public DriveAppWindow(Stage stage, DriveApp app) {
		super(stage, app);
		scene.getRoot().getStyleClass().add("fusemount");
		scene.setFill(Color.TRANSPARENT);
	}

	@Override
	public void setContent(Node content) {
		var tiles = app.getTiles();
		tiles.indexProperty().addListener((c, o, n) -> {
			if (n.intValue() == 0) {
				titleBar().addAccessories(mainMenu, add);
			} else {
				titleBar().removeAccessories(mainMenu, add);
			}
		});

		mainMenu.disableProperty().addListener((c, o, n) -> {
			System.out.println("DIS " + n);
		});

		super.setContent(content);
	}

	@Override
	protected TitleBar createTitleBar() {
		var title = super.createTitleBar();
		title.maximizeVisibleProperty().setValue(true);

		mainMenu = new FontIcon();
		mainMenu.setIconSize(18);
		mainMenu.setOnMouseClicked(evt -> {
			evt.consume();
			createMainMenu().show(mainMenu, evt.getScreenX(), evt.getScreenY());
		});
		mainMenu.setIconCode(FontAwesomeSolid.ELLIPSIS_V);

		add = new FontIcon();
		add.setIconCode(FontAwesomeSolid.FOLDER_PLUS);
		add.setIconSize(18);
		add.setOnMouseClicked(evt -> {
//			platform.audio().ifPresent(a -> a.muted(!a.muted()));
		});

		title.addAccessories(mainMenu, add);

		return title;
	}

	protected ContextMenu createMainMenu() {

		var optionsMenuItem = new MenuItem(RESOURCES.getString("options"));
		optionsMenuItem.setOnAction(e -> {
			app.getTiles().popup(OptionsPage.class, PageTransition.FROM_RIGHT);
		});

		var aboutMenuItem = new MenuItem(RESOURCES.getString("about"));
		aboutMenuItem.setOnAction(e -> {
			about();
		});

		var exitMenuItem = new MenuItem(RESOURCES.getString("quit"));
		exitMenuItem.setOnAction(e -> {
			stage().close();
		});

		return new ContextMenu(optionsMenuItem, new SeparatorMenuItem(), aboutMenuItem, exitMenuItem);
	}

	public DriveApp app() {
		return app;
	}

	@Override
	public StageStyle borderlessStageStyle() {
		return StageStyle.TRANSPARENT;
	}

	private void about() {

		Platform.runLater(() -> {
			var actions = new AboutPane(app);

			var stg = new Stage();
			var wnd = new JajaFXAppWindow<>(stg, app, 400, 400);
			wnd.setContent(actions);
			wnd.scene().getRoot().setId("about-dialog");

			stg.initOwner(stage());
			stg.initModality(Modality.APPLICATION_MODAL);

			stg.getIcons().add(new Image(app.getIcon().toExternalForm()));
			stg.setResizable(false);
			stg.setTitle(RESOURCES.getString("about"));
			stg.showAndWait();
		});
	}

}
