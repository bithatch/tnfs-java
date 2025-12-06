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

import java.net.InetAddress;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import com.sshtools.jajafx.FXUtil;
import com.sshtools.jajafx.JajaFXApp;
import com.sshtools.jajafx.Tiles;
import com.sshtools.jini.config.Monitor;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.stage.Stage;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;
import uk.co.bithatch.tnfs.daemonlib.MDNS;
import uk.co.bithatch.tnfs.mountlib.MountConfiguration;
import uk.co.bithatch.tnfs.mountlib.MountManager;

@Reflectable
@TypeReflect
@Bundle
public class DriveApp extends JajaFXApp<Drive, DriveAppWindow> {

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Drive.class.getName());

	private Tiles<DriveApp> tiles;

	private Monitor monitor;
	private Configuration config;

	private MountManager mountMgr;
	private MDNS mdns;

	private MountConfiguration mountConfig;

	private LocalFileSystemManager localFileSystemManager;

	@Reflectable
	public DriveApp() {
		super(DriveApp.class.getResource("icon.png"), 
				RESOURCES.getString("title"), 
				(Drive) Drive.getInstance(),
				Preferences.userNodeForPackage(DriveApp.class)
		);
	}

	public final Tiles<DriveApp> getTiles() {
		return tiles;
	}

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void init() throws Exception {
		super.init();
		monitor = new Monitor(getContainer().getScheduler());
		config = new Configuration(monitor);
		mountConfig = new MountConfiguration(Configuration.APPNAME, "mounts", monitor, Optional.empty(), Optional.empty());
		mdns = new MDNS(InetAddress.getLocalHost());
		mountMgr = new MountManager.Builder(mountConfig).
				withMDNS(mdns).
				withExecutor(getContainer().getScheduler()).
				build();
		localFileSystemManager = new LocalFileSystemManager(
				getContainer().getScheduler(), 
				mountMgr, 
				config, 
				Optional.empty());
	}
	
	public Configuration configuration() {
		return config;
	}
	
	public MountConfiguration mountConfiguration() {
		return mountConfig;
	}
	
	public LocalFileSystemManager localFileSystemManager() {
		return localFileSystemManager;
	}

	public MountManager mountManager() {
		return mountMgr;
	}

	@Override
	public void addCommonStylesheets(ObservableList<String> stylesheets) {
		var appResource = getClass().getResource("FUSEMOUNT.css");
		if (appResource != null) {
			FXUtil.addIfNotAdded(stylesheets, appResource.toExternalForm());
		}
		super.addCommonStylesheets(stylesheets);
	}
	
	@Override
	protected DriveAppWindow createAppWindow(Stage stage) {
		var aw = new DriveAppWindow(stage, this/* , appContext */);
		var ctx = createContent(stage, aw);
		aw.setContent(ctx);
		return aw;
	}

	@Override
	protected Node createContent(Stage stage, DriveAppWindow window) {
		tiles = new Tiles<>(this, window);
		tiles.add(MountsPage.class);
		tiles.getStyleClass().add("padded");
		return tiles;
	}
}