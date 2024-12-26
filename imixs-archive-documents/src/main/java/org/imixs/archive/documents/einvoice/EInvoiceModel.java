package org.imixs.archive.documents.einvoice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A EInvoiceModel represents the dom tree of a e-invoice.
 * <p>
 * The model elements can be read only.
 * 
 * @author rsoika
 *
 */
public abstract class EInvoiceModel {
    protected static Logger logger = Logger.getLogger(EInvoiceModel.class.getName());

    private static final SecureRandom random = new SecureRandom();
    private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    private Document doc;
    private Element root;
    // elements
    protected String id = null;
    protected String buyerReference = null;
    protected LocalDate issueDateTime = null;
    protected LocalDate dueDateTime = null;
    protected BigDecimal grandTotalAmount = new BigDecimal("0.00");
    protected BigDecimal taxTotalAmount = new BigDecimal("0.00");
    protected BigDecimal netTotalAmount = new BigDecimal("0.00");
    protected Set<TradeParty> tradeParties = null;

    private final Map<EInvoiceNS, String> URI_BY_NAMESPACE = new HashMap<>();
    private final Map<EInvoiceNS, String> PREFIX_BY_NAMESPACE = new HashMap<>();

    /**
     * This method instantiates a new eInvoice model based on a given
     * org.w3c.dom.Document. The method parses the namespaces.
     * <p>
     * 
     * 
     * 
     * @param doc
     */
    public EInvoiceModel(Document doc) {
        // this();
        tradeParties = new LinkedHashSet<>();

        if (doc != null) {
            this.doc = doc;
            root = doc.getDocumentElement();
            if (getRoot() != null) {
                setNameSpaces();
                parseContent();
            }

        }
    }

    public void setNameSpaces() {
    }

    public void parseContent() {
    }

    public Document getDoc() {
        return doc;
    }

    public Element getRoot() {
        return root;
    }

    public String getId() {
        return id;
    }

    public LocalDate getIssueDateTime() {
        return issueDateTime;
    }

    public LocalDate getDueDateTime() {
        return dueDateTime;
    }

    public BigDecimal getGrandTotalAmount() {
        return grandTotalAmount;
    }

    public BigDecimal getTaxTotalAmount() {
        return taxTotalAmount;
    }

    public BigDecimal getNetTotalAmount() {
        return netTotalAmount;
    }

    public abstract void setNetTotalAmount(BigDecimal value);

    public void setNetTotalAmount(Double value) {
        setNetTotalAmount(BigDecimal.valueOf(value));
    }

    public abstract void setGrandTotalAmount(BigDecimal value);

    public void setGrandTotalAmount(Double value) {
        setGrandTotalAmount(BigDecimal.valueOf(value));
    }

    /**
     * Returns all trade parties
     * 
     * @return
     */
    public Set<TradeParty> getTradeParties() {
        if (tradeParties == null) {
            tradeParties = new LinkedHashSet<TradeParty>();
        }
        return tradeParties;
    }

    /**
     * Adds a new Trade party. If a party with this type already exists, the method
     * removes first the existing party.
     * 
     * @param party
     */
    public void setTradeParty(TradeParty party) {

        if (party == null) {
            return;
        }

        // Remove existing party of same type (if exists)
        TradeParty existingParty = findTradeParty(party.getType());
        if (existingParty != null) {
            tradeParties.remove(existingParty);
        }

        // Add new party
        tradeParties.add(party);
    }

