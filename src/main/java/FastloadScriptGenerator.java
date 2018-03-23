
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 FastLoad Script Generator - creates Teradata FastLoad scripts from tab delimited text files.
 This class generates the loading and unpause scripts.
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
 */

public class FastloadScriptGenerator
{
	// @@@ INSTANCE VARIABLES @@@
	// ### time measurements ###
	private Instant startTime = null;
	//private Instant endTime = null;

	// ### charsets ###
	// *** files ***
	private static final Charset ASCII = Charset.forName("US-ASCII");
	private static final Charset LATIN = Charset.forName("ISO-8859-1");
	private static final Charset UTF8 = Charset.forName("UTF-8");
	//defaults to big endian without a byte order marking
	private static final Charset UTF16 = Charset.forName("UTF-16");
	private static final Charset UTF16_BE = Charset.forName("UTF-16BE");
	private static final Charset UTF16_LE = Charset.forName("UTF-16LE");
	// *** Teradata client-to-server
	private String clientToServerEncoding;
	private static final String clientToServer_ASCII = "'ASCII'";
	private static final String clientToServer_UTF8 = "'UTF8'";
	private static final String clientToServer_UTF16 = "'UTF16'";

	// ### data processing variables ###
	private String[][] columnMetaData = null;
	private String[] header = null;
	private int[] columnSizes = null;
	private String[] columnDataTypes = null;
	private int lineCount = 0;
	private byte columnCount = 0;
	//private final static String END_OF_LINE_DELIMITER = "\r|\n|\r\n|$";
	private String sourceFileDelimiter;
	private Charset sourceFileEncoding;
	//private Charset parsedFileEncoding;

	// ### file I/O ###
	//private BufferedReader stdinFile;
	private BufferedWriter stdoutFile;
	private Path sourceDirectory;
	private Path targetDirectory;
	//private Path sourceFile;
	// *** directory + file ***
	private Path sourceAbsolutePath;
	//private Path targetAbsolutePath;

	// ### SQL parameters ###
	//exponents used for column lengths, to allow some leway for additional data to be loaded later
	//private int[] exponents = { 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384 };
	private int rowID = 1;
	private String logonFile = "";
	private String databaseName = "";
	private String tableName = "";
	private static final String ERROR_TABLE1 = "_E1";
	private static final String ERROR_TABLE2 = "_E2";
	private static final byte SESSION_LIMIT = 36;
	private short errorLimit = 0;
	private String sourceFileNameNoExtension = "";
	private String outputFileName = "";
	private String fastloadFileName = "";
	private String unpauseFastloadFileName = "";
	//this is just the file delimiter
	//private String recordVarText = "";
	private String charSet = "";

	// ### fastload scripts ###
	private String fastloadScript = "";
	private String unpauseScript = "";

	// ### redirect ###
	//private PrintStream innerPS;

	// ### error catching logic ###
	private boolean noError = true;

	// @@@ CONSTRUCTOR(s) @@@
	public FastloadScriptGenerator(
			File sourceFilePath
			,String sourceFileEncoding
			,boolean sourceHasHeader
			,String sourceFileDelimiter
			,String targetServer
			,String targetDatabaseName
			,String targetTableName
			,String targetColumnCharset
			,String userLogonFile
			,String clientToServerEncoding
			,boolean runUnpause
			,PrintStream outterPS)
	{
		//assign instance variables
		//standard output/error redirection
		PrintStream innerPS = outterPS;
		System.setOut(innerPS);
		System.setErr(innerPS);

		//get the directory tree only (remove filename)
		String absolutePath = sourceFilePath.getAbsolutePath();
		String filename = sourceFilePath.toPath().getFileName().toString();
		String directory = absolutePath.replaceAll(Pattern.quote(filename), "");

		//source file information - convert from File to Path
		sourceAbsolutePath = sourceFilePath.toPath();
		Path sourceFile = sourceFilePath.toPath().getFileName();
		sourceDirectory = Paths.get(directory);
		this.sourceFileDelimiter = sourceFileDelimiter;
		switch(sourceFileEncoding)
		{
			case "ASCII" : this.sourceFileEncoding = ASCII;
				break;

			case "Latin" : this.sourceFileEncoding = LATIN;
				break;

			case "UTF-8" : this.sourceFileEncoding = UTF8;
				break;

			case "UTF-16 BE" : this.sourceFileEncoding = UTF16_BE;
				break;

			case "UTF-16 LE" : this.sourceFileEncoding = UTF16_LE;
				break;
		}

/*		if (sourceFileEncoding.equals("ASCII"))
		{
			this.sourceFileEncoding = ASCII;
		}
		else if (sourceFileEncoding.equals("Latin"))
		{
			this.sourceFileEncoding = LATIN;
		}
		else if (sourceFileEncoding.equals("UTF-8"))
		{
			this.sourceFileEncoding = UTF8;
		}
		else if (sourceFileEncoding.equals("UTF-16 BE"))
		{
			this.sourceFileEncoding = UTF16_BE;
		}
		else if (sourceFileEncoding.equals("UTF-16 LE"))
		{
			this.sourceFileEncoding = UTF16_LE;
		}*/

		//client-to-server information
		switch (clientToServerEncoding)
		{
			case "ASCII" : this.clientToServerEncoding = clientToServer_ASCII;
					break;

			case "UTF-8" : this.clientToServerEncoding = clientToServer_UTF8;
				break;

			case "UTF-16" : this.clientToServerEncoding = clientToServer_UTF16;
				break;
		}
/*		if(clientToServerEncoding.equals("ASCII"))
		{
			this.clientToServerEncoding = clientToServer_ASCII;
		}
		else if(clientToServerEncoding.equals("UTF-8"))
		{
			this.clientToServerEncoding = clientToServer_UTF8;
		}
		else if(clientToServerEncoding.equals("UTF-16"))
		{
			this.clientToServerEncoding = clientToServer_UTF16;
		}*/

		//login details
		logonFile = userLogonFile;

		runTime(sourceHasHeader
				,this.sourceFileEncoding
				,sourceFileDelimiter
				,targetServer
				,targetDatabaseName
				,targetTableName
				,targetColumnCharset
				,clientToServerEncoding
				,runUnpause);
	}

