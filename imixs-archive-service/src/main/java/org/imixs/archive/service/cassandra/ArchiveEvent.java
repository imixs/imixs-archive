package org.imixs.archive.service.cassandra;

import org.imixs.workflow.ItemCollection;

/**
 * The ArchiveEvent provides a CDI observer pattern. The ArchiveEvent is fired
 * by the DocumentService EJB. An event Observer can react on a save or load
 * event.
 * 
 * 
 * The DocumentEvent defines the following event types:
 * <ul>
 * <li>ON_DOCUMENT_SAVE - send immediately before a document will be saved
 * <li>ON_DOCUMENT_LOAD - send immediately after a document was loaded
 * <li>ON_DOCUMENT_DELETE - send immediately before a document will be deleted
 * </ul>
 * 
 * @author Ralph Soika
 * @version 1.0
 * @see org.imixs.workflow.engine.WorkflowService
 */
public class ArchiveEvent {

    public static final int ON_ARCHIVE = 1;
    public static final int ON_RESTORE = 2;
    public static final int ON_DELETE = 3;

    private int eventType;
    private ItemCollection document;

    public ArchiveEvent(ItemCollection document, int eventType) {
        this.eventType = eventType;
        this.document = document;
    }

    public int getEventType() {
        return eventType;
    }

    public ItemCollection getDocument() {
        return document;
    }

}
