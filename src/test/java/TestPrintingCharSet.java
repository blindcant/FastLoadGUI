import java.nio.charset.Charset;

public class TestPrintingCharSet
{
	//@@@ INSTANCE VARIABLES @@@
	//### Character Sets ###
	private static final Charset ASCII = Charset.forName("US-ASCII");
	private static final Charset LATIN = Charset.forName("ISO-8859-1");
	private static final Charset UTF8 = Charset.forName("UTF-8");
	//default is BE
	private static final Charset UTF16_BE = Charset.forName("UTF-16");
	private static final Charset UTF16_LE = Charset.forName("UTF-16LE");


	private static final Charset[] charsetArray = {Charset.forName("US-ASCII"), Charset.forName("ISO-8859-1"), Charset.forName("UTF-8"), Charset.forName("UTF-16"), Charset.forName("UTF-16LE")};

	//@@@ MAIN METHOD @@@
	public static void main(String[] args)
	{
		TestPrintingCharSet runtime = new TestPrintingCharSet();
	}
	//@@@ CONSTRUCTOR(S) @@@
	public TestPrintingCharSet()
	{
		for (int i = 0; i < charsetArray.length; i++)
		{
			System.out.println(charsetArray[i].toString());
		}
	}

	//@@@ METHODS @@@
	private String printCharset(Charset inputCharset)
	{
		return inputCharset.toString();
	}
}
