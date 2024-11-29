package org.imixs.archive.documents.einvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * A EInvoiceModel represents the dom tree of a e-invoice.
 * <p>
 * The model elements can be read only.
 * 
 * @author rsoika
 *
 */
public class EInvoiceModelUBL extends EInvoiceModel {

    public EInvoiceModelUBL(Document doc) {
        super(doc);
    }

    /**
     * This method instantiates a new BPMN model with the default BPMN namespaces
     * and prefixes.
     * 
     * @param doc
     */
    @Override
    public void setNameSpaces() {

        setUri(EInvoiceNS.CAC, "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2");
        setUri(EInvoiceNS.CBC, "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2");

        setPrefix(EInvoiceNS.CAC, "cac");
        setPrefix(EInvoiceNS.CBC, "cbc");

        // parse the UBL namespaces
        NamedNodeMap defAttributes = getRoot().getAttributes();
        for (int j = 0; j < defAttributes.getLength(); j++) {
            Node node = defAttributes.item(j);

            if (getPrefix(EInvoiceNS.CBC).equals(node.getLocalName())
                    && !getUri(EInvoiceNS.CBC).equals(node.getNodeValue())) {
                logger.fine("...set CBC namespace URI: " + node.getNodeValue());
                setUri(EInvoiceNS.CBC, node.getNodeValue());
            }
            if (getPrefix(EInvoiceNS.CAC).equals(node.getLocalName())
                    && !getUri(EInvoiceNS.CAC).equals(node.getNodeValue())) {
                logger.fine("...set CAC namespace URI: " + node.getNodeValue());
                setUri(EInvoiceNS.CAC, node.getNodeValue());
            }

        }

    }

    /**
     * This method instantiates a new eInvoice model based on a given
     * org.w3c.dom.Document. The method parses the namespaces.
     * <p>
     * 
     * 
     * 
     */
    @Override
    public void parseContent() {
        // Load e-invoice standard data
        loadDocumentCoreData();

    }

    /**
     * This method parses the xml content and builds the model.
     * 
     */
    private void loadDocumentCoreData() {
        Element element = null;
        // cbc:ID
        element = findChildNodeByName(getRoot(), EInvoiceNS.CBC, "ID");
        if (element != null) {
            id = element.getTextContent();
        }

        // read Date time
        element = findChildNodeByName(getRoot(), EInvoiceNS.CBC, "IssueDate");
        if (element != null) {
            String dateStr = element.getTextContent();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            issueDateTime = LocalDate.parse(dateStr, formatter);
        }

        Element accountingSupplierPartyElement = findChildNodeByName(getRoot(), EInvoiceNS.CAC,
                "AccountingSupplierParty");
        if (accountingSupplierPartyElement != null) {
            tradeParties.add(parseTradeParty(accountingSupplierPartyElement, "seller"));
        }

        parseTotal();

        // // read Total amount
        // element = findChildNodeByName(supplyChainTradeTransaction, EInvoiceNS.RAM,
        // "ApplicableHeaderTradeSettlement");
        // if (element != null) {
        // Element tradeSettlementElement = findChildNodeByName(element, EInvoiceNS.RAM,
        // "SpecifiedTradeSettlementHeaderMonetarySummation");
        // if (tradeSettlementElement != null) {
        // Element child = findChildNodeByName(tradeSettlementElement, EInvoiceNS.RAM,
        // "GrandTotalAmount");
        // if (child != null) {
        // grandTotalAmount = new BigDecimal(child.getTextContent());
        // }
        // child = findChildNodeByName(tradeSettlementElement, EInvoiceNS.RAM,
        // "TaxTotalAmount");
        // if (child != null) {
        // taxTotalAmount = new BigDecimal(child.getTextContent());
        // }
        // netTotalAmount = grandTotalAmount.subtract(taxTotalAmount).setScale(2,
        // RoundingMode.HALF_UP);

        // }

        // }

    }

    /**
     * Parse monetary totals
     * 
     */
    public void parseTotal() {
        Element monetaryTotalElement = findChildNodeByName(getRoot(), EInvoiceNS.CAC,
                "LegalMonetaryTotal");
        if (monetaryTotalElement != null) {

            Element child = null;
            child = findChildNodeByName(monetaryTotalElement, EInvoiceNS.CBC,
                    "TaxInclusiveAmount");
            if (child != null) {
                grandTotalAmount = new BigDecimal(child.getTextContent()).setScale(2, RoundingMode.HALF_UP);
            }
            // net
            child = findChildNodeByName(monetaryTotalElement, EInvoiceNS.CBC,
                    "LineExtensionAmount");
            if (child != null) {
                netTotalAmount = new BigDecimal(child.getTextContent()).setScale(2, RoundingMode.HALF_UP);
            }
            // tax
            taxTotalAmount = grandTotalAmount.subtract(netTotalAmount).setScale(2, RoundingMode.HALF_UP);

        }

    }

    /**
     * Parse a TradeParty element
     * 
     * @param tradePartyElement
     * @param type
     * @return
     */
    public TradeParty parseTradeParty(Element tradePartyElement, String type) {
        TradeParty tradeParty = new TradeParty(type);
        Element partyElement = null;

        // Parse name
        partyElement = findChildNodeByName(tradePartyElement, EInvoiceNS.CAC,
                "Party");
        if (partyElement != null) {
            // partyname
            Element element = findChildNodeByName(partyElement, EInvoiceNS.CAC,
                    "PartyName");
            if (element != null) {
                element = findChildNodeByName(element, EInvoiceNS.CBC,
                        "Name");
                if (element != null) {
                    tradeParty.setName(element.getTextContent());
                }
            }

        }

        // Element postalAddress = findChildNodeByName(tradePartyElement,
        // EInvoiceNS.RAM,
        // "PostalTradeAddress");
        // if (postalAddress != null) {
        // partyElement = findChildNodeByName(postalAddress, EInvoiceNS.RAM,
        // "PostcodeCode");
        // if (partyElement != null) {
        // tradeParty.setPostcodeCode(partyElement.getTextContent());
        // }
        // partyElement = findChildNodeByName(postalAddress, EInvoiceNS.RAM,
        // "CityName");
        // if (partyElement != null) {
        // tradeParty.setCityName(partyElement.getTextContent());
        // }
        // partyElement = findChildNodeByName(postalAddress, EInvoiceNS.RAM,
        // "CountryID");
        // if (partyElement != null) {
        // tradeParty.setCountryId(partyElement.getTextContent());
        // }
        // partyElement = findChildNodeByName(postalAddress, EInvoiceNS.RAM,
        // "LineOne");
        // if (partyElement != null) {
        // tradeParty.setStreetAddress(partyElement.getTextContent());
        // }
        // }

        // Element specifiedTaxRegistration = findChildNodeByName(tradePartyElement,
        // EInvoiceNS.RAM,
        // "SpecifiedTaxRegistration");
        // if (specifiedTaxRegistration != null) {
        // partyElement = findChildNodeByName(specifiedTaxRegistration, EInvoiceNS.RAM,
        // "ID");
        // if (partyElement != null) {
        // tradeParty.setVatNumber(partyElement.getTextContent());
        // }
        // }
        return tradeParty;
    }

}
