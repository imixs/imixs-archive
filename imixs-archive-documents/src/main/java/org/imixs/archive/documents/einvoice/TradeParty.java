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
        return "TradeParty{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", postcodeCode='" + postcodeCode + '\'' +
                ", streetAddress='" + streetAddress + '\'' +
                ", cityName='" + cityName + '\'' +
                ", countryId='" + countryId + '\'' +
                ", vatNumber='" + vatNumber + '\'' +
                '}';
    }

}
