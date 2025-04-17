package main;

import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.SwingWorker;
import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;


//Updates the prices
//Can either run in a background thread or not
class PriceWorker extends SwingWorker<ArrayList<String>, Void> {

    private static final String apiURL = "https://www.alphavantage.co/query?";
    private static final LocalDate now = LocalDate.now();

    //Cache tickers so we don't search for things multiple times
    private static HashMap<String, Float> cachedPrices = new HashMap<>(32);
    private static HashMap<String, Float> cachedDivs = new HashMap<>(32);

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
            boolean priceFailed = false;
            if(entry.getUpdatePrice()){
                String ticker = entry.getTicker();
                float price = updatePrice(ticker);
                if(price >= 0f)
                    entry.setPrice(price);
                else{
                    failedEntries.add(ticker + " (price)");
                    priceFailed = true;
                }
            }
            if(entry.getUpdateDiv() && !priceFailed){
                String ticker = entry.getTicker();
                float div = updateDiv(ticker, entry.getPriceF());
                if(div >= 0f)
                    entry.setDiv(div);
                else if(!failedEntries.contains(ticker))
                    failedEntries.add(ticker + " (yield)");
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
        //Use cached result
        if(cachedPrices.containsKey(ticker))
            return cachedPrices.get(ticker);

        //After 13 commas have been seen we reach the price
        int skipCommas = 13;
        float price = -1f; //Unknown error, probably unreachable
        try{
            //Almost all of these lines can fail
            URI uri = URI.create(apiURL+"function=GLOBAL_QUOTE&symbol="+ticker+"&apikey="+Db.getApiKey()+"&datatype=csv");
            URL url = uri.toURL();
            URLConnection uc = url.openConnection();
            InputStream is = uc.getInputStream();
            InputStreamReader ir = new InputStreamReader(is);

            try(BufferedReader in = new BufferedReader(ir)){
                //Search through one byte at a time counting commas
                int seenCommas = 0;
                int readChar;
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
                price = -3f;
            }
        } catch(Exception unused){ //Could be no internet, they changed their query format, or any number of other things
            price = -2f;
        }
        return price;
    };

    //Returns the yield or something < 0f if there was an error
    public static float updateDiv(String ticker, float price){
        //Use cached result
        if(cachedDivs.containsKey(ticker))
            return cachedDivs.get(ticker);
        
        //We look at the two most recent distributions to determine the distribution frequency
        //We report the yield as the mean of those two distributions over the period of a year relative to the price
        float div = -1f; //Unknown error, probably unreachable
        
        int dateSkip = 28;
        int dateLen = 10;
        int amountSkip = 23;
        int doubleQuote = 34;
        int yearThreshold = 370;
        float daysPerYear = 365.2425f;
        
        try{
            //Almost all of these lines can fail
            URI uri = URI.create(apiURL+"function=DIVIDENDS&symbol="+ticker+"&apikey="+Db.getApiKey());
            URL url = uri.toURL();
            URLConnection uc = url.openConnection();
            InputStream is = uc.getInputStream();
            InputStreamReader ir = new InputStreamReader(is);

            try(BufferedReader in = new BufferedReader(ir)){
                LocalDate[] dates = new LocalDate[2];
                float[] amounts = new float[2];

                //We assume the data is returned over multiple lines using fixed spacing
                //Skip until record date
                for(int i = 0; i < 6; i++)
                    in.readLine();
                String line = in.readLine();
                if (line == null){
                    div = 0f; //Premature exit; no dividends
                    return div;
                }
                dates[0] = LocalDate.parse(line.substring(dateSkip, dateSkip+dateLen));

                //Make sure most recent distribution is recent
                long interval = ChronoUnit.DAYS.between(dates[0], now);
                if(interval > yearThreshold){
                    div = 0f;//Distributions are old; no current yield
                    return div;
                }

                //Skip until amount
                in.readLine();
                line = in.readLine();
                int endIndex = line.indexOf(doubleQuote, amountSkip);
                amounts[0] = Float.parseFloat(line.substring(amountSkip, endIndex-1));

                //Skip until next record date
                for(int i = 0; i < 4; i++)
                    in.readLine();
                line = in.readLine();
                dates[1] = LocalDate.parse(line.substring(dateSkip, dateSkip+dateLen));

                //Skip until amount
                in.readLine();
                line = in.readLine();
                endIndex = line.indexOf(doubleQuote, amountSkip);
                amounts[1] = Float.parseFloat(line.substring(amountSkip, endIndex-1));

                //At this point we have dates and amounts, now calculate yield if the data is reasonable             
                interval = ChronoUnit.DAYS.between(dates[1], dates[0]);
                float amount = 0.5f*(amounts[0]+amounts[1]);
                if(interval > yearThreshold && interval > 0)
                    div = -5f; //Distributions are not regular
                else if(amount < 0f || !Float.isFinite(amount))
                    div = -4f; //Distribution amounts are wrong
                else
                    div = (daysPerYear / interval) * amount / price;
            } catch(Exception unused){ //Could not find a valid yield or I/O error
                div = -3f;
            }
        } catch(Exception unused){
            div = -2f; //Could be no internet, they changed their query format, or any number of other things
        }
        finally {
            if (div >= 0f)
                cachedDivs.put(ticker, div);
        }
        return div;
    }

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
                rate = -3f;
            }
        } catch(Exception unused){ //Could be no internet, they changed their query format, or any number of other things
            rate = -2f;
        }
        return rate;
    }

}
