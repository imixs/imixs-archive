package org.imixs.archive.documents.einvoice;

/**
 * A TradeParty is a container for a TradeParty element
 * 
 * @author rsoika
 *
 */
public class TradeParty {

    private String type; // type of trade party
    private String name;
    private String postcodeCode;
    private String streetAddress;
    private String cityName;
    private String countryId;
    private String vatNumber;

    public TradeParty(String type) {
        this.type = type;
    }
    // public TradeParty(Element sellerElement, String type) {
    // this.type = type;
    // // Parse name
    // Element element=sellerElement.getElementsByTagNameNS( EInvoiceNS.RAM.name(),
    // "name");
    // this.name =
    // .getElementsByTagName("ram:Name")
    // .item(0)
    // .getTextContent();

    // // Get PostalTradeAddress element
    // Element postalAddress = (Element) sellerElement
    // .getElementsByTagName("ram:PostalTradeAddress")
    // .item(0);

    // // Parse address details
    // this.postcodeCode = postalAddress
    // .getElementsByTagName("ram:PostcodeCode")
    // .item(0)
    // .getTextContent();

    // this.streetAddress = postalAddress
    // .getElementsByTagName("ram:LineOne")
    // .item(0)
    // .getTextContent();

    // this.cityName = postalAddress
    // .getElementsByTagName("ram:CityName")
    // .item(0)
    // .getTextContent();

    // this.countryId = postalAddress
    // .getElementsByTagName("ram:CountryID")
    // .item(0)
    // .getTextContent();

    // // // Parse VAT number
    // // Element taxRegistration = (Element) sellerElement
    // // .getElementsByTagName("ram:SpecifiedTaxRegistration")
    // // .item(0);

    // // this.vatNumber = taxRegistration
    // // .getElementsByTagName("ram:ID")
    // // .item(0)
    // // .getTextContent();
    // }

    // Getters
    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getPostcodeCode() {
        return postcodeCode;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public String getCityName() {
        return cityName;
    }

    public String getCountryId() {
        return countryId;
    }

    public String getVatNumber() {
        return vatNumber;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPostcodeCode(String postcodeCode) {
        this.postcodeCode = postcodeCode;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public void setCountryId(String countryId) {
        this.countryId = countryId;
    }

    public void setVatNumber(String vatNumber) {
        this.vatNumber = vatNumber;
    }

    // toString method for easy debugging
    @Override
    public String toString() {
        return "SellerTradeParty{" +
                "name='" + name + '\'' +
                ", postcodeCode='" + postcodeCode + '\'' +
                ", streetAddress='" + streetAddress + '\'' +
                ", cityName='" + cityName + '\'' +
                ", countryId='" + countryId + '\'' +
                ", vatNumber='" + vatNumber + '\'' +
                '}';
    }

}
