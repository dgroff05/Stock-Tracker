package stock_tracker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;

public class StockQuote {
    // Instance variables
    private String symbol;
    private double price;
    private long volume;
    private double change;
    private String changePercent;

    // Constructors
    public StockQuote(String symbol, double price, long volume, double change, String changePercent) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.change = change;
        this.changePercent = changePercent;
    }




    // Getters

    /**
     * Uses the API to get a quote of the stock. Here is an example of a quote:
     * {
        "Global Quote": {
            "01. symbol": "AAPL",
            "02. open": "198.7100",
            "03. high": "200.3700",
            "04. low": "197.4500",
            "05. price": "199.9500",
            "06. volume": "52364128",
            "07. latest trading day": "2026-05-30",
            "08. previous close": "198.5300",
            "09. change": "1.4200",
            "10. change percent": "0.7152%"
        }
    }
     * @param symbol is the stock ticker to lookup
     * @return returns latest quote for the ticker
     * @throws Exception
     */
    public static String getStockData(String symbol) throws Exception {
        // Builds URL to HTTP with
        String url =
            "https://www.alphavantage.co/query" +
            "?function=GLOBAL_QUOTE" +
            "&symbol=" + symbol +
            "&apikey=" + API_KEY;

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    /**
     * Parses the quote to get the instance variables of the stock
     * @param symbol is the ticker to use
     * @return returns a StockQuote object with the quote data
     * @throws Exception
     */
    public static StockQuote getQuote(String symbol) {
        try {
            String json = getStockData(symbol);
        
            ObjectMapper mapper = new ObjectMapper();
            JsonNode quote = mapper.readTree(json).get("Global Quote");
        
            return new StockQuote(symbol,
                            quote.get("05. price").asDouble(),
                            quote.get("06. volume").asLong(), 
                            quote.get("09. change").asDouble(),
                            quote.get("10. change percent").asText());
        } catch (Exception e) {
            System.out.println("Error retrieving stock data for " + symbol + ".");
            return null;
        }
    }

    /**
     * Gets the stock symbol.
     * @return symbol as a String 
     */
    public String getSymbol() {
        return this.symbol;
    }

    /**
     * Gets the stock price.
     * @return price as a double
     */
    public double getPrice() {
        return this.price;
    }
    
    /**
     * Gets the stock volume.
     * @return the volume as a long
     */
    public long getVolume() {
        return this.volume;
    }

    /**
     * Gets the stock change.
     * @return the change as a double
     */
    public double getChange() {
        return this.change;
    }

    /**
     * Gets the stock change percent.
     * @return the change percent as a String
     */
    public String getChangePercent() {
        return this.changePercent;
    }

    /**
     * 
     */
    public String toString() {
        return this.symbol + "\n" +
                "Price: $" + this.price + "\n" +
                "Volume: " + this.volume + "\n" +
                "Change: " + this.change + "(" + this.changePercent + ")" + "\n";
    }

    // Global Vars
    private static final Dotenv dotenv = Dotenv.load();
    // Alpha Vantage key
    private static final String API_KEY = dotenv.get("ALPHA_VANTAGE_API_KEY");
}