	// @@@ METHODS @@@
	private void runTime(boolean sourceHasHeader
			,Charset sourceFileEncoding
			,String sourceFileDelimiter
			,String targetServer
			,String targetDatabaseName
			,String targetTableName
			,String targetColumnCharset
			,String clientToServerEncoding
			,boolean runUnpause)
	{
		try
		{
			//input parsing
			startTime = Instant.now();
			readSourceFile(sourceAbsolutePath, sourceFileEncoding, sourceFileDelimiter, sourceHasHeader, targetDatabaseName, targetTableName, targetColumnCharset);
			checkColumnSizesForZero();
			printStats(sourceHasHeader);
			printRuntime("FastLoad source file parsing.");

			//generate scripts
			startTime = Instant.now();
			setColumnMetaData(getHeader(), getColumnDataTypes(), getColumnSizes());
			setFastloadScript();
			setUnpauseScript();
			writeFastloadFile(true);
			writeFastloadFile(false);
			System.out.println();
			printRuntime("FastLoad script generation");

			//run the scripts
			startTime = Instant.now();
			if (runUnpause)
			{
				System.out.println("*********************");
				System.out.println("*     UNPAUSING     *");
				System.out.println("*********************\n");
				System.out.println("Running the FastLoad unpause script.\n");
				//new Thread(() -> runFastload(true)).start();
				runFastload(true);
			} else
			{
				System.out.println("*********************");
				System.out.println("*      LOADING      *");
				System.out.println("*********************\n");
				System.out.println("Running the FastLoad load script.\n");
				//new Thread(() -> runFastload(false)).start();
				runFastload(false);
			}
		}
		catch (Exception e)
		{
			System.out.println("<ERROR>\nAn error has occured.\nSee stack trace for details.\n</ERROR>");
			e.printStackTrace();
		}
		finally
		{

		}
	}

