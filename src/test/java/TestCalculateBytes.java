
import java.nio.charset.Charset;

public class TestCalculateBytes
{
	//@@@ INSTANCE VARIABLES @@@
	//### Character Sets ###
	private static final Charset ASCII = Charset.forName("US-ASCII");
	private static final Charset LATIN = Charset.forName("ISO-8859-1");
	private static final Charset UTF8 = Charset.forName("UTF-8");
	//default is BE
	private static final Charset UTF16 = Charset.forName("UTF-16");
	private static final Charset UTF16_BE = Charset.forName("UTF-16BE");
	private static final Charset UTF16_LE = Charset.forName("UTF-16LE");
	// all in an array
	private static final Charset[] charsetArray = {Charset.forName("US-ASCII"), Charset.forName("ISO-8859-1"), Charset.forName("UTF-8"), Charset.forName("UTF-16"), Charset.forName("UTF-16BE"), Charset.forName("UTF-16LE")};

	//### test data ###
	private static final char EURO = '\u20ac';
	private static final String BANK_BALANCE = "123.45";

	//@@@ MAIN METHOD @@@
	public static void main(String[] args)
	{
		TestCalculateBytes runtime = new TestCalculateBytes();
	}

	//@@@ CONSTRUCTOR(S) @@@
	public TestCalculateBytes()
	{
		String startingString = EURO + BANK_BALANCE;
		for (int i = 0; i < charsetArray.length; i++)
		{
			String currentCharset = charsetArray[i].toString();
			calculateBytesInChar(startingString, charsetArray[i]);
		}
	}

	//@@@ METHODS @@@
	private int calculateBytesInChar(String inputString, Charset inputCharset)
	{
		//processing varaibles
		char[] inputCharArray = inputString.toCharArray();

		//return variables
		int totalByteCount = 0;

		int largestByteCount = 0;
		String largestByteChar = null;

		for (int i = 0; i < inputCharArray.length; i++)
		{
			String currentChar = inputCharArray[i] + "";
			byte[] bytes = currentChar.getBytes(inputCharset);
			totalByteCount+=bytes.length;
			if(bytes.length > largestByteCount)
			{
				largestByteCount = bytes.length;
				//https://docs.oracle.com/javase/tutorial/i18n/text/string.html
				largestByteChar = new String(bytes, inputCharset);
			}
		}
		System.out.println("Starting string is: " + inputString);
		System.out.println("String's character length is: " + inputString.length());
		System.out.println("String's byte(s) length is: " + totalByteCount);
		System.out.println(largestByteChar + " in " + inputCharset.name() + " is "  + largestByteCount + " byte(s)\n");
		return largestByteCount;
	}
}
