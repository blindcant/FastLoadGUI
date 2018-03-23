import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.IOException;
import java.io.OutputStream;

/**
 FastLoad Script Generator - creates Teradata FastLoad scripts from tab delimited text files.
 This class is one way of writing console output to the GUI.
 Copyright (C) Dallas Hall, 2017.  Taken from https://stackoverflow.com/a/33502923

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
 */

public class ConsoleOutput extends OutputStream
{
	private static TextArea console;

	public ConsoleOutput(TextArea console)
	{
		ConsoleOutput.console = console;
	}

	public void appendText(String valueOf)
	{
		Platform.runLater(() -> ConsoleOutput.console.appendText(valueOf));
	}

	public void write(int b) throws IOException
	{
		appendText(String.valueOf((char) b));
	}
}
