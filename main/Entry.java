package main;

import java.util.*;

//Entries contain a quantity, price, and an optional map of categorizing tags
class Entry {
    private float quantity;
    private boolean updatePrice;
    private String ticker;
    private float price;
    private Currency currency;
    private Map<String, String> tagMap;

    public Entry(String ticker, float quantity, boolean updatePrice, float price, Currency currency){
        this.ticker = ticker;
        this.quantity = quantity;
        this.updatePrice = updatePrice;
        this.price = price;
        this.currency = currency;
        this.tagMap = new HashMap<>(32);
    }

    //Opposite of toString()
    public static Entry fromString(String line){
        Entry retVal;
        try{
            String[] splitLine = line.split(",");
            retVal = new Entry(splitLine[0], Float.parseFloat(splitLine[1]), Db.readBool(splitLine[2]), Float.parseFloat(splitLine[3]), Currency.valueOf(splitLine[4]));
            for(int i = 5; i < splitLine.length; i += 2){
                retVal.addValue(splitLine[i], splitLine[i+1]);
            }
        } catch(Exception unused){//Something went wrong while parsing the string, don't complete the construction
            retVal = null;
        }

        return retVal;
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
        return Float.toString(quantity);
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

    public Currency getCurrency(){
        return currency;
    }

    public Set<Map.Entry<String, String>> getIterable(){
        return tagMap.entrySet();
    }

    //The line that gets saved in the file
    @Override
    public String toString(){
        String essentials = String.join(",", ticker, Float.toString(quantity), Db.writeBool(updatePrice), Float.toString(price), currency.toString());
        String tags = System.lineSeparator();
        for(Map.Entry<String, String> i : tagMap.entrySet()){
            tags = "," + i.getKey() + "," + i.getValue() + tags;
        }

        return essentials + tags;
    };

    //The line that gets written to the display
    public String displayLine(){
        String summary = String.join(", ", "Ticker: " + ticker, "Currency: " + getCurrency(), "Quantity: " + Float.toString(quantity), "Price: " + Float.toString(price));
        for(Map.Entry<String, String> i : tagMap.entrySet()){
            summary += ", " + i.getKey() + ": " + i.getValue();
        }
       return summary;
    };

}