	/**
	 * This method reads at file into a memory buffer and then processes it as a table. The processing involves determining
	 * column header information, column data type information, and column size information. This information is stored
	 * into various Arrays inside of various ArrayLists.
	 *
	 * @param sourceFile  - The absolute filepath.
	 * @param sourceFileEncoding The encoding of the source file.
	 * @param sourceFileDelimiter - The delimiter used in the file.
	 * @param hasHeader      - This flag is used to say whether the file has its own header information or not.
	 * @param targetdDatabaseName This will set the database schema name.
	 * @param targetTableName This will set the database table name.
	 * @param targetColumnCharset   - The character set used when creating the table in Teradata.
	 */
	private void readSourceFile(Path sourceFile, Charset sourceFileEncoding, String sourceFileDelimiter, boolean hasHeader, String targetdDatabaseName, String targetTableName, String targetColumnCharset)
	{
		charSet = targetColumnCharset;
		try
		{
			//get the filename and remove the extention
			sourceFileNameNoExtension = sourceFile.getFileName().toString().replaceAll("\\..*$", "");

			//read in the file using a buffer and the specified character set - default is UTF-8
			BufferedReader stdinFile = Files.newBufferedReader(sourceAbsolutePath, sourceFileEncoding);

			//recordVarText = inputFileDelimiter;

			// update database details
			if (targetdDatabaseName == null || targetdDatabaseName.equals(""))
			{
				databaseName = "FIS_PRD_DB";
			} else
			{
				databaseName = targetdDatabaseName;
			}
			if (targetTableName == null || targetTableName.equals(""))
			{
				tableName = sourceFileNameNoExtension;
			} else
			{
				tableName = targetTableName;
			}

			// count the number of columns based on the delimiter, convert to a byte sized number, adding 1 for the new column ROW_ID
			String currentLine = stdinFile.readLine();

			//if it was a UTF-16 BE encoded file, then we need to remove the BOM (0xFE & 0xFF) from the start of it.
			if (sourceFileEncoding.equals(UTF16_BE))
			{
				char firstChar = currentLine.charAt(0);
				if (firstChar == '\uFEFF')
				{
					currentLine = currentLine.substring(1);
				}
			}

			if (hasHeader)
			{
				String tmp[];
				if(currentLine.matches("^\".*\"$"))
				{
					tmp = currentLine.split("(?<=\")" + Pattern.quote(sourceFileDelimiter) + "(?=\")", -1);
					columnCount = (byte) (tmp.length + 1);

				}
				else
				{
					tmp = currentLine.split(Pattern.quote(sourceFileDelimiter), -1);
					columnCount = (byte) (tmp.length + 1);
					//columnCount = (byte) (new StringTokenizer(currentLine, inputFileDelimiter).countTokens() + 1);
				}
			}
			//noheader
			else
			{
				String tmp[];
				if(currentLine.matches("^\".*\"$"))
				{
					tmp = currentLine.split("(?<=\")" + Pattern.quote(sourceFileDelimiter) + "(?=\")", -1);
					columnCount = (byte) (tmp.length + 1);

				}
				else
				{
					tmp = currentLine.split(Pattern.quote(sourceFileDelimiter), -1);
					columnCount = (byte) (tmp.length + 1);
				}
			}

			//create an array for holding the columns counts using the number of columns
			columnSizes = new int[columnCount];

			//create the array for holding datatypes
			columnDataTypes = new String[columnCount];

			//setup the output directory details - create an output directory based on the source file
			//resolve joins two partial paths together and adds a path separator - https://docs.oracle.com/javase/tutorial/essential/io/pathOps.html#resolve
			//targetDirectory = Paths.get(sourceDirectory + "\\" + sourceFileNameNoExtension + "-upload_files\\");
			targetDirectory = sourceDirectory.resolve(sourceFileNameNoExtension + "(upload_files)\\");

			//check if it exists, create it if it doesn't or delete all the files inside it if it does
			if (Files.notExists(targetDirectory))
			{
				Files.createDirectory(targetDirectory);
			}
			//delete all files and the direcotry if it exists
			else
			{
				//delete all the files first - https://docs.oracle.com/javase/tutorial/essential/io/walk.html
				DeleteAllFilesFromDirectory runtimeDeleteAllFilesFromDirectory = new DeleteAllFilesFromDirectory();
				Files.walkFileTree(targetDirectory, runtimeDeleteAllFilesFromDirectory);

				//delete the directory - https://docs.oracle.com/javase/tutorial/essential/io/delete.html
				//Files.deleteIfExists(targetDirectory);
			}
			//setup the output file details
			outputFileName = sourceFileNameNoExtension.concat("(parsed for upload).txt");

			//create the absolute path
			//targetAbsolutePath = Paths.get(targetDirectory + "\\" + outputFileName);
			Path targetAbsolutePath = targetDirectory.resolve(outputFileName);

			// CREATE – Opens the file if it exists or creates a new file if it does not.
			//check if UTF16_BE, if it is we need to write a BOM out which only UTF16 does (by default it is BE)
			//but because I am now a broken man in regards to supporting big endians, I am now just converting big endian to little endian.
			Charset parsedFileEncoding;
			if(sourceFileEncoding.equals(UTF16_BE))
			{
				parsedFileEncoding = UTF16_LE;
				stdoutFile = Files.newBufferedWriter(targetAbsolutePath, parsedFileEncoding, CREATE);
			}
			else
			{
				parsedFileEncoding = sourceFileEncoding;
				stdoutFile = Files.newBufferedWriter(targetAbsolutePath, parsedFileEncoding, CREATE);

			}

			// copy input file contents to and output file which is used for loading
			//preprend row id column
			String currentLineAndRowID = null;

			//store column header or generate it
			if (hasHeader)
			{
				if(currentLine.matches("^\".*\"$"))
				{
					currentLineAndRowID = "\"ROW ID\"" + sourceFileDelimiter + currentLine;
				}
				else
				{
					currentLineAndRowID = "ROW ID" + sourceFileDelimiter + currentLine;
				}

				//header.add(currentLineAndRowID.split(inputDelimiter + END_OF_LINE_DELIMITER));
				//Automatically excape the sourceFileDelimiter as a regex literal -  https://stackoverflow.com/a/10796174
				if(currentLineAndRowID.matches("^\".*\"$"))
				{
					header = currentLineAndRowID.split("(?<=\")" + Pattern.quote(sourceFileDelimiter) + "(?=\")", -1);
				}
				else
				{
					header = currentLineAndRowID.split(Pattern.quote(sourceFileDelimiter), -1);
				}

				//update the header to prepend / append " if needed, but always replace space with _
				for (int i = 0; i < header.length; i++)
				{
					StringBuilder tmpSB;

					String tmpS = header[i].replaceAll("\\s", "_");
					//tmpS = tmpS.replace(" ", "<>");
					if (tmpS.matches("^\".*\"$"))
					{
						tmpSB = new StringBuilder(tmpS.toUpperCase());
					}
					else
					{
						tmpSB = new StringBuilder("\"" + tmpS.toUpperCase() + "\"");
					}
					tmpS = new String(tmpSB);
					header[i] = tmpS;
				}
			}
			//no header
			else
			{
				String[] tmp = new String[columnCount];
				for (int i = 0; i < columnCount; i++)
				{
					if (i == 0)
					{
						tmp[0] = "\"ROW_ID\"";
					} else
					{
						tmp[i] = "\"COLUMN_" + i + "\"";
					}
				}
				header = tmp;

				//write to output file
/*				stdoutFile.write(currentLineAndRowID);
				stdoutFile.newLine();*/

				//reset the array
				tmp = null;

				//add first line to the temporary array, but check if quoted or not before processing
				//split it up into an array - http://docs.oracle.com/javase/8/docs/api/java/lang/String.html#split-java.lang.String-int-
				if (currentLine.matches("^\".*\"$"))
				{
					//prepend the row id
					currentLineAndRowID = "\"" + rowID + "\"" + sourceFileDelimiter + currentLine;
					tmp = currentLineAndRowID.split("(?<=\")" + Pattern.quote(sourceFileDelimiter) + "(?=\")", -1);
				}
				else
				{
					//prepend the row id
					currentLineAndRowID = rowID + sourceFileDelimiter + currentLine;
					tmp = currentLineAndRowID.split(Pattern.quote(sourceFileDelimiter), -1);
				}

				//write to output file with columns encased in ""
				for (int i = 0; i < tmp.length; i++)
				{
					//http://www.regular-expressions.info/java.html
					String currentCell = tmp[i]; //.replaceAll("\"", "'\"");
					if (i < tmp.length - 1)
					{
						if (currentCell.matches("^\".*\"$"))
						{
							stdoutFile.write(currentCell + sourceFileDelimiter);
						}
						else
						{
							stdoutFile.write("\"" + currentCell + "\"" + sourceFileDelimiter);
							//stdoutFile.write(currentCell + sourceFileDelimiter);
						}
					}
					else
					{
						if (currentCell.matches("^\".*\"$"))
						{
							stdoutFile.write(currentCell);
						}
						else
						{
							stdoutFile.write("\"" + currentCell + "\"");
							//stdoutFile.write(currentCell);
						}
					}
				}
				stdoutFile.newLine();

				//create the column sizes, these need to be different between LATIN and UNICODE
				// http://community.teradata.com/t5/Tools/CHAR-n-CHAR-SET-UNICODE-LATIN-define-schema-in-14-xx-multiply-by/td-p/25886
				//https://stackoverflow.com/a/15128103
				setColumnSizes(tmp, this.sourceFileEncoding, this.clientToServerEncoding);

				//add to ArrayList
				//body.add(tmp);
				lineCount++;
				rowID++;
			}
			//clear current lines
			currentLine = null;
			currentLineAndRowID = null;

			//get the rest of the data, store it in a temporary array for further processing, and then write it to a new file for fastloading
			while ((currentLine = stdinFile.readLine()) != null)
			{
				//split it up into an array - http://docs.oracle.com/javase/8/docs/api/java/lang/String.html#split-java.lang.String-int-
				String[] tmp = null;
				if (currentLine.matches("^\".*\"$"))
				{
					//prepend the row id
					currentLineAndRowID = "\"" + rowID + "\"" + sourceFileDelimiter + currentLine;
					tmp = currentLineAndRowID.split("(?<=\")" + Pattern.quote(sourceFileDelimiter) + "(?=\")", -1);
				}
				else
				{
					//prepend the row id
					currentLineAndRowID = rowID + sourceFileDelimiter + currentLine;
					tmp = currentLineAndRowID.split(Pattern.quote(sourceFileDelimiter), -1);
				}
				//body.add(tmp);

				//write to output file with columns encased in ""
				for (int i = 0; i < tmp.length; i++)
				{
					//http://www.regular-expressions.info/java.html
					String currentCell = tmp[i]; //.replaceAll("\"", "'\"");
					if (i < tmp.length - 1)
					{
						if (currentCell.matches("^\".*\"$"))
						{
							stdoutFile.write(currentCell + sourceFileDelimiter);
						}
						else
						{
							stdoutFile.write("\"" + currentCell + "\"" + sourceFileDelimiter);
							//stdoutFile.write(currentCell + sourceFileDelimiter);
						}
					}
					else
					{
						if (currentCell.matches("^\".*\"$"))
						{
							stdoutFile.write(currentCell);
						}
						else
						{
							stdoutFile.write("\"" + currentCell + "\"");
							//stdoutFile.write(currentCell);
						}
					}
				}
				stdoutFile.newLine();

				//create the column sizes, these need to be different between LATIN and UNICODE
				setColumnSizes(tmp, this.sourceFileEncoding, this.clientToServerEncoding);

				//compare column data types
				setColumnDataTypes(tmp);

				//clear current line
				currentLine = null;
				currentLineAndRowID = null;

				//update line count
				lineCount++;
				rowID++;
			}
			//lineCount += body.size();

			//flush and close teh buffer
			stdoutFile.close();

			//testing variables
/*			System.out.println("sourceFileNameNoExtension: " + sourceFileNameNoExtension);
			System.out.println("recordVarText: '" + recordVarText + "'");
			System.out.println("abosoluteOutputPath: " + abosoluteOutputPathString);
			System.out.println("outputFileName: " + outputFileName);
			System.out.println("tableName: " + tableName);
			System.out.println("currentLine: " + currentLine);
			System.out.println("columnCount: " + columnCount);
			System.out.println("columnSizes: " + Arrays.toString(columnSizes));
			System.out.println("columnDataTypes: " + Arrays.toString(columnDataTypes));*/
		}
		catch (IOException e)
		{
			System.out.println("Current line is: " + lineCount);
			e.printStackTrace();
		}
		catch (Exception e)
		{
			System.out.println("Current line is: " + lineCount);
			e.printStackTrace();
		}
		finally
		{
			return;
		}
	}


