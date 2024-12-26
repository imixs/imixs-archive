package org.imixs.archive.documents.einvoice;

/**
 * A TradeParty is a container for a TradeParty element
 * 
 * @author rsoika
 *
 */
public class TradeLineItem {

    private String id;
    private String name;
    private String description;
    private double grossPrice;
    private double netPrice;
    private double quantity;
    private double vat;
    private double total;

    public TradeLineItem(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getGrossPrice() {
        return grossPrice;
    }

    public void setGrossPrice(double grossPrice) {
        this.grossPrice = grossPrice;
    }

    public double getNetPrice() {
        return netPrice;
    }

    public void setNetPrice(double netPrice) {
        this.netPrice = netPrice;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public double getVat() {
        return vat;
    }

    public void setVat(double vat) {
        this.vat = vat;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    // toString method for easy debugging
    @Override
    public String toString() {
        return "TradeLineItem{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", grossPrice='" + grossPrice + '\'' +
                ", netPrice='" + netPrice + '\'' +
                ", quantity='" + quantity + '\'' +
                ", vat='" + vat + '\'' +
                ", total='" + total + '\'' +
                '}';
    }

}
