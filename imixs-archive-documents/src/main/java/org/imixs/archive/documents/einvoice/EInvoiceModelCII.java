package org.imixs.archive.documents.einvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;

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

    protected Element exchangedDocumentContext;
    protected Element exchangedDocument;
    protected Element supplyChainTradeTransaction;
    protected Element applicableHeaderTradeSettlement;
    protected Element specifiedTradeSettlementHeaderMonetarySummation;

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

        // Parse standard tags...
        exchangedDocumentContext = findOrCreateChildNode(getRoot(), EInvoiceNS.RSM,
                "ExchangedDocumentContext");
        exchangedDocument = findOrCreateChildNode(getRoot(), EInvoiceNS.RSM, "ExchangedDocument");
        supplyChainTradeTransaction = findOrCreateChildNode(getRoot(), EInvoiceNS.RSM,
                "SupplyChainTradeTransaction");

        applicableHeaderTradeSettlement = findOrCreateChildNode(supplyChainTradeTransaction, EInvoiceNS.RAM,
                "ApplicableHeaderTradeSettlement");
        specifiedTradeSettlementHeaderMonetarySummation = findChildNode(applicableHeaderTradeSettlement,
                EInvoiceNS.RAM,
                "SpecifiedTradeSettlementHeaderMonetarySummation");

        // Load e-invoice standard data
        loadDocumentCoreData();

    }

    private void loadDocumentCoreData() {
        Element element = null;

        // read invoice number
        element = findChildNode(exchangedDocument, EInvoiceNS.RAM, "ID");
        if (element != null) {
            id = element.getTextContent();
        }

        // read Date time
        element = findChildNode(exchangedDocument, EInvoiceNS.RAM, "IssueDateTime");
        if (element != null) {
            Element dateTimeElement = findChildNode(element, EInvoiceNS.UDT, "DateTimeString");
            if (dateTimeElement != null) {
                String dateStr = dateTimeElement.getTextContent();
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                    issueDateTime = LocalDate.parse(dateStr, formatter);
                } catch (DateTimeParseException e) {
                    // not parsable
                }
            }
        }

        // read Total amount

        Element child = findChildNode(specifiedTradeSettlementHeaderMonetarySummation, EInvoiceNS.RAM,
                "GrandTotalAmount");
        if (child != null) {
            grandTotalAmount = new BigDecimal(child.getTextContent());
        }
        child = findChildNode(specifiedTradeSettlementHeaderMonetarySummation, EInvoiceNS.RAM, "TaxTotalAmount");
        if (child != null) {
            taxTotalAmount = new BigDecimal(child.getTextContent());
        }
        netTotalAmount = grandTotalAmount.subtract(taxTotalAmount).setScale(2, RoundingMode.HALF_UP);

        // due date
        Element specifiedTradePaymentTermsElement = findChildNode(element, EInvoiceNS.RAM,
                "SpecifiedTradePaymentTerms");
        if (specifiedTradePaymentTermsElement != null) {
            Element dateTimeElement = findChildNode(specifiedTradePaymentTermsElement, EInvoiceNS.RAM,
                    "DueDateDateTime");
            if (dateTimeElement != null) {
                Element dateTimeElementString = findChildNode(dateTimeElement, EInvoiceNS.UDT,
                        "DateTimeString");
                if (dateTimeElementString != null) {
                    String dateStr = dateTimeElementString.getTextContent();
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                        dueDateTime = LocalDate.parse(dateStr, formatter);
                    } catch (DateTimeParseException e) {
                        // not parsable
                    }
                }
            }
        }

        // read ApplicableHeaderTradeAgreement - buyerReference
        element = findChildNode(supplyChainTradeTransaction, EInvoiceNS.RAM, "ApplicableHeaderTradeAgreement");
        if (element != null) {
            Element buyerReferenceElement = findChildNode(element, EInvoiceNS.RAM,
                    "BuyerReference");
            if (buyerReferenceElement != null) {
                buyerReference = buyerReferenceElement.getTextContent();
            }
            Element tradePartyElement = findChildNode(element, EInvoiceNS.RAM,
                    "SellerTradeParty");
            if (tradePartyElement != null) {
                tradeParties.add(parseTradeParty(tradePartyElement, "seller"));
            }
            tradePartyElement = findChildNode(element, EInvoiceNS.RAM,
                    "BuyerTradeParty");
            if (tradePartyElement != null) {
                tradeParties.add(parseTradeParty(tradePartyElement, "buyer"));
            }
        }

        // read ShipToTradeParty from ApplicableHeaderTradeDelivery
        element = findChildNode(supplyChainTradeTransaction, EInvoiceNS.RAM, "ApplicableHeaderTradeDelivery");
        if (element != null) {
            Element tradePartyElement = findChildNode(element, EInvoiceNS.RAM, "ShipToTradeParty");
            if (tradePartyElement != null) {
                tradeParties.add(parseTradeParty(tradePartyElement, "ship_to"));
            }
        }

        // read line items...
        parseTradeLineItems();

    }

    public TradeParty parseTradeParty(Element tradePartyElement, String type) {
        TradeParty tradeParty = new TradeParty(type);
        Element element = null;

        // Parse name
        element = findChildNode(tradePartyElement, EInvoiceNS.RAM,
                "Name");
        if (element != null) {
            tradeParty.setName(element.getTextContent());
        }

        Element postalAddress = findChildNode(tradePartyElement, EInvoiceNS.RAM,
                "PostalTradeAddress");
        if (postalAddress != null) {
            element = findChildNode(postalAddress, EInvoiceNS.RAM,
                    "PostcodeCode");
            if (element != null) {
                tradeParty.setPostcodeCode(element.getTextContent());
            }
            element = findChildNode(postalAddress, EInvoiceNS.RAM,
                    "CityName");
            if (element != null) {
                tradeParty.setCityName(element.getTextContent());
            }
            element = findChildNode(postalAddress, EInvoiceNS.RAM,
                    "CountryID");
            if (element != null) {
                tradeParty.setCountryId(element.getTextContent());
            }
            element = findChildNode(postalAddress, EInvoiceNS.RAM,
                    "LineOne");
            if (element != null) {
                tradeParty.setStreetAddress(element.getTextContent());
            }
        }

        Element specifiedTaxRegistration = findChildNode(tradePartyElement, EInvoiceNS.RAM,
                "SpecifiedTaxRegistration");
        if (specifiedTaxRegistration != null) {
            element = findChildNode(specifiedTaxRegistration, EInvoiceNS.RAM,
                    "ID");
            if (element != null) {
                tradeParty.setVatNumber(element.getTextContent());
            }
        }
        return tradeParty;
    }

    /**
     * Parse the trade line items and return a list of items collected
     * 
     * @return
     */
    public Set<TradeLineItem> parseTradeLineItems() {
        Set<TradeLineItem> items = new LinkedHashSet<>();

        Set<Element> lineItems = findChildNodesByName(supplyChainTradeTransaction, EInvoiceNS.RAM,
                "IncludedSupplyChainTradeLineItem");

        for (Element lineItem : lineItems) {
            // Get Line ID
            Element docLine = findChildNode(lineItem, EInvoiceNS.RAM, "AssociatedDocumentLineDocument");
            if (docLine == null)
                continue;

            Element idElement = findChildNode(docLine, EInvoiceNS.RAM, "LineID");
            if (idElement == null)
                continue;

            TradeLineItem item = new TradeLineItem(idElement.getTextContent());

            // Product details
            Element product = findChildNode(lineItem, EInvoiceNS.RAM, "SpecifiedTradeProduct");
            if (product != null) {
                Element nameElement = findChildNode(product, EInvoiceNS.RAM, "Name");
                if (nameElement != null) {
                    item.setName(nameElement.getTextContent());
                }
                Element descElement = findChildNode(product, EInvoiceNS.RAM, "Description");
                if (descElement != null) {
                    item.setDescription(descElement.getTextContent());
                }
            }

            // Price info
            Element agreement = findChildNode(lineItem, EInvoiceNS.RAM, "SpecifiedLineTradeAgreement");
            if (agreement != null) {
                Element grossPrice = findChildNode(agreement, EInvoiceNS.RAM, "GrossPriceProductTradePrice");
                if (grossPrice != null) {
                    Element amount = findChildNode(grossPrice, EInvoiceNS.RAM, "ChargeAmount");
                    if (amount != null) {
                        item.setGrossPrice(Double.parseDouble(amount.getTextContent()));
                    }
                }

                Element netPrice = findChildNode(agreement, EInvoiceNS.RAM, "NetPriceProductTradePrice");
                if (netPrice != null) {
                    Element amount = findChildNode(netPrice, EInvoiceNS.RAM, "ChargeAmount");
                    if (amount != null) {
                        item.setNetPrice(Double.parseDouble(amount.getTextContent()));
                    }
                }
            }

            // Quantity
            Element delivery = findChildNode(lineItem, EInvoiceNS.RAM, "SpecifiedLineTradeDelivery");
            if (delivery != null) {
                Element quantity = findChildNode(delivery, EInvoiceNS.RAM, "BilledQuantity");
                if (quantity != null) {
                    item.setQuantity(Double.parseDouble(quantity.getTextContent()));
                }
            }

            // VAT and total
            Element settlement = findChildNode(lineItem, EInvoiceNS.RAM, "SpecifiedLineTradeSettlement");
            if (settlement != null) {
                Element tax = findChildNode(settlement, EInvoiceNS.RAM, "ApplicableTradeTax");
                if (tax != null) {
                    Element rate = findChildNode(tax, EInvoiceNS.RAM, "RateApplicablePercent");
                    if (rate != null) {
                        item.setVat(Double.parseDouble(rate.getTextContent()));
                    }
                }

                Element summation = findChildNode(settlement, EInvoiceNS.RAM,
                        "SpecifiedTradeSettlementLineMonetarySummation");
                if (summation != null) {
                    Element total = findChildNode(summation, EInvoiceNS.RAM, "LineTotalAmount");
                    if (total != null) {
                        item.setTotal(Double.parseDouble(total.getTextContent()));
                    }
                }
            }

            items.add(item);
        }

        return items;
    }

    /**
     * Update Invoice Number
     */
    @Override
    public void setId(String value) {
        super.setId(value);
        Element element = findOrCreateChildNode(exchangedDocument, EInvoiceNS.RAM, "ID");
        element.setTextContent(value);
    }

    /**
     * Update Invoice date
     */
    @Override
    public void setIssueDateTime(LocalDate value) {
        super.setIssueDateTime(value);
        Element element = findOrCreateChildNode(exchangedDocument, EInvoiceNS.RAM,
                "IssueDateTime");
        Element dateTimeElement = findOrCreateChildNode(element, EInvoiceNS.UDT,
                "DateTimeString");
        dateTimeElement.setAttribute("format", "102");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        dateTimeElement.setTextContent(formatter.format(value));
    }

    @Override
    public void setNetTotalAmount(BigDecimal value) {
        super.setNetTotalAmount(value);
        // Update LineTotalAmount
        Element lineTotalElement = findChildNode(specifiedTradeSettlementHeaderMonetarySummation, EInvoiceNS.RAM,
                "LineTotalAmount");
        if (lineTotalElement != null) {
            lineTotalElement.setTextContent(value.toPlainString());
            // Update auch den internen Wert
            netTotalAmount = value;
        }
        // Update TaxBasisTotalAmount
        Element taxBasisElement = findChildNode(specifiedTradeSettlementHeaderMonetarySummation, EInvoiceNS.RAM,
                "TaxBasisTotalAmount");
        if (taxBasisElement != null) {
            taxBasisElement.setTextContent(value.toPlainString());
        }

        // Update ApplicationTradeTax
        Element applicableTradeTax = findOrCreateChildNode(applicableHeaderTradeSettlement,
                EInvoiceNS.RAM, "ApplicableTradeTax");
        updateElementValue(applicableTradeTax, EInvoiceNS.RAM, "BasisAmount", value.toPlainString());

    }

    @Override
    public void setGrandTotalAmount(BigDecimal value) {
        super.setGrandTotalAmount(value);
        // Update GrandTotalAmount
        Element amountElement = findChildNode(specifiedTradeSettlementHeaderMonetarySummation, EInvoiceNS.RAM,
                "GrandTotalAmount");
        if (amountElement != null) {
            amountElement.setTextContent(value.toPlainString());
        }
        amountElement = findChildNode(specifiedTradeSettlementHeaderMonetarySummation, EInvoiceNS.RAM,
                "DuePayableAmount");
        if (amountElement != null) {
            amountElement.setTextContent(value.toPlainString());
        }

    }

    /**
     * <ram:TaxTotalAmount currencyID="EUR">0.00</ram:TaxTotalAmount>
     */
    @Override
    public void setTaxTotalAmount(BigDecimal value) {
        super.setTaxTotalAmount(value);
        Element amountElement = findOrCreateChildNode(specifiedTradeSettlementHeaderMonetarySummation,
                EInvoiceNS.RAM,
                "TaxTotalAmount");
        amountElement.setTextContent(value.toPlainString());
        amountElement.setAttribute("currencyID", "EUR");

        // Update ApplicableTradeTax/CalculatedAmount
        Element applicableTradeTax = findOrCreateChildNode(applicableHeaderTradeSettlement,
                EInvoiceNS.RAM, "ApplicableTradeTax");
        updateElementValue(applicableTradeTax, EInvoiceNS.RAM, "CalculatedAmount", value.toPlainString());

    }

    /**
     * Update Duedate
     */
    @Override
    public void setDueDateTime(LocalDate value) {
        super.setDueDateTime(value);
        Element specifiedTradePaymentTermsElement = findOrCreateChildNode(applicableHeaderTradeSettlement,
                EInvoiceNS.RAM,
                "SpecifiedTradePaymentTerms");
        if (specifiedTradePaymentTermsElement != null) {
            Element dueDateTimeElement = findOrCreateChildNode(specifiedTradePaymentTermsElement,
                    EInvoiceNS.RAM,
                    "DueDateDateTime");
            Element dateTimeElement = findOrCreateChildNode(dueDateTimeElement, EInvoiceNS.UDT,
                    "DateTimeString");
            dateTimeElement.setAttribute("format", "102");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            dateTimeElement.setTextContent(formatter.format(value));
        }

    }

    /**
     * Updates or creates a trade party in the model and XML structure
     * 
     * @param newParty the trade party to be set
     */
    /**
     * Updates or creates a trade party in the model and XML structure
     * 
     * @param newParty the trade party to be set
     */
    @Override
    public void setTradeParty(TradeParty newParty) {
        if (newParty == null) {
            return;
        }

        // First update the model
        super.setTradeParty(newParty);

        Element parentElement;
        String elementName;

        // Determine parent element and party element name based on type
        if ("ship_to".equals(newParty.getType())) {
            parentElement = findChildNode(supplyChainTradeTransaction, EInvoiceNS.RAM,
                    "ApplicableHeaderTradeDelivery");
            elementName = "ShipToTradeParty";
        } else {
            parentElement = findChildNode(supplyChainTradeTransaction, EInvoiceNS.RAM,
                    "ApplicableHeaderTradeAgreement");
            elementName = newParty.getType().equals("seller") ? "SellerTradeParty" : "BuyerTradeParty";
        }

        if (parentElement != null) {
            Element tradePartyElement = findChildNode(parentElement, EInvoiceNS.RAM, elementName);

            // Create element if it doesn't exist
            if (tradePartyElement == null) {
                tradePartyElement = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + elementName);
                parentElement.appendChild(tradePartyElement);
            }

            // Update Name
            updateElementValue(tradePartyElement, EInvoiceNS.RAM, "Name", newParty.getName());

            // Update PostalTradeAddress
            Element postalAddress = findChildNode(tradePartyElement, EInvoiceNS.RAM, "PostalTradeAddress");
            if (postalAddress == null) {
                postalAddress = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "PostalTradeAddress");
                tradePartyElement.appendChild(postalAddress);
            }

            // Update address details

            // <ram:PostalTradeAddress>
            // <ram:PostcodeCode>12345</ram:PostcodeCode>
            // <ram:LineOne>Musterstraße 123</ram:LineOne>
            // <ram:CityName>Muster</ram:CityName>
            // <ram:CountryID>DE</ram:CountryID>
            // </ram:PostalTradeAddress>
            updateElementValue(postalAddress, EInvoiceNS.RAM, "PostcodeCode", newParty.getPostcodeCode());
            updateElementValue(postalAddress, EInvoiceNS.RAM, "LineOne", newParty.getStreetAddress());
            updateElementValue(postalAddress, EInvoiceNS.RAM, "CityName", newParty.getCityName());
            updateElementValue(postalAddress, EInvoiceNS.RAM, "CountryID", newParty.getCountryId());

            // Update VAT registration if available
            if (newParty.getVatNumber() != null && !newParty.getVatNumber().isEmpty()) {
                Element taxRegistration = findChildNode(tradePartyElement, EInvoiceNS.RAM,
                        "SpecifiedTaxRegistration");
                if (taxRegistration == null) {
                    taxRegistration = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "SpecifiedTaxRegistration");
                    tradePartyElement.appendChild(taxRegistration);
                }
                updateElementValue(taxRegistration, EInvoiceNS.RAM, "ID", newParty.getVatNumber());
            }
        }
    }

    /**
     * Adds a new TradeLineItem into the XML tree.
     * 
     * @param item
     */
    @Override
    public void setTradeLineItem(TradeLineItem item) {
        if (item == null) {
            return;
        }

        super.setTradeLineItem(item);

        // create main tags...
        Element lineItem = createChildNode(supplyChainTradeTransaction, EInvoiceNS.RAM,
                "IncludedSupplyChainTradeLineItem");
        Element associatedDocumentLineDocument = createChildNode(lineItem, EInvoiceNS.RAM,
                "AssociatedDocumentLineDocument");
        Element product = createChildNode(lineItem, EInvoiceNS.RAM, "SpecifiedTradeProduct");
        Element agreement = createChildNode(lineItem, EInvoiceNS.RAM, "SpecifiedLineTradeAgreement");
        Element delivery = createChildNode(lineItem, EInvoiceNS.RAM, "SpecifiedLineTradeDelivery");
        Element settlement = createChildNode(lineItem, EInvoiceNS.RAM,
                "SpecifiedLineTradeSettlement");

        // Document Line with ID
        updateElementValue(associatedDocumentLineDocument, EInvoiceNS.RAM, "LineID", item.getId());

        // Product details
        if (item.getName() != null) {
            updateElementValue(product, EInvoiceNS.RAM, "Name", item.getName());
        }
        if (item.getDescription() != null) {
            updateElementValue(product, EInvoiceNS.RAM, "Description", item.getDescription());
        }

        // Trade Agreement (Prices)
        Element grossPrice = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "GrossPriceProductTradePrice");
        Element grossAmount = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "ChargeAmount");
        grossAmount.setTextContent(String.valueOf(item.getGrossPrice()));
        grossPrice.appendChild(grossAmount);
        agreement.appendChild(grossPrice);
        Element netPrice = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "NetPriceProductTradePrice");
        Element netAmount = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "ChargeAmount");
        netAmount.setTextContent(String.valueOf(item.getNetPrice()));
        netPrice.appendChild(netAmount);
        agreement.appendChild(netPrice);

        // Trade Delivery (Quantity)
        Element quantity = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "BilledQuantity");
        quantity.setAttribute("unitCode", "C62"); // Standard unit code
        quantity.setTextContent(String.valueOf(item.getQuantity()));
        delivery.appendChild(quantity);

        // Trade Settlement (VAT and Total)
        Element tax = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "ApplicableTradeTax");
        Element typeCode = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "TypeCode");
        typeCode.setTextContent("VAT");
        Element categoryCode = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "CategoryCode");
        categoryCode.setTextContent("S");
        Element rate = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "RateApplicablePercent");
        rate.setTextContent(String.valueOf(item.getVat()));
        tax.appendChild(typeCode);
        tax.appendChild(categoryCode);
        tax.appendChild(rate);
        settlement.appendChild(tax);

        // Update summary
        Element monetarySummation = createChildNode(settlement, EInvoiceNS.RAM,
                "SpecifiedTradeSettlementLineMonetarySummation");
        Element totalAmount = createChildNode(monetarySummation, EInvoiceNS.RAM,
                "LineTotalAmount");
        totalAmount.setTextContent(String.valueOf(item.getTotal()));

    }

}