	/**
	 * This method opens a Windows command prompt and runs fastload with a generated script.
	 * <p>
	 * https://stackoverflow.com/questions/4688123/how-to-open-the-command-prompt-and-insert-commands-using-java
	 * https://ss64.com/nt/cmd.html
	 * <p>
	 * ProcessBuilder v Runtime
	 * http://www.javaworld.com/article/2071275/core-java/when-runtime-exec---won-t.html
	 * https://stackoverflow.com/questions/7134486/how-to-execute-command-with-parameters/7134525#7134525
	 * https://stackoverflow.com/a/13890515
	 * https://stackoverflow.com/a/11336257
	 *
	 * @param runUnpause Determines if the load or unpause script is run.
	 */
	private void runFastload(boolean runUnpause)
	{
		Process process;
		ProcessBuilder processBuilder;
		StreamGobbler outputGobbler;
		try
		{
			if (runUnpause)
			{
				// The start is needed to open the CMD prompt window and /K is needed to stop it from closing after runtime
				// https://www.computerhope.com/starthlp.htm
				if(clientToServerEncoding.equals("UTF-16"))
				{
					processBuilder = new ProcessBuilder().inheritIO().command("CMD.exe", "/C", "fastload");
				}
				else
				{
					processBuilder = new ProcessBuilder().inheritIO().command("CMD.exe", "/C", "fastload");
				}

				// join both standard output and error together
				processBuilder.redirectErrorStream(true);

				// redirect both standard output and error to the console
				//processBuilder.redirectInput(new File(targetDirectory + getUnpauseFastloadFileName()));
				//processBuilder = new ProcessBuilder().inheritIO().command("CMD.exe", "/C", "fastload.exe", "-i " + String_sourceFileEncoding);
				processBuilder = new ProcessBuilder().inheritIO().command("CMD.exe", "/C", "fastload.exe");

				//processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
				processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);

				//create a new process
				process = processBuilder.start();

				//print the output of the process - USE PROCESS GOBBLER
				outputGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
				//outputGobbler.run();
				new Thread(outputGobbler).start();

				//tell the process to wait for things to finish before closing
				process.waitFor();
			} else
			{
				//craete new processBuilder & setup its state
				//https://downloads.teradata.com/tools/articles/whats-a-bom-and-why-do-i-care
				//http://downloads.teradata.com/tools/articles/how-do-standalone-utilities-handle-byte-order-mark
				//processBuilder = new ProcessBuilder().inheritIO().command("CMD.exe", "/C", "fastload.exe", "-i " + String_sourceFileEncoding);
				processBuilder = new ProcessBuilder().inheritIO().command("CMD.exe", "/C", "fastload.exe");

				// join both standard output and error together
				processBuilder.redirectErrorStream(true);

				// redirect both standard output and error to the console
				processBuilder.redirectInput(new File(targetDirectory.resolve(getFastloadFileame()).toString()));
				//processBuilder.redirectInput(new File(targetDirectory.resolve(getUnpauseFastloadFileName()).toString() + " -c " + this.clientToServerEncoding + " -i " + this.sourceFileEncoding));

				//processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
				processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);

				//create a new process
				process = processBuilder.start();

				//print the output of the process - USE BUFFERED READER
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String currentLine = null;
				while ((currentLine = bufferedReader.readLine()) != null)
				{
					System.out.println(currentLine);
				}
				//close resources
				bufferedReader.close();

				//tell the process to wait for things to finish before closing
				process.waitFor();

				//print stats at the end
				System.out.println();
				printRuntime("FastLoading data");

				//print a select * so users can check the table they laoded
				printSelectStatement();
			}
		}
		catch (Exception e)
		{
			System.out.println("<ERROR>\nCannot run file.\n</ERROR>");
			e.printStackTrace();
		}
		finally
		{

		}
	}

	/**
	 * This method will write the generated fastload script to a file. The file name will be the same as the input file
	 * and the extension will be .fastload
	 */
	private void writeFastloadFile(boolean isPauseScript)
	{
		//update filename variables
		fastloadFileName = sourceFileNameNoExtension.concat("(upload).fastload");
		unpauseFastloadFileName = sourceFileNameNoExtension.concat("(unpause).fastload");

		try
		{
			if (isPauseScript)
			{
				stdoutFile = Files.newBufferedWriter(targetDirectory.resolve(unpauseFastloadFileName), UTF8, CREATE);
				stdoutFile.write(getUnpauseScript());
				stdoutFile.close();
			} else
			{
				stdoutFile = Files.newBufferedWriter(targetDirectory.resolve(fastloadFileName), UTF8, CREATE);
				stdoutFile.write(getFastloadScript());
				stdoutFile.close();
			}
		} catch (Exception e)
		{
			System.out.println("<ERROR>\nCannot write to file.\n</ERROR>");
			e.printStackTrace();
			//tell the GUI to stop displaying the progress indicator

		}
	}

	//GETTERS

	/**
	 * This method gets the column name details which were calculated during readFile().
	 *
	 * @return - a String array.
	 */
	private String[] getHeader()
	{
		return header;
	}

	/**
	 * This method gets the column data types which were calculated during readFile().
	 *
	 * @return - A String array of column data types.
	 */
	private String[] getColumnDataTypes()
	{
		return columnDataTypes;
	}

	/**
	 * This method gets the column sizes which were calculated during readFile().
	 *
	 * @return - A integer array of column sizes.
	 */
	private int[] getColumnSizes()
	{
		return columnSizes;
	}

	/**
	 * This method gets the column metadata, which is the combination of column name, column data types, and column sizes.
	 *
	 * @return - A 2D String array, the first array holds the data for each column. The second array holds the metadata.
	 */
	private String[][] getColumnMetaData()
	{
		return columnMetaData;
	}

	/**
	 * This method returns the generated fastload script.
	 *
	 * @return String containing the generated fastload script.
	 */
	private String getFastloadScript()
	{
		return fastloadScript;
	}

	private String getFastloadFileame()
	{
		return fastloadFileName;
	}

	private String getUnpauseFastloadFileName()
	{
		return unpauseFastloadFileName;
	}

	/**
	 * This method returns the generated fastload unpause script.
	 *
	 * @return String containing the generated fastload unpause script.
	 */
	private String getUnpauseScript()
	{
		return unpauseScript;
	}

	private String getLogonFile()
	{
		return logonFile;
	}

	//SETTERS
	//NOTE: Column names are set during readFile().

	/**
	 * This method calculates the column's data types.
	 *
	 * @param tmpArray - Accepts a String array which is used to calculate the column data types.
	 */
	private void setColumnDataTypes(String[] tmpArray)
	{
		for (int i = 0; i < columnSizes.length; i++)
		{
			//always set the ROW_ID to integer so we don't restrict ourselves incase we want to add more rows later
			if (i == 0 && columnDataTypes[0] == null)
			{
				columnDataTypes[0] = "INTEGER";
				continue;
			}
			//if it is already set, skip the rest of this iteration and keep going on the next
			else if (i == 0 && columnDataTypes[0] != null)
			{
				continue;
			}
			else
			{
				//remove the "" from the data if it exists
				String currentSourceDataCell = tmpArray[i].replaceAll("\"", "");
				String currentColumnDataType = columnDataTypes[i];

				//skip if the cell is empty
				if (currentColumnDataType != null && (currentSourceDataCell.equals("")))
				{
					continue;
				}

				//byte = 8bit signed number (Teradata = byteint)
				try
				{
					Byte.parseByte(currentSourceDataCell);
					//skip if a higher number exists or a character value
					if (currentColumnDataType != null && (currentColumnDataType.contains("SMALLINT") || currentColumnDataType.contains("INTEGER") || currentColumnDataType.contains("BIGINT") || currentColumnDataType.contains("FLOAT") || currentColumnDataType.contains("DOUBLE") || currentColumnDataType.contains("VARCHAR")))
					{
						continue;
					}
					//set the number
					else
					{
						columnDataTypes[i] = "BYTEINT";
						continue;
					}

				} catch (NumberFormatException e)
				{

				}

				//short = 16bit signed number (Teradata = smallint)
				try
				{
					Short.parseShort(currentSourceDataCell);
					//skip if a higher number exists  or a character value
					if (currentColumnDataType != null && (currentColumnDataType.contains("INTEGER") || currentColumnDataType.contains("BIGINT") || currentColumnDataType.contains("FLOAT") || currentColumnDataType.contains("DOUBLE") || currentColumnDataType.contains("VARCHAR")))
					{
						continue;
					}
					//set the number
					else
					{
						columnDataTypes[i] = "SMALLINT";
						continue;
					}

				} catch (NumberFormatException e)
				{

				}

				try
				{
					Integer.parseInt(currentSourceDataCell);
					//skip if a higher number exists or a character value
					if (currentColumnDataType != null && (currentColumnDataType.contains("BIGINT") || currentColumnDataType.contains("FLOAT") || currentColumnDataType.contains("DOUBLE") || currentColumnDataType.contains("VARCHAR")))
					{
						continue;
					}
					//set the number
					else
					{
						columnDataTypes[i] = "INTEGER";
						continue;
					}

				} catch (NumberFormatException e)
				{

				}

				//long = 64bit signed number (Teradata = bigint)
				try
				{
					Long.parseLong(currentSourceDataCell);
					//skip if a higher number exists or a character value
					if (currentColumnDataType != null && (currentColumnDataType.contains("FLOAT") || currentColumnDataType.contains("DOUBLE") || currentColumnDataType.contains("VARCHAR")))
					{
						continue;
					}
					//set the number
					else
					{
						columnDataTypes[i] = "BIGINT";
						continue;
					}

				} catch (NumberFormatException e)
				{

				}

				//float = 32bit floating number (Teradata = float)
				try
				{
					Float.parseFloat(currentSourceDataCell);
					//skip if a higher number exists or a character value
					if (currentColumnDataType != null && (currentColumnDataType.contains("DOUBLE") || currentColumnDataType.contains("VARCHAR")))
					{
						continue;
					}
					//set the number
					else
					{
						columnDataTypes[i] = "FLOAT";
						continue;
					}
				} catch (NumberFormatException e)
				{
				}

				//double = 64bit floating number (Teradata = double precision)
				try
				{
					Double.parseDouble(currentSourceDataCell);
					//skip if character value exists
					if (currentColumnDataType != null && currentColumnDataType.contains("VARCHAR"))
					{
						continue;
					}
					//set the number
					else
					{
						columnDataTypes[i] = "DOUBLE";
						continue;
					}
				} catch (NumberFormatException e)
				{
				}

				//BigInteger / BigDouble = arbitrary numbers with no precision (Teradata = number)

				//char 16bit unicode character (Teradata = char(1) or varchar(1))
/*				if (currentColumnDataType != null && currentSourceDataCell.length() == 1)
				{
					columnDataTypes[i] = "CHAR";
					continue;
				}*/
				//String 16bit unicode characters (Teradata = char(n) or varchar(n))
				if (currentSourceDataCell.length() >= 1)
				{
					columnDataTypes[i] = "VARCHAR";
					continue;
				}
				//if nothing else has been set then set a VARCHAR
				else if(currentColumnDataType == null)
				{
					columnDataTypes[i] = "VARCHAR";
					continue;
				}
			}
		}
	}

	/**
	 * This method calculates the column's size. It will detect if any characters are UTF8 or UTF16 and how many bytes
	 *
	 * @param tmpArray - Accepts an int array which is used to calculate the column sizes.
	 * @param sourceFileEncoding
	 * @param clientToServerEncoding
	 */
	private void setColumnSizes(String[] tmpArray, Charset sourceFileEncoding, String clientToServerEncoding)
	{
		for (int i = 0; i < columnSizes.length; i++)
		{
			//processing variables
			String sourceCurrentCell = tmpArray[i];
			String sourceFileEncodingName = sourceFileEncoding.name();
			int sourceCurrentCellCharacterLength = tmpArray[i].length();
			int sourceCurrentCellBytesLength = calculateStringTotalBytesLength(sourceCurrentCell, sourceFileEncoding);
			int targetColumnLength =  columnSizes[i];

			if (sourceCurrentCellBytesLength > targetColumnLength)
			{
				//increase the column length based on how many bytes there are
				//ASCII / LATIN - 1 character takes up 1 byte
				if (sourceFileEncodingName.equals("US-ASCII") || sourceFileEncodingName.equals("ISO-8859-1"))
				{
					//Teradata needs even numbers when in UTF16 mode
					if(clientToServerEncoding.equals("'UTF16'") && sourceCurrentCellBytesLength % 2 != 0)
					{
						columnSizes[i] = sourceCurrentCellBytesLength + 1;
						continue;
					}
					else
					{
						columnSizes[i] = sourceCurrentCellBytesLength;
						continue;
					}
				}
				//UTF-8 - 1 character takes up between 1-4 bytes
				else if (sourceFileEncodingName.equals("UTF-8"))
				{
					if(clientToServerEncoding.equals("'UTF16'") && sourceCurrentCellBytesLength % 2 != 0)
					{
						columnSizes[i] = sourceCurrentCellBytesLength + 1;
						continue;
					}
					else
					{
						columnSizes[i] = sourceCurrentCellBytesLength;
						continue;
					}
				}
				//UTF-16 - 1 character takes up 2 bytes
				else if (sourceFileEncodingName.matches("UTF-16.*"))
				{
					if(clientToServerEncoding.equals("'UTF16'") && sourceCurrentCellBytesLength % 2 != 0)
					{
						columnSizes[i] = sourceCurrentCellBytesLength + 1;
						continue;
					}
					else
					{
						columnSizes[i] = sourceCurrentCellBytesLength;
						continue;
					}
				}
			}
		}
	}

	/**
	 * This method is run after readSourceFile() to check for any values of 0, if a 0 is found it will be updated with 32.
	 * This effectively makes the column VARCHAR(32).
	 */
	private void checkColumnSizesForZero()
	{
		for (int i = 0; i < columnSizes.length; i++)
		{
			int currentColumnLength =  columnSizes[i];

			if(currentColumnLength == 0)
			{
				columnSizes[i] = 32;
			}
		}
	}

	/**
	 * This method sums all the bytes of each character inside a String, based on the inputted Charset.
	 *
	 * @param inputString The String to count.
	 * @param inputCharset The Charset of the String.
	 * @return The total numbers of bytes that the inputString has, based on its inputCharset.
	 */
	private int calculateStringTotalBytesLength(String inputString, Charset inputCharset)
	{
		//processing varaibles
		char[] inputCharArray = inputString.toCharArray();

		//return variables
		int bytesCount = 0;
		String largestByteChar = null;

		for (int i = 0; i < inputCharArray.length; i++)
		{
			String currentChar = inputCharArray[i] + "";
			byte[] bytes = currentChar.getBytes(inputCharset);
			bytesCount+= bytes.length;
		}
		//System.out.println("Starting string is: " + inputString);
		//System.out.println(largestByteChar + " in " + inputCharset.name() + " is "  + largestByteCount + " byte(s)\n");
		return bytesCount;
	}

	/**
	 * This method accepts 3 arrays which are used to create a new 2D array that stores the metadata together.
	 *
	 * @param columnNameArray     - This String array holds the column names.
	 * @param columnDataTypeArray - This String array holds the column data types.
	 * @param columnSizeArray     - This String array holds the column sizes.
	 */
	private void setColumnMetaData(String[] columnNameArray, String[] columnDataTypeArray, int[] columnSizeArray)
	{
		// 2d array, first array is as long as the number of columns, second array is 3 to store column name, data type, and size
		columnMetaData = new String[columnCount][3];

		for (int i = 0; i < columnCount; i++)
		{
			for (int j = 0; j < 3; j++)
			{
				if (j == 0)
				{
					columnMetaData[i][j] = columnNameArray[i];
				} else if (j == 1)
				{
					columnMetaData[i][j] = columnDataTypeArray[i];
				} else
				{
					columnMetaData[i][j] = (columnSizeArray[i] + "");
				}
			}
		}
	}

	/**
	 *
	 */
	private void setFastloadScript()
	{
		// load 10% at a time& allow 10% of errors
/*		if ((lineCount * .1) > 1)
		{
			errorLimit = (short) (lineCount * .1);
		}
		else
		{
			errorLimit = 1;
		}*/
		errorLimit = 10000;

		//generate the script
		//session can be 'ASCII', 'UTF8', or 'UNICODE' -- "\nSET SESSION CHARSET 'UTF8'";
		String fastloadScriptPart1 = ("SESSIONS " + SESSION_LIMIT + ";\n" + "ERRLIMIT " + errorLimit + ";\n" + "RECORD 1 THRU " + lineCount + ";\nSET SESSION CHARSET " + clientToServerEncoding + ";\n.SHOW VERSIONS;\n" + ".LOGMECH LDAP\n" + getLogonFile() + ";\nDROP TABLE " + databaseName + "." + tableName + ";\n" + "DROP TABLE " + databaseName + "." + tableName + ERROR_TABLE1 + ";\n" + "DROP TABLE " + databaseName + "." + tableName + ERROR_TABLE2 + ";\n\n" + "CREATE SET TABLE " + databaseName + "." + tableName + "\n" + "\t,NO FALLBACK\n\t,NO BEFORE JOURNAL\n\t,NO AFTER JOURNAL\n\t,CHECKSUM = DEFAULT\n\t,DEFAULT MERGEBLOCKRATIO\n(\n");

		//generate create section fastloadCreate
		String[][] tmpArray = getColumnMetaData();

		// update primary index
		//String primaryIndex = tmpArray[inputPrimaryIndex - 1][0];

		// get the columns
		StringBuilder fastloadCreate = new StringBuilder();
		StringBuilder fastloadDefine = new StringBuilder();
		StringBuilder fastloadInsert = new StringBuilder();

		for (int i = 0; i < tmpArray.length; i++)
		{
			for (int j = 0; j < tmpArray[i].length; j++)
			{
				// line 1, column 1
				if (i == 0 && j == 0)
				{
					fastloadCreate.append("\t " + tmpArray[i][j] + " ");
				}
				// line > 1, column 1
				else if (i > 0 && j == 0)
				{
					fastloadCreate.append("\t," + tmpArray[i][j] + " ");
				}
				// all lines, character data
				else if (j == 1 && tmpArray[i][1].contains("CHAR"))
				{
					fastloadCreate.append(tmpArray[i][j] + "(");
				}
				// all lines, numeric data
				else if (j == 1 && !tmpArray[i][1].contains("CHAR"))
				{
					fastloadCreate.append(tmpArray[i][j] + "\n");
				}
				// all lines, character data
				else if (j == 2 && tmpArray[i][1].contains("CHAR"))
				{
					fastloadCreate.append(tmpArray[i][j] + ") CHARACTER SET " + charSet + " NOT CASESPECIFIC" + "\n");
				}
			}
		}
		fastloadCreate.append(")\nPRIMARY INDEX (ROW_ID);\n\n");

		//create define - http://www.info.teradata.com/HTMLPubs/DB_TTU_14_10/index.html#page/Load_and_Unload_Utilities/B035_2411_082K/2411Ch03.014.122.html#ww1972916
		//DON'T use "NOSTOP" as it will cause teh application
		//fastloadDefine.append("SET RECORD VARTEXT \"" + sourceFileDelimiter + "\" TRIM BOTH '\"';\n\nDEFINE\n");
		fastloadDefine.append("SET RECORD\n\tVARTEXT \"" + this.sourceFileDelimiter + "\"\n\tDISPLAY_ERRORS '" + targetDirectory.toString()
				//+ "\\row_in_error.txt'\n\tTRIM BOTH '\"';\n\nDEFINE\n");
				+ "\\row_in_error.txt'\n\tTRIM BOTH '\"'\n\tQUOTE YES '\"';\n\nDEFINE\n");

		for (int i = 0; i < tmpArray.length; i++)
		{
			for (int j = 0; j < tmpArray[i].length; j++)
			{
				// line 1, column 1
				if (i == 0 && j == 0)
				{
					fastloadDefine.append("\t " + tmpArray[i][j] + " (");
				}
				// line > 1, column 1
				else if (i > 0 && j == 0)
				{
					fastloadDefine.append("\t," + tmpArray[i][j] + " (");
				}
				// all lines
				else if (j == 1)
				{
					fastloadDefine.append("VARCHAR(");
				}
				// all lines, character data
				else if (j == 2)
				{
					fastloadDefine.append(tmpArray[i][j] + "))\n");
				}
			}
		}

		//fastloadInsert.append("\t,FILE=" + targetDirectory.resolve(outputFileName) + ";\nSHOW;\n" + "\nBEGIN LOADING \n\t " + databaseName + "." + tableName + "\nERRORFILES \n\t " + databaseName + "." + tableName + ERROR_TABLE1 + "\n\t," + databaseName + "." + tableName + ERROR_TABLE2 + "\nCHECKPOINT 50000;\nINSERT INTO " + databaseName + "." + tableName + "\n(\n");
		fastloadInsert.append("\t,FILE=" + targetDirectory.resolve(outputFileName) + ";\nSHOW;\n" + "\nBEGIN LOADING \n\t " + databaseName + "." + tableName + "\nERRORFILES \n\t " + databaseName + "." + tableName + ERROR_TABLE1 + "\n\t," + databaseName + "." + tableName + ERROR_TABLE2 + ";\nINSERT INTO " + databaseName + "." + tableName + "\n(\n");
		for (int i = 0; i < tmpArray.length; i++)
		{
			for (int j = 0; j < tmpArray[i].length; j++)
			{
				// line 1, column 1
				if (i == 0 && j == 0)
				{
					fastloadInsert.append("\t :" + tmpArray[i][j] + "\n");
				}
				// line > 1, column 1
				else if (i > 0 && j == 0)
				{
					fastloadInsert.append("\t,:" + tmpArray[i][j] + "\n");
				} else
				{
					break;
				}
			}
		}
		fastloadInsert.append(");\nEND LOADING;\n\n.LOGOFF;\n.QUIT;");

		// store what we have so far
		fastloadScript = fastloadScriptPart1 + fastloadCreate + fastloadDefine + fastloadInsert;

		//null the objects
		fastloadScriptPart1 = null;
		fastloadCreate = null;
		fastloadDefine = null;
		fastloadInsert = null;
	}

	/**
	 * This method creates a fastload unpause script.
	 */
	private void setUnpauseScript()
	{
		unpauseScript = ("SESSIONS " + SESSION_LIMIT + ";\n" + "ERRLIMIT " + errorLimit + ";\n"
				+ "RECORD 1 THRU " + lineCount + ";\nSET SESSION CHARSET " + clientToServerEncoding + ";\n\n"
				+ ".LOGMECH LDAP\n" + getLogonFile() + ";\n.SHOW VERSIONS;\n\n" + "BEGIN LOADING \n\t" + databaseName + "." + tableName + "\nERRORFILES \n\t " + databaseName + "." + tableName + ERROR_TABLE1 + "\n\t," + databaseName + "." + tableName + ERROR_TABLE2 + ";\n\n" + "END LOADING;\n\n.LOGOFF;\n.QUIT;");
	}

	//HELPERS

	/**
	 * This method prints how long it took for a process to run.  The process is passed in as a parameter.
	 *
	 * @param process This String is used to describe the process in the output.
	 */
	private void printRuntime(String process)
	{
		Instant endTime = Instant.now();
		//String runtime = Duration.between(startTime, endTime) + "";
		String runtime = Duration.between(startTime, endTime).toString();
		System.out.println("*********************");
		System.out.println("*   RUNTIME STATS   *");
		System.out.println("*********************\n");
		System.out.println("The runtime for " + process + " was: " + runtime.substring(2) + "\n");
	}

	/**
	 * This method is used for testing purposes only. It prints some metadata information/
	 *
	 * @param hasHeader - This flag is used to determine if the file has its own column headers or not.
	 */
	private void printStats(boolean hasHeader)
	{
		System.out.println("*********************");
		System.out.println("*   FILE METADATA   *");
		System.out.println("*********************\n");

		System.out.println("The column count is: " + columnCount);
		//System.out.println(Arrays.toString(header));
		System.out.println();

		//print counts
		for (int i = 0; i < columnSizes.length; i++)
		{
			System.out.println(header[i].replaceAll("\"", "") + " column size is " + columnSizes[i] + ". The data type is: " + columnDataTypes[i] + "\n");
		}

		System.out.println("The line count was: " + lineCount + "\n");
	}

	/**
	 * This method prints out a simple SQL statement that can be copied and pasted to check the contents of the FastLoaded data.
	 */
	private void printSelectStatement()
	{
		System.out.println("*********************");
		System.out.println("*   SQL CHECK CODE  *");
		System.out.println("*********************\n");
		System.out.println("SELECT TOP 1000 *\nFROM " + databaseName + "." + tableName + "\n;\n" );
		System.out.println("SELECT TOP 1000 *\nFROM " + databaseName + "." + tableName + ERROR_TABLE1 + "\n;\n" );
		System.out.println("SELECT TOP 1000 *\nFROM " + databaseName + "." + tableName + ERROR_TABLE2 + "\n;\n" );
	}
}