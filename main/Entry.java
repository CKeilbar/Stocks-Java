package main;

import java.util.*;

//Entries contain a quantity, price, and an optional map of categorizing tags
class Entry {
    private int quantity;
    private boolean updatePrice;
    private String ticker;
    private float price;
    private Map<String, String> tagMap;

    public Entry(String Ticker, int Quantity, boolean UpdatePrice, float Price){
        ticker = Ticker;
        quantity = Quantity;
        updatePrice = UpdatePrice;
        price = Price;
        tagMap = new HashMap(32);
    }

    public float getValue(){
        return price * quantity;
    };

    public void addValue(String key, String value){
        tagMap.put(key, value);
    };

    public String getPrice(){
        return Float.toString(price);
    };

    public void setPrice(float newPrice){
        price = newPrice;
    }

    public boolean getUpdatePrice(){
        return updatePrice;
    }

    public String getQuantity(){
        return Integer.toString(quantity);
    };

    public boolean containsPair(String key, String value){
        return value.equals(tagMap.get(key));
    };

    public String valueForTag(String key){
        return tagMap.getOrDefault(key, "Not classified");
    };

    public String getTicker(){
        return ticker;
    };

    public Set<Map.Entry<String, String>> getIterable(){
        return tagMap.entrySet();
    }

    //The line that gets saved in the file
    public String saveableLine(){
        String essentials = String.join(",", ticker, Integer.toString(quantity), (updatePrice ? "yes" : "no"), Float.toString(price));
        String tags = System.lineSeparator();
        for(Map.Entry<String, String> i : tagMap.entrySet()){
            tags = "," + i.getKey() + "," + i.getValue() + tags;
        }

        return essentials + tags;
    };

    //The line that gets written to the display
    public String displayLine(){
        String summary = String.join(", ", "Ticker: " + ticker, "Quantity: " + Integer.toString(quantity), "Price: " + Float.toString(price));
        for(Map.Entry<String, String> i : tagMap.entrySet()){
            summary += ", " + i.getKey() + ": " + i.getValue();
        }
       return summary;
    };

}
