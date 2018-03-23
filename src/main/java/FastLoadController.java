import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * FastLoad Script Generator - creates Teradata FastLoad scripts from tab delimited text files.
 * This class controls the GUI.
 * Copyright (C) Dallas Hall, 2017.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

public class FastLoadController
{
	//INSTANCE VARIABLES
	//Predefined at runtime
	//https://docs.oracle.com/javase/tutorial/essential/environment/sysprop.html
	private static final String OS_ARCH = System.getProperty("os.arch");
	private static final String OS_NAME = System.getProperty("os.name");
	private static final String OS_VERSION = System.getProperty("os.version");
	private static final String NEWLINE = System.getProperty("line.separator");
	private static final String USERNAME = System.getProperty("user.name");
	private static final String USER_HOME_DIR = System.getProperty("user.home");
	private static final String FILE_SEPARATOR = System.getProperty("file.separator");
	private static final String LOGON_FILE = "logon.fastload";
	private static final String LOGON_FILE_ABSOLUTE_PATH = USER_HOME_DIR + FILE_SEPARATOR + LOGON_FILE;
	public static final double PROGRAM_VERSION = 2.6;
	public static final String PROGRAM_NAME = "FastLoad Load & Unpause Script Generator";
	private final static String TAB = "\t";
	private final static String COMMA = ",";
	private final static String SPACE = " ";
	private final static String SEMI_COLON = ";";
	private final static String PIPE = "|";

	//Created during runtime (Note: password may be read in from a file)
	private BufferedReader stdinFile;
	private PrintWriter stdoutFile;
	private File sourceFilePath;
	private String sourceFileDelimiter;
	private String sourceFileEncoding;
	private String password;
	private String teradataConnectionFile = ".RUN FILE " + LOGON_FILE_ABSOLUTE_PATH;
	private String clientToServerEncoding;
	private final static String DEVTEST_SERVER_NAME = "tdattst.csda.gov.au/";
	private final static String PROD_SERVER_NAME = "tdatprd.csda.gov.au/";
	private String targetServer;
	private String targetSchemaName;
	private String targetTableName;
	private String targetTableCharset;
	private boolean parsingDone = false;

	//object specific details
	private FastloadScriptGenerator fastloadScriptGenerator;

	//Menu specific
	@FXML
	private MenuBar menuBar_top;
	@FXML
	private Menu menu_file;
	@FXML
	private MenuItem menuItem_run;
	@FXML
	private MenuItem menuItem_open;
	@FXML
	private MenuItem menuItem_quit;
	@FXML
	private Menu menu_help;
	@FXML
	private MenuItem menuItem_about;
	@FXML
	private MenuItem menuItem_licence;
	@FXML
	private MenuItem menuItem_help;

	//Input Pane specific
	@FXML
	private TextField textField_username;
	@FXML
	private TextField textField_password;
	@FXML
	private ComboBox comboBox_clientToServerCharset;
	@FXML
	private ComboBox comboBox_targetServer;
	@FXML
	private CheckBox checkBox_sourceFileHeader;
	@FXML
	private ComboBox comboBox_sourceFileEncoding;
	@FXML
	private ComboBox comboBox_sourceFileDelimiter;
	@FXML
	private CheckBox checkBox_targetSchemaName;
	@FXML
	private TextField textField_targetSchemaName;
	@FXML
	private CheckBox checkBox_targetTableName;
	@FXML
	private TextField textField_targetTableName;
	@FXML
	private ComboBox comboBox_targetColumnCharSet;
	@FXML
	private Button button_browse;
	@FXML
	//https://stackoverflow.com/questions/20205145/javafx-how-to-show-read-only-text
	private TextField textField_sourceFilePath;
	@FXML
	private CheckBox checkBox_runUnpause;
	@FXML
	private Button button_run;
	@FXML
	private Button button_quit;

	//Console redirection specific
	@FXML
	private TextArea textArea_consoleOutput;
	private PrintStream printStream;

	//progress indicator
	@FXML
	private ProgressIndicator progressIndicator;

	//METHODS

