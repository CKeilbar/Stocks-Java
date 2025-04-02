package main;

import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.SwingWorker;
import java.io.*;
import java.net.*;


//Updates the prices
//Can either run in a background thread or not
class PriceWorker extends SwingWorker<ArrayList<String>, Void> {

    //Cache tickers so we don't search for the price multiple times
    private static HashMap<String, Float> cachedPrices = new HashMap<>(32);

    //Cache exchange rate so we don't search multiple times
    private static float cachedRate;
    private static boolean cachedRateValid = false;

    //Updates the prices in the provided entries and returns an array containing tickers that had issues
    @Override
    public ArrayList<String> doInBackground(){
        ArrayList<String> failedEntries = new ArrayList<>();
        int numEntries = Db.getNumEntries();

        //No prices; return done
        int progress;
        if (numEntries == 0)
            progress = 1;
        else
            progress = 0;

        for(int i = 0; i < numEntries; i++){
            Entry entry = Db.getEntry(i);
            setProgress(progress++); //Increment on each ticker

            if(entry.getUpdatePrice()){
                String ticker = entry.getTicker();
                float price = updatePrice(ticker);
                if(price >= 0f)
                    entry.setPrice(price);
                else
                    failedEntries.add(ticker);
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
    public static float updatePrice(String ticker){
        //After 13 commas have been seen we reach the price
        int skipCommas = 13;
        float price = -1f; //Unknown error, probably unreachable
        int readChar;

        //Use cached result
        if(cachedPrices.containsKey(ticker))
            return cachedPrices.get(ticker);

        try{
            //Almost all of these lines can fail
            URI uri = URI.create("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol="+ticker+"&apikey="+Db.getApiKey()+"&datatype=csv");
            URL url = uri.toURL();
            URLConnection uc = url.openConnection();
            InputStream is = uc.getInputStream();
            InputStreamReader ir = new InputStreamReader(is);

            try(BufferedReader in = new BufferedReader(ir)){
                //Search through one byte at a time counting commas
                int seenCommas = 0;
                while((readChar = in.read()) != -1){
                    if(readChar == ','){
                        if(++seenCommas == skipCommas)
                            break;
                    }
                }

                //Reached the price, extract char by char
                String stringPrice = "";
                while(true){
                    readChar = in.read();
                    if (readChar == -1 || readChar == ',')
                        break;
                    stringPrice += (char) readChar;
                }
                price = Float.parseFloat(stringPrice);
                cachedPrices.put(ticker, price);
            } catch(Exception unused){ //Could not find a valid price or I/O error
                price = -2f;
            }
        } catch(Exception unused){ //Could be no internet, they changed their query format, or any number of other things
            price = -3f;
        }
        return price;
    };

    //Returns the exchane rate or something < 0f if there was an error
    public static float updateRate(){
        float rate = -1f; //Unknown error, probably unreachable

        if (cachedRateValid)
            return cachedRate;

        try{
            //Almost all of these lines can fail
            URI uri = URI.create("https://www.alphavantage.co/query?function=CURRENCY_EXCHANGE_RATE&from_currency=CAD&to_currency=USD&apikey="+Db.getApiKey());
            URL url = uri.toURL();
            URLConnection uc = url.openConnection();
            InputStream is = uc.getInputStream();
            InputStreamReader ir = new InputStreamReader(is);

            try(BufferedReader in = new BufferedReader(ir)){
                in.skip(257); //Skip all of the other information, advancing the position to the exchange rate

                //Reached the rate, now parse char by char
                int readChar;
                String stringPrice = "";
                while(true){
                    readChar = in.read();
                    if (readChar == -1 || readChar == '"')
                        break;
                    stringPrice += (char) readChar;
                }
                rate = Float.parseFloat(stringPrice);
                cachedRate = rate;
                cachedRateValid = true;
            } catch(Exception unused){ //Could not find a valid rate or I/O error
                rate = -2f;
            }
        } catch(Exception unused){ //Could be no internet, they changed their query format, or any number of other things
            rate = -3f;
        }
        return rate;
    }

}
