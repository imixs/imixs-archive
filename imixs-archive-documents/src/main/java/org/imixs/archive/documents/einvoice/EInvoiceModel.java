package org.imixs.archive.documents.einvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
public class EInvoiceModel {
    protected static Logger logger = Logger.getLogger(EInvoiceModel.class.getName());

    private static final SecureRandom random = new SecureRandom();
    private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    private Document doc;
    private Element crossIndustryInvoice;
    private Element exchangedDocumentContext;
    private Element exchangedDocument;
    private Element supplyChainTradeTransaction;

    // elements
    protected String id = null;
    protected String buyerReference = null;
    protected LocalDate issueDateTime = null;
    protected BigDecimal grandTotalAmount = new BigDecimal("0.00");
    protected BigDecimal taxTotalAmount = new BigDecimal("0.00");
    protected BigDecimal netTotalAmount = new BigDecimal("0.00");
    protected Set<TradeParty> tradeParties = null;

    private final Map<EInvoiceNS, String> URI_BY_NAMESPACE = new HashMap<>();
    private final Map<EInvoiceNS, String> PREFIX_BY_NAMESPACE = new HashMap<>();
    public static final String FILE_PREFIX = "file://";

    /**
     * This method instantiates a new BPMN model with the default BPMN namespaces
     * and prefixes.
     * 
     * @param doc
     */
    private EInvoiceModel() {
        setUri(EInvoiceNS.A, "urn:un:unece:uncefact:data:standard:QualifiedDataType:100");
        setUri(EInvoiceNS.RSM, "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100");
        setUri(EInvoiceNS.QDT, "urn:un:unece:uncefact:data:standard:QualifiedDataType:10");
        setUri(EInvoiceNS.RAM, "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100");
        setUri(EInvoiceNS.UDT, "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100");

        setPrefix(EInvoiceNS.A, "a");
        setPrefix(EInvoiceNS.RSM, "rsm");
        setPrefix(EInvoiceNS.QDT, "qdt");
        setPrefix(EInvoiceNS.RAM, "ram");
        setPrefix(EInvoiceNS.UDT, "udt");

    }

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
        this();
        tradeParties = new LinkedHashSet<>();

