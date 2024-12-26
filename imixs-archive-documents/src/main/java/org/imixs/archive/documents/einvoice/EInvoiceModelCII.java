package org.imixs.archive.documents.einvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

        // read ShipToTradeParty from ApplicableHeaderTradeDelivery
        element = findChildNodeByName(supplyChainTradeTransaction, EInvoiceNS.RAM, "ApplicableHeaderTradeDelivery");
        if (element != null) {
            Element tradePartyElement = findChildNodeByName(element, EInvoiceNS.RAM, "ShipToTradeParty");
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
            Element docLine = findChildNodeByName(lineItem, EInvoiceNS.RAM, "AssociatedDocumentLineDocument");
            if (docLine == null)
                continue;

            Element idElement = findChildNodeByName(docLine, EInvoiceNS.RAM, "LineID");
            if (idElement == null)
                continue;

            TradeLineItem item = new TradeLineItem(idElement.getTextContent());

            // Product details
            Element product = findChildNodeByName(lineItem, EInvoiceNS.RAM, "SpecifiedTradeProduct");
            if (product != null) {
                Element nameElement = findChildNodeByName(product, EInvoiceNS.RAM, "Name");
                if (nameElement != null) {
                    item.setName(nameElement.getTextContent());
                }
                Element descElement = findChildNodeByName(product, EInvoiceNS.RAM, "Description");
                if (descElement != null) {
                    item.setDescription(descElement.getTextContent());
                }
            }

            // Price info
            Element agreement = findChildNodeByName(lineItem, EInvoiceNS.RAM, "SpecifiedLineTradeAgreement");
            if (agreement != null) {
                Element grossPrice = findChildNodeByName(agreement, EInvoiceNS.RAM, "GrossPriceProductTradePrice");
                if (grossPrice != null) {
                    Element amount = findChildNodeByName(grossPrice, EInvoiceNS.RAM, "ChargeAmount");
                    if (amount != null) {
                        item.setGrossPrice(Double.parseDouble(amount.getTextContent()));
                    }
                }

                Element netPrice = findChildNodeByName(agreement, EInvoiceNS.RAM, "NetPriceProductTradePrice");
                if (netPrice != null) {
                    Element amount = findChildNodeByName(netPrice, EInvoiceNS.RAM, "ChargeAmount");
                    if (amount != null) {
                        item.setNetPrice(Double.parseDouble(amount.getTextContent()));
                    }
                }
            }

            // Quantity
            Element delivery = findChildNodeByName(lineItem, EInvoiceNS.RAM, "SpecifiedLineTradeDelivery");
            if (delivery != null) {
                Element quantity = findChildNodeByName(delivery, EInvoiceNS.RAM, "BilledQuantity");
                if (quantity != null) {
                    item.setQuantity(Double.parseDouble(quantity.getTextContent()));
                }
            }

            // VAT and total
            Element settlement = findChildNodeByName(lineItem, EInvoiceNS.RAM, "SpecifiedLineTradeSettlement");
            if (settlement != null) {
                Element tax = findChildNodeByName(settlement, EInvoiceNS.RAM, "ApplicableTradeTax");
                if (tax != null) {
                    Element rate = findChildNodeByName(tax, EInvoiceNS.RAM, "RateApplicablePercent");
                    if (rate != null) {
                        item.setVat(Double.parseDouble(rate.getTextContent()));
                    }
                }

                Element summation = findChildNodeByName(settlement, EInvoiceNS.RAM,
                        "SpecifiedTradeSettlementLineMonetarySummation");
                if (summation != null) {
                    Element total = findChildNodeByName(summation, EInvoiceNS.RAM, "LineTotalAmount");
                    if (total != null) {
                        item.setTotal(Double.parseDouble(total.getTextContent()));
                    }
                }
            }

            items.add(item);
        }

        return items;
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
            parentElement = findChildNodeByName(supplyChainTradeTransaction, EInvoiceNS.RAM,
                    "ApplicableHeaderTradeDelivery");
            elementName = "ShipToTradeParty";
        } else {
            parentElement = findChildNodeByName(supplyChainTradeTransaction, EInvoiceNS.RAM,
                    "ApplicableHeaderTradeAgreement");
            elementName = newParty.getType().equals("seller") ? "SellerTradeParty" : "BuyerTradeParty";
        }

        if (parentElement != null) {
            Element tradePartyElement = findChildNodeByName(parentElement, EInvoiceNS.RAM, elementName);

            // Create element if it doesn't exist
            if (tradePartyElement == null) {
                tradePartyElement = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + elementName);
                parentElement.appendChild(tradePartyElement);
            }

            // Update Name
            updateElementValue(tradePartyElement, "Name", newParty.getName());

            // Update PostalTradeAddress
            Element postalAddress = findChildNodeByName(tradePartyElement, EInvoiceNS.RAM, "PostalTradeAddress");
            if (postalAddress == null) {
                postalAddress = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "PostalTradeAddress");
                tradePartyElement.appendChild(postalAddress);
            }

            // Update address details
            updateElementValue(postalAddress, "PostcodeCode", newParty.getPostcodeCode());
            updateElementValue(postalAddress, "CityName", newParty.getCityName());
            updateElementValue(postalAddress, "CountryID", newParty.getCountryId());
            updateElementValue(postalAddress, "LineOne", newParty.getStreetAddress());

            // Update VAT registration if available
            if (newParty.getVatNumber() != null && !newParty.getVatNumber().isEmpty()) {
                Element taxRegistration = findChildNodeByName(tradePartyElement, EInvoiceNS.RAM,
                        "SpecifiedTaxRegistration");
                if (taxRegistration == null) {
                    taxRegistration = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "SpecifiedTaxRegistration");
                    tradePartyElement.appendChild(taxRegistration);
                }
                updateElementValue(taxRegistration, "ID", newParty.getVatNumber());
            }
        }
    }

    /**
     * Adds a new TradeLineItem into the XML tree.
     * 
     * @param item
     */
    public void addTradeLineItem(TradeLineItem item) {
        Element lineItem = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "IncludedSupplyChainTradeLineItem");

        // Document Line with ID
        Element docLine = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "AssociatedDocumentLineDocument");
        Element lineId = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "LineID");
        lineId.setTextContent(item.getId());
        docLine.appendChild(lineId);
        lineItem.appendChild(docLine);

        // Product details
        Element product = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "SpecifiedTradeProduct");
        if (item.getName() != null) {
            Element name = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "Name");
            name.setTextContent(item.getName());
            product.appendChild(name);
        }
        if (item.getDescription() != null) {
            Element desc = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "Description");
            desc.setTextContent(item.getDescription());
            product.appendChild(desc);
        }
        lineItem.appendChild(product);

        // Trade Agreement (Prices)
        Element agreement = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "SpecifiedLineTradeAgreement");

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

        lineItem.appendChild(agreement);

        // Trade Delivery (Quantity)
        Element delivery = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "SpecifiedLineTradeDelivery");
        Element quantity = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "BilledQuantity");
        quantity.setAttribute("unitCode", "C62"); // Standard unit code
        quantity.setTextContent(String.valueOf(item.getQuantity()));
        delivery.appendChild(quantity);
        lineItem.appendChild(delivery);

        // Trade Settlement (VAT and Total)
        Element settlement = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "SpecifiedLineTradeSettlement");

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

        Element summation = getDoc()
                .createElement(getPrefix(EInvoiceNS.RAM) + "SpecifiedTradeSettlementLineMonetarySummation");
        Element total = getDoc().createElement(getPrefix(EInvoiceNS.RAM) + "LineTotalAmount");
        total.setTextContent(String.valueOf(item.getTotal()));
        summation.appendChild(total);
        settlement.appendChild(summation);

        lineItem.appendChild(settlement);

        // Add to supplyChainTradeTransaction
        supplyChainTradeTransaction.appendChild(lineItem);
    }

}
