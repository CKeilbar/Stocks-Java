package main;

import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.SwingWorker;
import java.io.*;
import java.net.*;


//Updates the prices
//Can either run in a background thread or not
public class PriceWorker extends SwingWorker<ArrayList<String>, Void> {

    private String apiKey;
    private ArrayList<Entry> entries;
    public static Float exchangeRate = 1f;
    public static boolean shouldUpdate;
    private static boolean didUpdate;

    public PriceWorker(String apiKey, ArrayList<Entry> entries){
        this.apiKey = apiKey;
        this.entries = entries;
    }

    //Updates the prices in the main entries and returns an array containing tickers that had issues
    @Override
    public ArrayList<String> doInBackground(){
        float price;
        int progress;

        HashMap<String, Float> priceMap = new HashMap<>(32); //Keep track of which tickers we have already updated so we don't search for the price multiple times
        ArrayList<String> failedEntries = new ArrayList<>();
        int numEntries = entries.size();

        //No prices; return done
        if (numEntries == 0)
            progress = 1;
        else
            progress = 0;

        for(int i = 0; i < numEntries; i++){
            Entry entry = entries.get(i);
            setProgress(progress++);//Increment on each ticker
            if(entry.getUpdatePrice()){
                String ticker = entry.getTicker();
                if(priceMap.containsKey(ticker)){
                    entry.setPrice(priceMap.get(ticker));
                }
                else{
                    price = updatePrice(ticker, apiKey);
                    if(price >= 0f){
                        priceMap.put(ticker, price);
                        entry.setPrice(price);
                    }
                    else{
                        failedEntries.add(ticker);
                    }
                }
            }
        }

        setProgress(progress); //We set the progress one final time to let the world know we are about to finish
        return failedEntries;
    }

    @Override
    public void done(){
        //Doesn't do anything at the moment, gets called automatically
    }

    //Returns the price or something < 0f if there was an error
    public static float updatePrice(String ticker, String apiKey){
        //After 13 commas have been seen we reach the price
        int skipCommas = 13;
        int seenCommas = 0;
        float price = -1f;//Unknown error, probably unreachable
        int readChar;

        try{
            //Almost all of these lines can fail
            URI uri = URI.create("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol="+ticker+"&apikey="+apiKey+"&datatype=csv");
            URL url = uri.toURL();
            URLConnection uc = url.openConnection();
            InputStream is = uc.getInputStream();
            InputStreamReader ir = new InputStreamReader(is);

            try(BufferedReader in = new BufferedReader(ir)){
                //Search through one byte at a time counting commas
                while((readChar = in.read()) != -1){
                    if(readChar == ','){
                        if(++seenCommas == skipCommas)
                            break;
                    }
                }

                String stringPrice = "";
                while(true){
                    readChar = in.read();
                    if (readChar == -1 || readChar == ',')
                        break;
                    stringPrice += (char) readChar;
                }
                price = Float.parseFloat(stringPrice);

            } catch(NumberFormatException unused){
                return -2f;//Could not find a valid price
            } catch(Exception unused){
                return -3f;//I/O error
            }
        } catch(Exception unused){
            return -4f;//Could be no internet, they changed their query format, or any number of other things
        }

        return price;
    };

    public static void updateRate(String apiKey){
        if (didUpdate)
            return;
        
        try{
            //Almost all of these lines can fail
            URI uri = URI.create("https://www.alphavantage.co/query?function=CURRENCY_EXCHANGE_RATE&from_currency=CAD&to_currency=USD&apikey="+apiKey);
            URL url = uri.toURL();
            URLConnection uc = url.openConnection();
            InputStream is = uc.getInputStream();
            InputStreamReader ir = new InputStreamReader(is);

            try(BufferedReader in = new BufferedReader(ir)){
                in.skip(257); //Skip all of the other information, advancing the position to the exchange rate

                //Now parse the rate
                int readChar;
                String stringPrice = "";
                while(true){
                    readChar = in.read();
                    if (readChar == -1 || readChar == '"')
                        break;
                    stringPrice += (char) readChar;
                }

                float rate = Float.parseFloat(stringPrice);
                exchangeRate = rate;
                didUpdate = true;

            } catch(Exception unused){}//Could not find a valid price or I/O error, too bad

        } catch(Exception unused){}//Could be no internet, they changed their query format, or any number of other things
    }

    public static String getExchangeRate(){
        return Float.toString(exchangeRate);
    }

    public static float getExchangeRate(String apiKey){
        if(shouldUpdate && !didUpdate)
            updateRate(apiKey);
        return exchangeRate;
    }

    public static void setExchangeRate(float rate){
        exchangeRate = rate;
    }

}
