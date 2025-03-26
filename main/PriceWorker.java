package main;

import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.SwingWorker;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;


//Updates the prices
//Can either run in a background thread or not
public class PriceWorker extends SwingWorker<ArrayList<String>, Void> {

    //Updates the prices in the main entries and returns an array containing tickers that had issues
    @Override
    public ArrayList<String> doInBackground(){
        float price;
        int progress = 0;

        HashMap<String, Float> priceMap = new HashMap<>(32); //Keep track of which tickers we have already updated so we don't search for the price multiple times
        ArrayList<String> failedEntries = new ArrayList<>();

        for(int i = 0; i < Main.entries.size(); i++){
            Entry entry = Main.entries.get(i);
            setProgress(progress++);//Increment on each ticker
            if(entry.getUpdatePrice()){
                String ticker = entry.getTicker();
                if(priceMap.containsKey(ticker)){
                    entry.setPrice(priceMap.get(ticker));
                }
                else{
                    price = updatePrice(ticker);
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
    public static float updatePrice(String ticker){
        //Magic pattern that shows up right before the price
        //The price will be after the first '>' after this message and before the next '<'
        String searchTerm = "Trsdu(0.3s) Fw(b) Fz(36px) Mb(-4px)";
        int searchLength = searchTerm.length();
        int searchIndex = 0;

        boolean foundTerm = false;
        int readChar;
        float price = -1f;

        try{
            URL url = URI.create("https://ca.finance.yahoo.com/quote/" + ticker).toURL();//Tickers should be in this form
            URLConnection uc = url.openConnection();

            InputStreamReader input = new InputStreamReader(uc.getInputStream());
            BufferedReader in = new BufferedReader(input);

            //Search through one byte at a time for our magic pattern
            while((readChar = in.read()) != -1){
                if(readChar == searchTerm.charAt(searchIndex)){
                    if(++searchIndex == searchLength){
                        foundTerm = true;
                        break;
                    }
                }
                else{//Need to check if this character is the start of the search
                    searchIndex = 0;
                    if(readChar == searchTerm.charAt(searchIndex)){
                        searchIndex++;
                    }
                }
            }

            if(foundTerm){
                String stringPrice = "";

                while((readChar = in.read()) != '>'){}//Fast forward to the start of the price

                while((readChar = in.read()) != '<'){//Read until the price is gobbled up
                    stringPrice += (char) readChar;
                }

                price = Float.parseFloat(stringPrice);
            }

            in.close();

        } catch(Exception ex){//Something somewhere went wrong, cannot recover.
            return -1f;//Could be bad ticker, no internet, Yahoo changed their page
        }

        return foundTerm ? price : -2f;
    };

}