	/**
	 * This method configures Nodes within the Scene after they have been initialised.
	 * It cannot be done in a constructor because  that runs before the @FXML initialisation.
	 */
	public void initialize()
	{
		//redirect console to a Scene TextArea Pane - https://stackoverflow.com/a/33502923
		printStream = new PrintStream(new ConsoleOutput(textArea_consoleOutput));
		System.setOut(printStream);
		System.setErr(printStream);

		//Auto scroll TextArea Pane Listener- https://stackoverflow.com/a/20568196
		textArea_consoleOutput.textProperty().addListener(new ChangeListener<String>()
		{
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
			{
				//Scroll to the bottom of the page
				textArea_consoleOutput.setScrollTop(Double.MAX_VALUE);
				//Scroll to the top of the page
				//textArea_consoleOutput.setScrollTop(Double.MIN_VALUE);
			}
		});

		//prepopulate logon details from file, else just grab usernmae
		getLogonDetailsFromFile();

		//update save location label
		//label_saveLogon.setText("@ " + getLogonDetails());

		//user defined table check box event listener - http://docs.oracle.com/javafx/2/ui_controls/checkbox.htm
		checkBox_targetSchemaName.selectedProperty().addListener(new ChangeListener<Boolean>()
		{
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue)
			{
				if (checkBox_targetSchemaName.isSelected())
				{
					textField_targetSchemaName.setEditable(true);
					textField_targetSchemaName.setOpacity(1.0);
				} else
				{
					textField_targetSchemaName.setEditable(false);
					textField_targetSchemaName.setOpacity(0.5);
				}
			}
		});

		checkBox_targetTableName.selectedProperty().addListener(new ChangeListener<Boolean>()
		{
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue)
			{
				if (checkBox_targetTableName.isSelected())
				{
					textField_targetTableName.setEditable(true);
					textField_targetTableName.setOpacity(1.0);
				} else
				{
					textField_targetTableName.setEditable(false);
					textField_targetTableName.setOpacity(0.5);
				}
			}
		});

		//add the combo box options
		//DHS servers
		comboBox_targetServer.getItems().add("DEV/TEST");
		comboBox_targetServer.getItems().add("PROD");

		//delimiters
		comboBox_sourceFileDelimiter.getItems().add("Comma");
		comboBox_sourceFileDelimiter.getItems().add("Semi-Colon");
		comboBox_sourceFileDelimiter.getItems().add("Space");
		comboBox_sourceFileDelimiter.getItems().add("Tab");
		comboBox_sourceFileDelimiter.getItems().add("Pipe");

		//file encoding
		comboBox_sourceFileEncoding.getItems().add("ASCII");
		comboBox_sourceFileEncoding.getItems().add("Latin (ISO-8859-1)");
		comboBox_sourceFileEncoding.getItems().add("UTF-8");
		comboBox_sourceFileEncoding.getItems().add("UTF-16 BE");
		comboBox_sourceFileEncoding.getItems().add("UTF-16 LE");

		//client to server session encoding
		comboBox_clientToServerCharset.getItems().add("ASCII");
		comboBox_clientToServerCharset.getItems().add("UTF-8");
		comboBox_clientToServerCharset.getItems().add("UTF-16");

		//column charset
		comboBox_targetColumnCharSet.getItems().add("LATIN");
		comboBox_targetColumnCharSet.getItems().add("UNICODE");

		//keyboard shortcuts
