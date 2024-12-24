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
public class EInvoiceModelCII extends EInvoiceModel {

    private Element exchangedDocumentContext;
    private Element exchangedDocument;
    private Element supplyChainTradeTransaction;

    public EInvoiceModelCII(Document doc) {
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
        // setUri(EInvoiceNS.A,
        // "urn:un:unece:uncefact:data:standard:QualifiedDataType:100");
        setUri(EInvoiceNS.RSM, "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100");
        setUri(EInvoiceNS.QDT, "urn:un:unece:uncefact:data:standard:QualifiedDataType:10");
        setUri(EInvoiceNS.RAM, "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100");
        setUri(EInvoiceNS.UDT, "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100");

        // setPrefix(EInvoiceNS.A, "a");
        setPrefix(EInvoiceNS.RSM, "rsm");
        setPrefix(EInvoiceNS.QDT, "qdt");
        setPrefix(EInvoiceNS.RAM, "ram");
        setPrefix(EInvoiceNS.UDT, "udt");

        // parse the CII namespaces
        NamedNodeMap defAttributes = getRoot().getAttributes();
        for (int j = 0; j < defAttributes.getLength(); j++) {
            Node node = defAttributes.item(j);

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

    }

    /**
     * This method parses the xml content and builds the model.
     * 
     */
    @Override
    public void parseContent() {

        exchangedDocumentContext = findChildNodeByName(getRoot(), EInvoiceNS.RSM,
                "ExchangedDocumentContext");
        exchangedDocument = findChildNodeByName(getRoot(), EInvoiceNS.RSM, "ExchangedDocument");
        supplyChainTradeTransaction = findChildNodeByName(getRoot(), EInvoiceNS.RSM,
                "SupplyChainTradeTransaction");

        // Load e-invoice standard data
        loadDocumentCoreData();

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

            // due date
            Element specifiedTradePaymentTermsElement = findChildNodeByName(element, EInvoiceNS.RAM,
                    "SpecifiedTradePaymentTerms");
            if (specifiedTradePaymentTermsElement != null) {
                Element dateTimeElement = findChildNodeByName(specifiedTradePaymentTermsElement, EInvoiceNS.RAM,
                        "DueDateDateTime");
                if (dateTimeElement != null) {
                    Element dateTimeElementString = findChildNodeByName(dateTimeElement, EInvoiceNS.UDT,
                            "DateTimeString");
                    if (dateTimeElementString != null) {
                        String dateStr = dateTimeElementString.getTextContent();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                        dueDateTime = LocalDate.parse(dateStr, formatter);
                    }
                }
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

    @Override
    public void setNetTotalAmount(BigDecimal value) {
        // Finde das SpecifiedTradeSettlementHeaderMonetarySummation Element
        Element element = findChildNodeByName(supplyChainTradeTransaction, EInvoiceNS.RAM,
                "ApplicableHeaderTradeSettlement");
        if (element != null) {
            Element tradeSettlementElement = findChildNodeByName(element, EInvoiceNS.RAM,
                    "SpecifiedTradeSettlementHeaderMonetarySummation");
            if (tradeSettlementElement != null) {
                // Update LineTotalAmount
                Element lineTotalElement = findChildNodeByName(tradeSettlementElement, EInvoiceNS.RAM,
                        "LineTotalAmount");
                if (lineTotalElement != null) {
                    lineTotalElement.setTextContent(value.toPlainString());
                    // Update auch den internen Wert
                    netTotalAmount = value;
                }
                // Update TaxBasisTotalAmount
                Element taxBasisElement = findChildNodeByName(tradeSettlementElement, EInvoiceNS.RAM,
                        "TaxBasisTotalAmount");
                if (taxBasisElement != null) {
                    taxBasisElement.setTextContent(value.toPlainString());
                }
            }
        }
    }

    @Override
    public void setGrandTotalAmount(BigDecimal value) {
        // Finde das SpecifiedTradeSettlementHeaderMonetarySummation Element
        Element element = findChildNodeByName(supplyChainTradeTransaction, EInvoiceNS.RAM,
                "ApplicableHeaderTradeSettlement");
        if (element != null) {
            Element tradeSettlementElement = findChildNodeByName(element, EInvoiceNS.RAM,
                    "SpecifiedTradeSettlementHeaderMonetarySummation");
            if (tradeSettlementElement != null) {

                // Update GrandTotalAmount
                Element amountElement = findChildNodeByName(tradeSettlementElement, EInvoiceNS.RAM,
                        "GrandTotalAmount");
                if (amountElement != null) {
                    amountElement.setTextContent(value.toPlainString());
                }
                amountElement = findChildNodeByName(tradeSettlementElement, EInvoiceNS.RAM,
                        "DuePayableAmount");
                if (amountElement != null) {
                    amountElement.setTextContent(value.toPlainString());
                }
            }
        }
    }
}