        if (doc != null) {
            this.doc = doc;

            crossIndustryInvoice = doc.getDocumentElement();

            // parse the BPMN namespaces
            NamedNodeMap defAttributes = crossIndustryInvoice.getAttributes();
            for (int j = 0; j < defAttributes.getLength(); j++) {
                Node node = defAttributes.item(j);

                if (getPrefix(EInvoiceNS.A).equals(node.getLocalName())
                        && !getUri(EInvoiceNS.A).equals(node.getNodeValue())) {
                    logger.fine("...set A namespace URI: " + node.getNodeValue());
                    setUri(EInvoiceNS.A, node.getNodeValue());
                }
                if (getPrefix(EInvoiceNS.RSM).equals(node.getLocalName())
                        && !getUri(EInvoiceNS.RSM).equals(node.getNodeValue())) {
                    logger.fine("...set RSM namespace URI: " + node.getNodeValue());
                    setUri(EInvoiceNS.RSM, node.getNodeValue());
                }
                if (getPrefix(EInvoiceNS.QDT).equals(node.getLocalName())
                        && !getUri(EInvoiceNS.QDT).equals(node.getNodeValue())) {
                    logger.fine("...set QDT namespace URI: " + node.getNodeValue());
                    setUri(EInvoiceNS.QDT, node.getNodeValue());
                }
                if (getPrefix(EInvoiceNS.RAM).equals(node.getLocalName())
                        && !getUri(EInvoiceNS.RAM).equals(node.getNodeValue())) {
                    logger.fine("...set RAM namespace URI: " + node.getNodeValue());
                    setUri(EInvoiceNS.RAM, node.getNodeValue());
                }
                if (getPrefix(EInvoiceNS.UDT).equals(node.getLocalName())
                        && !getUri(EInvoiceNS.UDT).equals(node.getNodeValue())) {
                    logger.fine("...set UDT namespace URI: " + node.getNodeValue());
                    setUri(EInvoiceNS.UDT, node.getNodeValue());
                }

            }

            exchangedDocumentContext = findChildNodeByName(crossIndustryInvoice, EInvoiceNS.RSM,
                    "ExchangedDocumentContext");
            exchangedDocument = findChildNodeByName(crossIndustryInvoice, EInvoiceNS.RSM, "ExchangedDocument");
            supplyChainTradeTransaction = findChildNodeByName(crossIndustryInvoice, EInvoiceNS.RSM,
                    "SupplyChainTradeTransaction");

            // Load e-invoice standard data
            loadDocumentCoreData();

        }
    }

    public Document getDoc() {
        return doc;
    }

    public Element getCrossIndustryInvoice() {
        return crossIndustryInvoice;
    }

    public Element getExchangedDocumentContext() {
        return exchangedDocumentContext;
    }

    public Element getExchangedDocument() {
        return exchangedDocument;
    }

    public String getId() {
        return id;
    }

    public LocalDate getIssueDateTime() {
        return issueDateTime;
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
        Iterator<TradeParty> iterParties = tradeParties.iterator();
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

    public TradeParty parseTradeParty(Element tradePartyElement, String type) {
        TradeParty tradeParty = new TradeParty(type);
        Element element = null;

        // Parse name
        element = findChildNodeByName(tradePartyElement, EInvoiceNS.RAM,
                "Name");
        if (element != null) {
            tradeParty.setName(element.getTextContent());
        }

        Element postalAddress = findChildNodeByName(tradePartyElement, EInvoiceNS.RAM,
                "PostalTradeAddress");
        if (postalAddress != null) {
            element = findChildNodeByName(postalAddress, EInvoiceNS.RAM,
                    "PostcodeCode");
            if (element != null) {
                tradeParty.setPostcodeCode(element.getTextContent());
            }
            element = findChildNodeByName(postalAddress, EInvoiceNS.RAM,
                    "CityName");
            if (element != null) {
                tradeParty.setCityName(element.getTextContent());
            }
            element = findChildNodeByName(postalAddress, EInvoiceNS.RAM,
                    "CountryID");
            if (element != null) {
                tradeParty.setCountryId(element.getTextContent());
            }
            element = findChildNodeByName(postalAddress, EInvoiceNS.RAM,
                    "LineOne");
            if (element != null) {
                tradeParty.setStreetAddress(element.getTextContent());
            }
        }

        Element specifiedTaxRegistration = findChildNodeByName(tradePartyElement, EInvoiceNS.RAM,
                "SpecifiedTaxRegistration");
        if (specifiedTaxRegistration != null) {
            element = findChildNodeByName(specifiedTaxRegistration, EInvoiceNS.RAM,
                    "ID");
            if (element != null) {
                tradeParty.setVatNumber(element.getTextContent());
            }
        }
        return tradeParty;
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

    private void loadDocumentCoreData() {
        Element element = null;

        // read invoice number
        element = findChildNodeByName(exchangedDocument, EInvoiceNS.RAM, "ID");
        if (element != null) {
            id = element.getTextContent();
        }

        // read Date time
        element = findChildNodeByName(exchangedDocument, EInvoiceNS.RAM, "IssueDateTime");
        if (element != null) {
            Element dateTimeElement = findChildNodeByName(element, EInvoiceNS.UDT, "DateTimeString");
            if (dateTimeElement != null) {
                String dateStr = dateTimeElement.getTextContent();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                issueDateTime = LocalDate.parse(dateStr, formatter);
            }
        }

        // read Total amount
        element = findChildNodeByName(supplyChainTradeTransaction, EInvoiceNS.RAM, "ApplicableHeaderTradeSettlement");
        if (element != null) {
            Element tradeSettlementElement = findChildNodeByName(element, EInvoiceNS.RAM,
                    "SpecifiedTradeSettlementHeaderMonetarySummation");
            if (tradeSettlementElement != null) {
                Element child = findChildNodeByName(tradeSettlementElement, EInvoiceNS.RAM, "GrandTotalAmount");
                if (child != null) {
                    grandTotalAmount = new BigDecimal(child.getTextContent());
                }
                child = findChildNodeByName(tradeSettlementElement, EInvoiceNS.RAM, "TaxTotalAmount");
                if (child != null) {
                    taxTotalAmount = new BigDecimal(child.getTextContent());
                }
                netTotalAmount = grandTotalAmount.subtract(taxTotalAmount).setScale(2, RoundingMode.HALF_UP);

            }

        }

        // read ApplicableHeaderTradeAgreement - buyerReference
        element = findChildNodeByName(supplyChainTradeTransaction, EInvoiceNS.RAM, "ApplicableHeaderTradeAgreement");
        if (element != null) {
            Element buyerReferenceElement = findChildNodeByName(element, EInvoiceNS.RAM,
                    "BuyerReference");
            if (buyerReferenceElement != null) {
                buyerReference = buyerReferenceElement.getTextContent();
            }
            Element tradePartyElement = findChildNodeByName(element, EInvoiceNS.RAM,
                    "SellerTradeParty");
            if (tradePartyElement != null) {
                tradeParties.add(parseTradeParty(tradePartyElement, "seller"));
            }
            tradePartyElement = findChildNodeByName(element, EInvoiceNS.RAM,
                    "BuyerTradeParty");
            if (tradePartyElement != null) {
                tradeParties.add(parseTradeParty(tradePartyElement, "buyer"));
            }
        }

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

}