/*		menuItem_open.setAccelerator(new KeyCharacterCombination("O", KeyCombination.CONTROL_DOWN));
		menuItem_run.setAccelerator(new KeyCharacterCombination("R", KeyCombination.CONTROL_DOWN));
		menuItem_quit.setAccelerator(new KeyCharacterCombination("F4", KeyCombination.ALT_DOWN));
		menuItem_help.setAccelerator(new KeyCharacterCombination("F1"));*/

		//don't show the progress indicator
		progressIndicator.setVisible(false);
		progressIndicator.setDisable(true);
	}

	/**
	 * This method displays the open file dialogue and has error handling in it
	 */
	private void showOpenDialogue()
	{
		try
		{
			// https://stackoverflow.com/a/25491812
			FileChooser fileChooser = new FileChooser();

			//update the initial directory displayed - https://stackoverflow.com/a/18482428
/*			Path pwd = Paths.get(FastLoadScriptGeneratorGUIMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			fileChooser.setInitialDirectory(pwd.toFile());*/
			//fileChooser.setInitialDirectory(new File(USER_HOME_DIR));

			//check if the user clicked cancel - https://stackoverflow.com/a/28268821
			sourceFilePath = fileChooser.showOpenDialog(new Stage());

			if (sourceFilePath != null)
			{
				textField_sourceFilePath.setText(sourceFilePath.toString());
			}
		} catch (Exception e)
		{
			e.getMessage();
		}
	}

	/**
	 * This method sets the users password when it is manually typed.
	 */
	private void setPassword()
	{
		password = textField_password.getText();
	}

	/**
	 * This method gets the username
	 *
	 * @return A string containing the username.
	 */
	private String getUsername()
	{
		return USERNAME;
	}

	/**
	 * This method gets the password
	 *
	 * @return A string containing a password.
	 */
	private String getPassword()
	{
		return password;
	}

	/**
	 * This method gets the absolute path of the user's logon details.
	 *
	 * @return A String containing the logon details absolute path.
	 */
	private String getLogonFileAbsolutePath()
	{
		return LOGON_FILE_ABSOLUTE_PATH;
	}

	/**
	 * This method gets the target server.  The default is production.
	 *
	 * @return A String containing the target server connection details.
	 */
	private String getTargetServer()
	{
		return targetServer;
	}

	public PrintStream getPrintStream()
	{
		return printStream;
	}

	/**
	 * This method looks for the user's password in their system defined local folder.
	 * If it exists it will load the file, otherwise it will just prepopulate the user name from the system properties.
	 * Windows = C:\Users\ user-name (the space is to stop Java complaining about invalid unicode declaration)
	 * Unix = /home/user-name
	 */
	private void getLogonDetailsFromFile()
	{
		textField_username.setText(getUsername());
		try
		{
			Path userLogonDetails = Paths.get(getLogonFileAbsolutePath());

			if (Files.exists(userLogonDetails))
			{
				//associate standard input to a file
				stdinFile = new BufferedReader(new FileReader(getLogonFileAbsolutePath()));

				// store the current line here
				String currentLine;

				// Store the current line as a String into currentLine.  The readLine() method returns null when the end of line is reached.
				while ((currentLine = stdinFile.readLine()) != null)
				{
					textField_password.setText(currentLine.replaceAll("^.*,", ""));
				}
				// close the open resources
				stdinFile.close();

				//print success to user
				System.out.println("*********************");
				System.out.println("*  READ LOGON FILE  *");
				System.out.println("*********************" + NEWLINE);
				System.out.println("Username and password successfully read in from " + getLogonFileAbsolutePath() + NEWLINE);
			}
		} catch (Exception e)
		{
			System.out.println("<ERROR>" + NEWLINE + e.getMessage() + NEWLINE + "</ERROR>");
		}
	}

	/**
	 * This method saves the user's password in their system defined local folder.
	 * Windows = C:\Users\ user-name  (the space is to stop Java complaining about invalid unicode declaration)
	 * Unix = /home/user-name
	 */
	private void saveUserDetails()
	{
		try
		{
			//associate standard output to a file.
			stdoutFile = new PrintWriter(new FileWriter(getLogonFileAbsolutePath()));

			//write logon details, no need for username since we grab it from Windows
			stdoutFile.println(".LOGON " + getTargetServer() + getUsername() + "," + getPassword());

			//close resources
			stdoutFile.close();

			//print success message
			System.out.println("*********************");
			System.out.println("*  SAVE LOGON FILE  *");
			System.out.println("*********************" + NEWLINE);
			System.out.println("Username and password successfully saved to " + getLogonFileAbsolutePath() + NEWLINE);
		} catch (Exception e)
		{
			System.out.println("<ERROR>" + NEWLINE + e.getMessage() + NEWLINE + "</ERROR>");
		}
	}

	//CONSOLE OUTPUT - https://stackoverflow.com/a/26961603


	/**
	 * This method holds the code that is run when the button or menu option is chosen.
	 */
	private void runScript()
	{
		//validation error flag
		boolean interfaceValidationError = false;

		//check if username was populated
		String username = textField_username.getText();
		if(username.equals(""))
		{
			interfaceValidationError = true;
			showNoUsername();
			//stop validating if an error is encountered
			return;
		}

		//check if password is empty
		String password = textField_password.getText();
		if(password.equals(""))
		{
			interfaceValidationError = true;
			showNoPassword();
			//stop validating if an error is encountered
			return;
		}
		//set the password
		else
		{
			setPassword();
		}

		//check if the user has selected a file to run
		String filePath = textField_sourceFilePath.getText();
		if (filePath.equals(""))
		{
			interfaceValidationError = true;
			showInputFileErrorNoFileSelected();
			//stop validating if an error is encountered
			return;
		}

		//check for source file header
		boolean sourceFileHeader = checkBox_sourceFileHeader.isSelected();

		//update file delimiter
		String comboBoxFileDelimiter = comboBox_sourceFileDelimiter.getValue().toString();
		if (null != comboBoxFileDelimiter)
		{
			if (comboBoxFileDelimiter.equals("Comma"))
			{
				sourceFileDelimiter = COMMA;
			} else if (comboBoxFileDelimiter.equals("Tab"))
			{
				sourceFileDelimiter = TAB;
			} else if (comboBoxFileDelimiter.equals("Pipe"))
			{
				sourceFileDelimiter = PIPE;
			} else if (comboBoxFileDelimiter.equals("Semi-Colon"))
			{
				sourceFileDelimiter = SEMI_COLON;
			} else
			{
				sourceFileDelimiter = SPACE;
			}
		}
		else
		{
			interfaceValidationError = true;
			showInvalidFileDelimiterSelection();
			//stop validating if an error is encountered
			return;
		}
		
		//update file encoding, default is UTF-8
		//must cast combobox object as a String, toString() doesn't work - http://tutorials.jenkov.com/javafx/combobox.html
		String fileEncoding = (String) comboBox_sourceFileEncoding.getValue();
		if(null == fileEncoding)
		{
			sourceFileEncoding = "UTF-8";
		}
		else
		{
			sourceFileEncoding = fileEncoding;
		}

		//check teradata server, default is production
		String teradataServer = (String) comboBox_targetServer.getValue();
		if(null == teradataServer)
		{
			targetServer = PROD_SERVER_NAME;
		}
		else
		{
			targetServer = DEVTEST_SERVER_NAME;
		}

		//check teradata client-to-server encoding, default is ASCII which includes Latin
		String clientToServer = (String) comboBox_clientToServerCharset.getValue();
		if(null == clientToServer)
		{
			clientToServerEncoding = "ASCII";
		}
		else
		{
			clientToServerEncoding = clientToServer;
		}

		//update schema & table name - http://www.info.teradata.com/HTMLPubs/DB_TTU_14_10/index.html#page/SQL_Reference/B035_1141_112A/Ch02.106.13.html
		boolean userProvidedSchemaName = checkBox_targetSchemaName.isSelected();
		if (userProvidedSchemaName)
		{
			//This assignment is never null
			targetSchemaName = textField_targetSchemaName.getText();
			// display alerts for errors
			// no name inputed
			if (targetSchemaName.equals(""))
			{
				interfaceValidationError = true;
				showDatabaseError(0);
				//stop validating if an error is encountered
				return;
			}
			//too long
			else if (targetSchemaName.length() > 30)
			{
				interfaceValidationError = true;
				showDatabaseError(1);
				//stop validating if an error is encountered
				return;
			}
			//invalid characters
			else if (targetSchemaName.matches("^.*[^0-9A-Za-z_].*$"))
			{
				interfaceValidationError = true;
				showDatabaseError(2);
				//stop validating if an error is encountered
				return;
			}
		}

		//check if tablename is valid
		boolean userProvidedTableName = checkBox_targetTableName.isSelected();
		if (userProvidedTableName)
		{
			//This assignment is never null
			targetTableName = textField_targetTableName.getText();
			// display alerts for errors
			//too short
			if (targetTableName.equals(""))
			{
				interfaceValidationError = true;
				showDatabaseError(10);
				//stop validating if an error is encountered
				return;
			}
			//too long
			else if (targetTableName.length() > 27)
			{
				interfaceValidationError = true;
				showDatabaseError(11);
				//stop validating if an error is encountered
				return;
			}
			//invalid characters
			else if (targetTableName.matches("^.*[^0-9A-Za-z_].*$"))
			{
				interfaceValidationError = true;
				showDatabaseError(12);
				//stop validating if an error is encountered
				return;
			}
		}
		//check if the file name is valid to be used as a table name, because no custom table name was entered
		else
		{
			//remove the absolute path and extension as we don't care about that here
			//remove absolute path
			String inputFileNameNoExtension = textField_sourceFilePath.getText().replaceAll("^.*(?=" + Pattern.quote(FILE_SEPARATOR) + ".*$)", "");
			//remove trailing file seperator
			inputFileNameNoExtension = inputFileNameNoExtension.replaceAll((Pattern.quote(FILE_SEPARATOR)), "");
			//remove file extension
			inputFileNameNoExtension = inputFileNameNoExtension.replaceAll(Pattern.quote(".") + ".*$", "");

			//too long
			if(inputFileNameNoExtension.length() > 27)
			{
				interfaceValidationError = true;
				showFileNameError(0);
				//stop validating if an error is encountered
				return;
			}
			//invalid characters
			else if (inputFileNameNoExtension.matches("^.*[^0-9A-Za-z_].*$"))
			{
				interfaceValidationError = true;
				showFileNameError(1);
				//stop validating if an error is encountered
				return;
			}
		}

		//update column charset
		String columnCharSet = (String) comboBox_targetColumnCharSet.getValue();
		if (null != columnCharSet)
		{
			if (columnCharSet.equals("UNICODE"))
			{
				targetTableCharset = "UNICODE";
			} else
			{
				targetTableCharset = "LATIN";
			}
		}
		else
		{
			targetTableCharset = "LATIN";
		}

		//check which script user wants to run
		boolean runUnpause = checkBox_runUnpause.isSelected();

		// write user logon details
		saveUserDetails();

		//run load script - show teh progress indicator when it is running and disable the run button when it is running
		if (runUnpause && interfaceValidationError == false)
		{
			//fastloadScriptGenerator = new FastloadScriptGenerator(sourceFilePath, targetTableCharset, sourceFileHeader, targetTableName, false, printStream);
			//new Thread(() -> fastloadScriptGenerator = new FastloadScriptGenerator(sourceFilePath, targetTableCharset, sourceFileHeader, targetSchemaName, targetTableName, teradataConnectionFile, true, printStream)).start();

			//http://docs.oracle.com/javafx/2/threads/jfxpub-threads.htm
			Task<FastloadScriptGenerator> taskUnpause = new Task<FastloadScriptGenerator>()
			{
				@Override
				public FastloadScriptGenerator call()
				{
					progressIndicator.setDisable(false);
					progressIndicator.setVisible(true);
					button_run.setDisable(true);
					fastloadScriptGenerator = new FastloadScriptGenerator(
							sourceFilePath
							,sourceFileEncoding
							,sourceFileHeader
							,sourceFileDelimiter
							,targetServer
							,targetSchemaName
							,targetTableName
							,targetTableCharset
							,teradataConnectionFile
							,clientToServerEncoding
							,true
							,printStream);
					return fastloadScriptGenerator;
				}
			};
			//https://stackoverflow.com/a/32773187
			taskUnpause.setOnRunning(e ->
			{
				progressIndicator.setVisible(taskUnpause.isRunning());
				button_run.setDisable(taskUnpause.isRunning());
			});
			taskUnpause.setOnFailed(e ->
			{
				progressIndicator.setVisible(taskUnpause.isRunning());
				button_run.setDisable(taskUnpause.isRunning());
				//try to run garbage collection
				fastloadScriptGenerator = null;
				System.gc();
			});
			taskUnpause.setOnSucceeded(e ->
			{
				progressIndicator.setVisible(taskUnpause.isRunning());
				button_run.setDisable(taskUnpause.isRunning());
				//try to run garbage collection
				fastloadScriptGenerator = null;
				System.gc();
			});
			new Thread(taskUnpause).start();

		}
		else if (!runUnpause && interfaceValidationError == false)
		{
			System.out.println("*********************");
			System.out.println("*  PROCESSING FILE  *");
			System.out.println("*********************" + NEWLINE);
			System.out.println("Source file is being processed, please wait." + NEWLINE + "In the VDI, this program can process 100,000 rows every ~30 seconds." + NEWLINE);
			showWaitMessage();
			//new Thread(() -> fastloadScriptGenerator = new FastloadScriptGenerator(sourceFilePath, targetTableCharset, sourceFileHeader, targetSchemaName, targetTableName, teradataConnectionFile, false, printStream)).start();
			Task<FastloadScriptGenerator> taskFastLoad = new Task<FastloadScriptGenerator>()
			{
				@Override
				public FastloadScriptGenerator call()
				{
					progressIndicator.setDisable(false);
					progressIndicator.setVisible(true);
					button_run.setDisable(true);
					button_run.setDisable(true);
					fastloadScriptGenerator = new FastloadScriptGenerator(
							sourceFilePath
							,sourceFileEncoding
							,sourceFileHeader
							,sourceFileDelimiter
							,targetServer
							,targetSchemaName
							,targetTableName
							,targetTableCharset
							,teradataConnectionFile
							,clientToServerEncoding
							,false
							,printStream);

					return fastloadScriptGenerator;
				}
			};
			taskFastLoad.setOnRunning(e ->
			{
				progressIndicator.setVisible(taskFastLoad.isRunning());
				button_run.setDisable(taskFastLoad.isRunning());
			});
			taskFastLoad.setOnFailed(e ->
			{
				progressIndicator.setVisible(taskFastLoad.isRunning());
				button_run.setDisable(taskFastLoad.isRunning());
				//try to run garbage collection
				fastloadScriptGenerator = null;
				System.gc();
			});
			taskFastLoad.setOnSucceeded(e ->
			{
				progressIndicator.setVisible(taskFastLoad.isRunning());
				button_run.setDisable(taskFastLoad.isRunning());
				//try to run garbage collection
				fastloadScriptGenerator = null;
				System.gc();
			});
			new Thread(taskFastLoad).start();
		}
	}

	//POP-UP WINDOWS
	//https://stackoverflow.com/a/28887273
	private void showNoUsername()
	{
		//display error box for no username
		Alert alert = new Alert(Alert.AlertType.ERROR, "No username was retrieved, enter your username.", ButtonType.OK);
		alert.setHeaderText("NO USERNAME.");
		alert.show();
	}

	private void showNoPassword()
	{
		//display error box for no password
		Alert alert = new Alert(Alert.AlertType.ERROR, "No password was entered or retrieved, enter your password.", ButtonType.OK);
		alert.setHeaderText("NO PASSWORD.");
		alert.show();
	}

	private void showInputFileErrorNoFileSelected()
	{
		//display error box for not selecting a file to load
		Alert alert = new Alert(Alert.AlertType.ERROR, "No source file selected, select one.", ButtonType.OK);
		alert.setHeaderText("INVALID SOURCE FILE.");
		alert.show();
	}

	private void showFileNameError(int errorFlag)
	{
		//display error box for not renaming the table and the file name being too long or invalid
		//file name is too long
		if (errorFlag == 0)
		{
			Alert alert = new Alert(Alert.AlertType.ERROR, "The filename is too long to be used as a table name."
					+ NEWLINE + "You must input a custom table name.", ButtonType.OK);
			alert.setHeaderText("INVALID FILENAME.");
			alert.show();
		}
		//file name has invalid characters
		else if (errorFlag == 1)
		{
			Alert alert = new Alert(Alert.AlertType.ERROR, "The filename contains characters that can't be used in a table name."
					+ NEWLINE +  "You must input a custom table name.", ButtonType.OK);
			alert.setHeaderText("INVALID FILENAME.");
			alert.show();
		}
	}

	private void showInvalidFileDelimiterSelection()
	{
		//display error box for not selecting a file delimiter
		Alert errorAlert = new Alert(Alert.AlertType.ERROR, "No file delimiter selected, select one.", ButtonType.OK);
		errorAlert.setHeaderText("INVALID FILE DELIMITER.");
		errorAlert.show();
	}

	private void showDatabaseError(int errorFlag)
	{
		// SCHEMA NAME
		//too short
		if (errorFlag == 0)
		{
			//display error box for empty database name
			Alert errorAlert = new Alert(Alert.AlertType.ERROR, "Database name is too short, type something.", ButtonType.OK);
			errorAlert.setHeaderText("INVALID DATABASE NAME.");
			errorAlert.show();
		}
		//too long
		else if (errorFlag == 1)
		{
			Alert errorAlert = new Alert(Alert.AlertType.ERROR, "Schema name is too long."
					+ NEWLINE + "Database name must be <= 30 because of Teradata object name restrictions.", ButtonType.OK);
			errorAlert.setHeaderText("INVALID DATABASE NAME.");
			errorAlert.show();

		}
		//invalid characters
		else if (errorFlag == 2)
		{
			Alert errorAlert = new Alert(Alert.AlertType.ERROR, "Database name has invalid characters in it.  Only use the following:"
					+ NEWLINE + "[0-9A-Za-z_] - Numbers, lower & UPPER case letters, and/or underscores.", ButtonType.OK);
			errorAlert.setHeaderText("INVALID DATABASE NAME.");
			errorAlert.show();
		}
		// TABLE NAME ERRORS
		//too short
		else if (errorFlag == 10)
		{
			//display error box for empty schema name
			Alert errorAlert = new Alert(Alert.AlertType.ERROR, "Table name is too short, type something.", ButtonType.OK);
			errorAlert.setHeaderText("INVALID TABLE NAME.");
			errorAlert.show();
		}
		//too long
		else if (errorFlag == 11)
		{
			Alert errorAlert = new Alert(Alert.AlertType.ERROR, "Table name is too long, it is greater than 27 characters."
					+ NEWLINE + "Table name must be <= 27 because of the FastLoad error tables prefixes and Teradata object name restrictions.", ButtonType.OK);
			errorAlert.setHeaderText("INVALID TABLE NAME.");
			errorAlert.show();
		}
		//invalid characters
		else if (errorFlag == 12)
		{
			Alert errorAlert = new Alert(Alert.AlertType.ERROR, "Table name has invalid characters in it.  Only use the following:"
					+ NEWLINE + "[0-9A-Za-z_] - Numbers, lower & UPPER case letters, and/or underscores.", ButtonType.OK);
			errorAlert.setHeaderText("TABLE TABLE NAME.");
			errorAlert.show();
		}
	}

	private void showAboutInformation()
	{
		Alert aboutAlert = new Alert(Alert.AlertType.INFORMATION, "About Information.", ButtonType.CLOSE);
		aboutAlert.setTitle("About " + PROGRAM_NAME);
		aboutAlert.setHeaderText("Miscellaneous About Information.");
		aboutAlert.setContentText("Operating System:\t" + OS_NAME + ", " + OS_ARCH + ", " + OS_VERSION + "." + NEWLINE
				+ "Program Name:\t\t" + PROGRAM_NAME + NEWLINE
				+ "Program Version:\t" + PROGRAM_VERSION + NEWLINE
				+ "Program Author:\t\tDallas Hall (hmd911)");
		aboutAlert.show();
	}

	private void showLicenceInformation()
	{
		Alert aboutAlert = new Alert(Alert.AlertType.INFORMATION, "Licence Information.", ButtonType.CLOSE);
		aboutAlert.setTitle("Licence for " + PROGRAM_NAME);
		aboutAlert.setHeaderText("FastLoad Script Generator Licence.");
		aboutAlert.setContentText("FastLoad Script Generator - creates Teradata FastLoad scripts from tab delimited text files.\n" +
				"Copyright (C) Dallas Hall, 2017.\n" +
				"\n" +
				"This program is free software: you can redistribute it and/or modify" +
				"it under the terms of the GNU General Public License as published by" +
				"the Free Software Foundation, either version 3 of the License, or" +
				" (at your option) any later version.\n" +
				"\n" +
				"This program is distributed in the hope that it will be useful, " +
				"but WITHOUT ANY WARRANTY; without even the implied warranty of" +
				"MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the " +
				"GNU General Public License for more details.\n" +
				"\n" +
				"You should have received a copy of the GNU General Public License" +
				"along with this program.  If not, see <http://www.gnu.org/licenses/>.");
		aboutAlert.show();
	}

	private void showHelpInformation()
	{
		Alert helpAlert = new Alert(Alert.AlertType.INFORMATION, "Help Information.", ButtonType.CLOSE);
		helpAlert.setTitle("Help for " + PROGRAM_NAME);
		helpAlert.setHeaderText("Miscellaneous Help Information.");
		helpAlert.setContentText("1) Read the README.txt file. There is a lot there."
				+ NEWLINE + "2) Mouse over things for tool tips for additional hints."
				+ NEWLINE + "3) Follow the information in the pop-up windows."
				+ NEWLINE + "4) Follow the suggestions on the main program screen.");

		helpAlert.show();
	}


	private void showWaitMessage()
	{
		Alert aboutAlert = new Alert(Alert.AlertType.INFORMATION, "About Information.", ButtonType.CLOSE);
		aboutAlert.setTitle("Running " + PROGRAM_NAME);
		aboutAlert.setHeaderText("Patience Is A Virtue.");
		aboutAlert.setContentText("Parsing input file, please wait."
				+ NEWLINE + "In the VDI, this program can process 100,000 rows every ~30 seconds."
				+ NEWLINE + "Read the program output panel for runtime information."
				+ NEWLINE + "The progress bar above the Teradata logo will disappear when processing is completed.");
		aboutAlert.show();
	}

	@FXML
	private void quitProgram()
	{
		Alert alertQuit = new Alert(Alert.AlertType.CONFIRMATION, "");
		alertQuit.setTitle(PROGRAM_NAME + " v" + PROGRAM_VERSION);
		alertQuit.setHeaderText("QUIT THE PROGRAM.");
		alertQuit.setContentText("Are you sure you want to quit?");
		//https://docs.oracle.com/javase/8/javafx/api/javafx/scene/control/Alert.html
		Optional<ButtonType> clickResult = alertQuit.showAndWait();
		if (clickResult.isPresent() && clickResult.get() == ButtonType.OK) {
			Platform.exit();
		}
	}

	//EVENT HANDLERS
	// File Sub-Menu

	/**
	 * This method handles the user event
	 *
	 * @param event Click the Open menu item within the File sub-menu.
	 */
	@FXML
	protected void openFileMenuItem(ActionEvent event)
	{
		showOpenDialogue();
	}

	/**
	 * @param event
	 */
	@FXML
	protected void runFileMenuItem(ActionEvent event)
	{
		//load it in a new thread, so the GUI still responds - https://stackoverflow.com/a/31685857
		//new Thread(() -> runScript()).start();
		runButton();
	}

	//Help Sub-Menu
	@FXML
	protected void openHelpMenuItem(ActionEvent event)
	{
		showHelpInformation();
	}

	@FXML
	protected void openLicenceMenuItem(ActionEvent event)
	{
		showLicenceInformation();
	}

	@FXML
	protected void openAboutMenuItem(ActionEvent event)
	{
		showAboutInformation();
	}

	/**
	 * @param event
	 */
	// quit the program via menu item - http://docs.oracle.com/javafx/2/ui_controls/menu_controls.htm#BABGIIGB
	@FXML
	protected void quitProgramMenu(ActionEvent event)
	{
		System.exit(0);
	}

	//TEXT FIELDS

	//CHECK BOXES

	//COMBO BOXES

	//BUTTONS
	// quit the program via button - https://stackoverflow.com/a/25038465

	/**
	 * @param event
	 */
	@FXML
	protected void quitProgramButton(ActionEvent event)
	{
		Stage stage = (Stage) button_quit.getScene().getWindow();
		stage.close();
	}

	/**
	 * @param event
	 */
	@FXML
	protected void openFileButton(ActionEvent event)
	{
		showOpenDialogue();
	}

	/**
	 *
	 */
	@FXML
	protected void runButton()
	{
		runScript();
	}

	//TEXTAREA - handled by ConsoleOutput class

	//PROGRESS INDICATOR

}

