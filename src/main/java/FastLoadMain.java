import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 FastLoad Script Generator - creates Teradata FastLoad scripts from tab delimited text files.
 This class loads the GUI.
 Copyright (C) Dallas Hall, 2017.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 * Fastload manual
 * http://www.info.teradata.com/browse.cfm > Teradata Utilities > Teradata Tools & Utilities Suite (version 14.10) > Fastload
 * Page 39 = fastload.exe parameters
 * Page 55 = sesssion charsets
 * Page 87 = script commands in full detail
 *
 * Terdata Parallel Transports details
 * https://developer.teradata.com/sites/all/files/documentation/linked_docs/2436020A_TPT-Reference-13.10.pdf
 * Page 73 = Unicode details
 *
 * Teradata tools session charsets details
 * http://www.info.teradata.com/htmlpubs/DB_TTU_14_00/index.html#page/Interface_Tools/B035_2425_071A/2425ch05.14.13.html
 *
 * Teradata tools BOM details
 * https://downloads.teradata.com/tools/articles/whats-a-bom-and-why-do-i-care
 * http://downloads.teradata.com/tools/articles/how-do-standalone-utilities-handle-byte-order-mark
 *
 * BOM in general
 * https://www.cs.umd.edu/class/sum2003/cmsc311/Notes/Data/endian.html

 */

public class FastLoadMain extends Application
{

	 public static void main(String[] args)
	 {
		  launch(args);
	 }

	 @Override
	 public void start(Stage primaryStage) throws Exception
	 {
	 	//https://stackoverflow.com/a/19603055
		FXMLLoader loader = new FXMLLoader();
		loader.setLocation(getClass().getResource("/FastLoad.fxml"));
	 	Parent content = loader.load();
		  Scene scene = new Scene(content, 1280, 800);

		  primaryStage.setTitle(FastLoadController.PROGRAM_NAME + " v" + FastLoadController.PROGRAM_VERSION);
		  //make the window non-resizeable https://stackoverflow.com/a/34809832
		  primaryStage.setResizable(false);
		  primaryStage.initStyle(StageStyle.DECORATED);
		  primaryStage.setScene(scene);
		  primaryStage.show();

		  // linked to quitProgramButton in the fat Controller
		  primaryStage.setOnCloseRequest(e -> Platform.exit());
	 }
}
