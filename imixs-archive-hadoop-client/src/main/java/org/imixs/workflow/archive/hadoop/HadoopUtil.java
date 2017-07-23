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

		StringBuffer sb = new StringBuffer();

		sb.append("{");
		int size = map.size();
		int count = 0;
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String key = entry.getKey();
			sb.append("\"" + key + "\":\"");

			Object value = entry.getValue();
			if (value != null) {
				sb.append(entry.getValue().toString());
			}
			sb.append("\"");

			count++;
			if (count < size) {
				sb.append(",");
			}

		}
		sb.append("}");
		return sb.toString();
	}
}
