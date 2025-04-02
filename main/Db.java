package main;

import java.util.*;
import java.io.*;
import java.time.*;
import java.time.format.*;

//Manages the information about the entries and settings
class Db {

    //Save file
    private static final String saveFname = "prevInfo.txt";

    //Saved settings
    private static String apiKey = "";

    private static float rate = 1.0f;
    private static boolean autoRate = false;
    private static Currency graphCurrency = Currency.CAD;

    //Modification times
    private static LocalDateTime priceTime = LocalDateTime.now(); //Default to current, overwritten if previous is found
    private static LocalDateTime dbTime = priceTime;

    //Entries
    private static ArrayList<Entry> entries = new ArrayList<Entry>();
    private static TagTracker tagMap = new TagTracker();

    //Private constructor; this class cannot be instantiated and is purely static
    private Db() {}

    //Read from the filesystem for our settings and saved entries
    static void readDb(){
        try(BufferedReader br = new BufferedReader(new FileReader(saveFname))){
            //First lines contain settings
            apiKey = br.readLine();
            graphCurrency = Currency.valueOf(br.readLine());
            autoRate = readBool(br.readLine());
            rate = Float.parseFloat(br.readLine());

            //Next lines are times
            priceTime = LocalDateTime.parse(br.readLine());
            dbTime = LocalDateTime.parse(br.readLine());

            //Rest of the lines correspond to entries
            String line;
            while((line = br.readLine()) != null){
                Entry currentEntry = Entry.fromString(line);
                if (currentEntry != null)
                    initEntry(currentEntry);
            }
        } catch (Exception unused){
            //Someone modified something manually incorrectly, silently give up
        }
    }

    //Write to the filesystem to store our settings and entries for next time
    static void writeDb(){
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(saveFname))){
            //First lines contain settings
            bw.write(apiKey);
            bw.newLine();
            bw.write(graphCurrency.toString());
            bw.newLine();
            bw.write(writeBool(autoRate));
            bw.newLine();
            bw.write(Float.toString(rate));
            bw.newLine();
            bw.write(priceTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            bw.newLine();
            bw.write(dbTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            bw.newLine();
            for(Entry i : entries){
                bw.write(i.toString());
            }
        } catch (Exception unused){
            //Very bad
        }
    };

    static String writeBool(boolean val){
        return val ? "true" : "false";
    }

    static boolean readBool(String val){
        return "true".equals(val);
    }

    //Remove an entry from the database
    static void removeEntry(Entry entry){
        entries.remove(entry);
        tagMap.removeEntry(entry.getIterable());
        updateDbTime();
    }

    //Insert an entry into the database
    static void createEntry(Entry entry){
        initEntry(entry);
        updateDbTime();
    }

    private static void initEntry(Entry entry){
        entries.add(entry);
        tagMap.addEntry(entry.getIterable());
    }

    private static void updateDbTime(){
        dbTime = LocalDateTime.now();
    }

    static void updatePriceTime(){
        priceTime = LocalDateTime.now();
    }

    //Returns the timestamps formatted as text
    static String datesToLabel(){
        DateTimeFormatter dispTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
        String startString = "Database last modified ";
        String dbString = "";
        String middleString = "; prices last updated ";
        String priceString = "";

        try {
            dbString = dbTime.format(dispTimeFormat);
            priceString = priceTime.format(dispTimeFormat);
        } catch (DateTimeException unused){
            //Use the default empty strings
        }
        return startString + dbString + middleString + priceString;
    }

    //From tag manager
    static String[] getValuesForTag(String tag){
        return tagMap.getValuesForTag(tag);
    };

    //From tag manager
    static String[] getTags(){
        return tagMap.getTags();
    };

    //From tag manager
    static int maxValsForTag(){
        return tagMap.maxValsForTag();
    };

    //Searches through the entries to find the ones satisfying the criteria
    //The inc values MUST be present, and the rem values MUST NOT, this functions as a logical AND
    static Map<String, Float> findGraphables(String axis, ArrayList<String> incVal, ArrayList<String> incTag, ArrayList<String> remVal, ArrayList<String> remTag){
        ArrayList<Entry> clone = new ArrayList<>(entries);

        //Remove all of the entries that don't satisfy the includes
        for(int i = 0; i < incVal.size(); i++){
            for(int j = clone.size()-1; j >= 0; j--){
                if(!clone.get(j).containsPair(incTag.get(i), incVal.get(i)))
                    clone.remove(j);
            }
        }

        //Remove all entries that satisfy the removes
        for(int i = 0; i < remVal.size(); i++){
            for(int j = clone.size()-1; j >= 0; j--){
                if(clone.get(j).containsPair(remTag.get(i), remVal.get(i)))
                    clone.remove(j);
            }
        }

        if (clone.size() == 0)
            return null;

        //Clone now holds only valid values
        Map<String, Float> unsortedMap = new HashMap<>();
        for(Entry i : clone){
            String myTag = "".equals(axis) ? i.getTicker() : i.valueForTag(axis); //If no axis, use ticker as label
            float value = currencyExchange(i.getValue(), i.getCurrency(), graphCurrency);
            unsortedMap.put(myTag, value + unsortedMap.getOrDefault(myTag, 0f));
        }

        Map<String, Float> retMap = new LinkedHashMap<>(); //Order is required
        //Sort by value
        unsortedMap.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).forEach(entry -> retMap.put(entry.getKey(), entry.getValue()));
        return retMap;
    }

    //Convert the total from the given currency to the target currency, using the current exchange rate
    private static float currencyExchange(float total, Currency from, Currency to){
        if (from == to)
            return total;
        
        float usingRate = getExchangeRateF();
        if (to == Currency.CAD)
            usingRate = 1 / usingRate;
        return total * usingRate; 
    }

    //Manually set exchange rate, only if automatic updating is already disabled
    static void setExchangeRate(float rate){
        if(!autoRate)
            Db.rate = rate;
    }

    //Gets the current exchange rate, updating from the remote if required
    private static float getExchangeRateF(){
        if(autoRate){
            float retVal = PriceWorker.updateRate();
            if (retVal > 0f)
                rate = retVal;
        }

        return rate;
    }

    static String getExchangeRate(){
        return Float.toString(getExchangeRateF());
    }

    static boolean getAutoRate(){
        return autoRate;
    }

    static void toggleAutoRate(){
        autoRate = !autoRate;
    }

    static String getApiKey() {
        return apiKey;
    }

    static void setApiKey(String key){
        apiKey = key;
    }

    static Currency getGraphCurrency() {
        return graphCurrency;
    }

    static void setGraphCurrency(Currency currency) {
        graphCurrency = currency;
    }

    static int getNumEntries() {
        return entries.size();
    }

    static Entry getEntry(int index){
        return entries.get(index);
    }

}