    /**
     * Finds a Trade Party by its type. Method can return null if not trade party of
     * the type is defined in the invoice
     * 
     * @param type
     * @return
     */
    public TradeParty findTradeParty(String type) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        Iterator<TradeParty> iterParties = getTradeParties().iterator();
        while (iterParties.hasNext()) {
            TradeParty party = iterParties.next();
            if (type.equals(party.getType())) {
                return party;
            }
        }
        // not found
        return null;
    }

    /**
     * This helper method returns a set of child nodes by name from a given parent
     * node. If no nodes were found, the method returns an empty list.
     * <p>
     * The method compares the Name including the namespace of the child elements.
     * <p>
     * See also {@link #findChildNodeByName(Element parent, String nodeName)
     * findChildNodeByName}
     * 
     * @param parent
     * @param nodeName
     * @return - list of nodes. If no nodes were found, the method returns an empty
     *         list
     */
    public Set<Element> findChildNodesByName(Element parent, EInvoiceNS ns, String nodeName) {
        Set<Element> result = new LinkedHashSet<Element>();
        // resolve the tag name
        String tagName = getPrefix(ns) + nodeName;
        if (parent != null && nodeName != null) {
            NodeList childs = parent.getChildNodes();
            for (int i = 0; i < childs.getLength(); i++) {
                Node childNode = childs.item(i);

                if (childNode.getNodeType() == Node.ELEMENT_NODE && tagName.equals(childNode.getNodeName())) {
                    result.add((Element) childNode);
                }
            }
        }
        return result;
    }

    /**
     * This helper method returns the first child node by name from a given parent
     * node. If no nodes were found the method returns null.
     * 
     * See also {@link #findChildNodesByName(Element parent, String nodeName)
     * findChildNodesByName}
     * 
     * @param parent
     * @param nodeName
     * @return - Child Element matching the given node name. If no nodes were found,
     *         the method returns null
     */
    public Element findChildNodeByName(Element parent, EInvoiceNS ns, String nodeName) {
        Set<Element> elementList = findChildNodesByName(parent, ns, nodeName);
        if (elementList.iterator().hasNext()) {
            // return first element
            return elementList.iterator().next();
        } else {
            // no child elements with the given name found!
            return null;
        }
    }

    /**
     * Helper method to update or create an element with a given value
     */
    public void updateElementValue(Element parent, String elementName, String value) {
        if (value == null) {
            return;
        }
        Element element = findChildNodeByName(parent, EInvoiceNS.RAM, elementName);
        if (element == null) {
            element = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + elementName);
            parent.appendChild(element);
        }
        element.setTextContent(value);
    }

    /**
     * Returns the central logger instance
     * 
     * @return
     */
    public static Logger getLogger() {
        return logger;
    }

    public static void log(String message) {
        logger.info(message);
    }

    public static void warning(String message) {
        logger.warning(message);
    }

    public static void error(String message) {
        logger.severe(message);
    }

    public static void debug(String message) {
        logger.info(message);
    }

    /**
     * Returns the namespace uri for a given namespace
     * 
     * @param ns
     * @return
     */
    public String getUri(EInvoiceNS ns) {
        return URI_BY_NAMESPACE.get(ns);
    }

    public void setUri(EInvoiceNS ns, String uri) {
        URI_BY_NAMESPACE.put(ns, uri);
    }

    /**
     * Returns the namespace prefix for a given namespace - e.g. 'ram' or
     * 'rsm'...
     * <p>
     * This is necessary because a model can work with the default namespace
     * prefix. The model instance automatically detects the used
     * namespace prefix and updates the prefix when loading a model file.
     * 
     * @param ns
     * @return
     */
    public String getPrefix(EInvoiceNS ns) {
        return PREFIX_BY_NAMESPACE.get(ns);
    }

    /**
     * Updates the namespace prefix for a given BPMN namespace - e.g. 'bpmn2' or
     * 'bpmndi'
     * The method automatically adds the prefix separator ':' if the prefix is not
     * empty. This is necessary to handle default namespaces without a prefix
     * correctly
     */
    public void setPrefix(EInvoiceNS ns, String prefix) {
        if (prefix == null) {
            prefix = "";
        }
        if (prefix.isEmpty()) {
            PREFIX_BY_NAMESPACE.put(ns, prefix);
        } else {
            PREFIX_BY_NAMESPACE.put(ns, prefix + ":");
        }

    }

    /**
     * Returns the XML representation of the current document as a byte array
     * 
     * @return byte array containing the XML data
     * @throws TransformerException
     */
    public byte[] getContent() throws TransformerException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // Setup transformer
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            // Configure output properties
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");

            // Transform DOM to byte array
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(outputStream);
            transformer.transform(source, result);

            return outputStream.toByteArray();

        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                logger.warning("Failed to close output stream: " + e.getMessage());
            }
        }
    }

}
