package org.imixs.workflow.archive.hadoop;

import java.util.Map;

/**
 * HadoopUtil provides helper methods to convert data objects.
 * 
 * @author rsoika
 *
 */
public class HadoopUtil {

	/*
	 * { "friends":["Rick","Ron","Victor"], "name":"John", "mobile":847339282,
	 * "city":"London" }
	 * 
	 */
	public static String getJSON(Map<String, Object> map) {

		StringBuffer sb=new StringBuffer();
		
		sb.append("{");
		
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue().toString();
			
			sb.append("\"" + key + "\":\"" + value + "\"");
		}
		sb.append("}");
		return sb.toString();
	}
}
