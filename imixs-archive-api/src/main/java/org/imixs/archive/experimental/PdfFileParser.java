package org.imixs.archive.experimental;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** 
 * This class parses the pdf file.
 * i.e this class returns the text from the pdf file.
 * @author Mubin Shrestha
 */
public class PdfFileParser {
	/**
	    * This method returns the pdf content in text form.
	    * @param pdffilePath : pdf file path of which you want to parse text
	    * @return : texts from pdf file
	    * @throws FileNotFoundException
	    * @throws IOException 
	    */
	    public String PdfFileParser(String pdffilePath) throws FileNotFoundException, IOException
	    {
	        String content;
	        FileInputStream fi = new FileInputStream(new File(pdffilePath));
	        PDFParser parser = new PDFParser((RandomAccessRead) fi);
	        parser.parse();
	        COSDocument cd = parser.getDocument();
	        PDFTextStripper stripper = new PDFTextStripper();
	        content = stripper.getText(new PDDocument(cd));
	        cd.close();
	        return content;
	    }
	    
	    /**
	     * Main method.
	     * @param args
	     * @throws FileNotFoundException
	     * @throws IOException 
	     */
	    public static void main(String args[]) throws FileNotFoundException, IOException
	    {
	        String filepath = "H:/lab.pdf";
	        System.out.println(new PdfFileParser().PdfFileParser(filepath));
	        
	        
	     //   doc = LucenePDFDocument.getDocument(file);
	        
	    }
	}