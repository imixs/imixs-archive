package org.imixs.archive.importer.mail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Gotenberg is a Docker-powered stateless API for converting HTML, Markdown and
 * Office documents to PDF.
 * <p>
 * This java client can be used to send a HTML file to the docker service to
 * receive a PDF file based on the Gotenberg API. This implementation uses pure
 * java based on JDK 1.8 and did not depend on any external library. You can use
 * this client on your own code base or as a scaffold for a custom
 * implementation.
 * <p>
 * The data is expected in UTF-8 encoding
 * 
 * @author rsoika
 * @version 1.0
 * @see https://github.com/thecodingmachine/gotenberg/
 * @see https://www.codejava.net/java-se/networking/upload-files-by-sending-multipart-request-programmatically
 * 
 */
public class GotenbergClient {

    private static final String LINE_FEED = "\r\n";

    public static byte[] convertHTML(String gotenbertEndpoint, InputStream inputStream) throws IOException {
        byte[] pdfResult;
        String boundary;
        // creates a unique boundary based on time stamp
        boundary = "------------------------" + System.currentTimeMillis();
        if (!gotenbertEndpoint.endsWith("/")) {
            gotenbertEndpoint = gotenbertEndpoint + "/";
        }
        
        URL url = new URL(gotenbertEndpoint + "convert/html");
        HttpURLConnection  httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(true);    // indicates POST method
        httpConn.setDoInput(true);
        httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        OutputStream outputStream = httpConn.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);
        
        // add html file...
        writer.append("--" + boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"fileUpload\"; filename=\"index.html\"").append(LINE_FEED);
        writer.append("Content-Type: text/html").append(LINE_FEED);
        writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
        writer.append(LINE_FEED);
        writer.flush();
        
        byte[] buffer = new byte[4096];
        int bytesRead = -1;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        inputStream.close();
        writer.append(LINE_FEED);
        writer.flush();
        writer.append("--" + boundary + "--").append(LINE_FEED);
        writer.close();
        
         // checks server's status code first
        int status = httpConn.getResponseCode();
        System.out.println("http con response code=" + status);
        if (status == HttpURLConnection.HTTP_OK) {
            InputStream in = null;
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            in = httpConn.getInputStream();
            byte[] block = new byte[1024];
            int length;
            while ((length = in.read(block)) != -1) {
                bout.write(block, 0, length);
            }
            in.close();
            pdfResult=  bout.toByteArray();
            httpConn.disconnect();
        } else {
            throw new IOException("Server connection failed - status: " + status);
        }

        return pdfResult;
    }

}
