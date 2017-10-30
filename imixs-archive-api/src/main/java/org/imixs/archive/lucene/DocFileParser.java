package org.imixs.archive.lucene;


import java.io.FileInputStream;

import org.apache.poi.hslf.extractor.PowerPointExtractor;
import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/**
 * This class parses the microsoft word files except .docx,.pptx and 
 * latest MSword files.
 *   
 * @author Mubin Shrestha
 */
public class DocFileParser {   
	 /**
	    * This method parses the content of the .doc file.
	    * i.e. this method will return all the text of the file passed to it.
	    * @param fileName : file name of which you want the content of.
	    * @return : returns the content of the file
	    */
	    public String DocFileContentParser(String fileName) {
	        POIFSFileSystem fs = null;
	        try {
	           
	            if (fileName.endsWith(".xls")) { //if the file is excel file
	                ExcelExtractor ex = new ExcelExtractor(fs);
	                return ex.getText(); //returns text of the excel file
	            } else if (fileName.endsWith(".ppt")) { //if the file is power point file
	                PowerPointExtractor extractor = new PowerPointExtractor(fs);
	                return extractor.getText(); //returns text of the power point file

	            }
	            
	            //else for .doc file
	            fs = new POIFSFileSystem(new FileInputStream(fileName));
	            HWPFDocument doc = new HWPFDocument(fs);
	            WordExtractor we = new WordExtractor(doc);
	            return we.getText();//if the extension is .doc
	        } catch (Exception e) {
	            System.out.println("document file cant be indexed");
	        }
	        return "";
	    }

	    /**
	     * Main method.
	     * @param args 
	     */
	    public static void main(String args[])
	    {
	        String filepath = "H:/Filtering.ppt";
	        System.out.println(new DocFileParser().DocFileContentParser(filepath));
	        
	    }
	}