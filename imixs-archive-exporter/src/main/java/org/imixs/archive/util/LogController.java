package org.imixs.archive.util;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * The LogController stores a message log under a specific topic. In this way
 * the logController can be use in different context.
 *
 * @author rsoika
 *
 */
@Named
@ApplicationScoped
public class LogController implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int LOG_INFO = 1;
    public static final int LOG_WARNING = 2;
    public static final int LOG_ERROR = 3;

    private static Logger logger = Logger.getLogger(LogController.class.getName());
    String pattern = " HH:mm:ss.SSSZ";
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);

    private Map<String, List<String>> logTopics;

    private int maxSize = 30;

    /**
     * Init LogTopics
     */
    public LogController() {
        super();
        logTopics = new HashMap<String, List<String>>();
    }

    public List<String> reset(String context) {
        logTopics.put(context, new ArrayList<String>());
        return logTopics.get(context);
    }

    public void info(String context, String message) {
        add(context, LOG_INFO, message);
    }

    public void warning(String context, String message) {
        add(context, LOG_WARNING, message);
    }

    public void severe(String context, String message) {
        add(context, LOG_ERROR, message);
    }

    /**
     * Logs a new message to the message log
     *
     * @param message
     */
    private void add(String topic, int type, String message) {
        // get the logger
        List<String> logEntries = logTopics.get(topic);
        if (logEntries == null) {
            logEntries = reset(topic);
        }

        // check maxsize...
        while (logEntries.size() > maxSize) {
            logEntries.remove(0);
        }

        String entry = simpleDateFormat.format(new Date()) + " ";
        if (type == LOG_ERROR) {
            entry = entry + "[ERROR] ";
            logger.severe(message);
        } else if (type == LOG_WARNING) {
            entry = entry + "[WARNING] ";
            logger.warning(message);
        } else {
            entry = entry + "[INFO]    ";
            logger.info(message);

        }
        entry = entry + message;
        logEntries.add(entry);
    }

    public List<String> getLogEntries(String context) {
        return logTopics.get(context);
    }

}