import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 FastLoad Script Generator - creates Teradata FastLoad scripts from tab delimited text files.
 This class is one way of writing console output to the GUI.
 Copyright (C) Dallas Hall, 2017.  Taken from https://stackoverflow.com/a/33386692

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

class StreamGobbler implements Runnable
{
	private static InputStream inputStream;
	private static Consumer<String> consumeInputLine;

	public StreamGobbler(InputStream inputStream, Consumer<String> consumeInputLine)
	{
		StreamGobbler.inputStream = inputStream;
		StreamGobbler.consumeInputLine = consumeInputLine;
	}

	public void run()
	{
		new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumeInputLine);
	}
}
