/* 
  File Name  : App.java
  Author     : Dylan Groff
  Date       : 5/27/26
  Description: Stock Tracker is a console-based portfolio management application
  built with Java and MongoDB. Users can register/login securely,
  buy and sell stocks, track portfolio holdings, view transaction
  history, and monitor portfolio statistics such as total shares,
  total invested value, and number of holdings.
 
  The application uses MongoDB for persistent storage and models
  portfolios, transaction histories, and user accounts using
  embedded documents, linking, and collections.
*/
package stock_tracker;


// imports
import java.util.Scanner;
import org.mindrot.jbcrypt.BCrypt;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Iterator;
import io.github.cdimascio.dotenv.Dotenv;
import org.javatuples.Triplet;

// Jakarta Mail imports
import jakarta.mail.Session;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Message;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

// MongoDB imports
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
/**Connect to a collection**/
import com.mongodb.client.MongoCollection;
/**Create Document Objects**/
import org.bson.Document;
/**Create Bson Objects (e.g. Bson filters)**/
import org.bson.conversions.Bson;
/**delete() and deleteOne() methods return DeleteResult Object**/
import com.mongodb.client.result.DeleteResult;

/**eq method returns a Bson filter that tests if arg1 = arg2**/
import static com.mongodb.client.model.Filters.eq;
/**Create BasicDBObject**/
import com.mongodb.BasicDBObject;
import org.bson.types.ObjectId;
/**Create Cursor Object to visit all documents in one query result**/
import com.mongodb.client.MongoCursor;


/**
 * Class for all Stock Tracker code
 */
public class StockTrackerCLI {
    public static void main( String[] args ) {
        //System.out.println("Connected to MongoDB!");

        // Print intro
        System.out.println("Welcome to the Stock Tracker!");

        // Login/register
        Document client;
        while (true) {
            System.out.println("Type 'register' or 'login': ");
            String action = input.nextLine();
            if (action.equalsIgnoreCase("register")) {
                client = register();
                break;
            } else if (action.equalsIgnoreCase("login")) {
                client = login();
                break;
            } else {
                System.out.println("Invalid option.");
            }
        }
        System.out.println("Welcome back, " + client.getString("name") + "!");

        // Start command loop
        System.out.println("Type 'help' to see a list of commands.");
        while (true) {
            switch (input.nextLine()) {
                case "help" -> printHelp();
                case "buy" -> {
                    boolean success = insert(client);
                    if (success) System.out.println("Buy successful!");
                    else System.out.println("Buy failed!");
                }
                case "sell" -> {
                    boolean success = delete(client);
                    if (success) System.out.println("Sell successful!");
                    else System.out.println("Sell failed!");
                }
                case "stats" -> {
                    Document portfolio = getPortfolio(client);
                    Document stats = (Document) portfolio.get("stats");
                    System.out.println("Stock Count: " + stats.getInteger("stockCount"));
                    System.out.println("Total Shares: " + stats.getInteger("shareCount"));
                    System.out.println("Total Invested: $" + stats.getDouble("totalInvested"));
                }
                case "transactions" -> printTransactionHistory(client);
                case "lookup" -> lookup();
                case "email" -> sendEmail(client.getString("email"), "Portfolio Report", buildMonthlyReport(client));
                case "quit" -> {
                    // close connections
                    input.close();
                    connection.close();

                    return;
                }
                default -> System.out.println("invalid choice.");
            }
        }
    }

    /**
     * 
     */
    private static Document login() {
        while (true) {
            // Prompt for username and password
            System.out.println("Username: ");
            String username = input.nextLine();
            System.out.println("Password: ");
            String password = input.nextLine();
        
            // Connect to the collection "clients"
		    MongoCollection<Document> clients = database.getCollection("clients");

            // Find client
            Document client = new Document("username", username);
            Document found = (Document) clients.find(client).first();
            if (found != null) {
                // Get stored hash
                String storedHash = found.getString("password");

                // Compare entered password to stored hash
                if (BCrypt.checkpw(password, storedHash)) {
                    System.out.println("Login successful!");
                    return found;
                } else {
                    System.out.println("Invalid password.");
            }
            } else {
                System.out.println("User not found.");
            }
        }
    }

    /**
     * 
     */
    private static Document register() {
        // Connect to the collection "clients" and insert new user
		MongoCollection<Document> clients = database.getCollection("clients");

        // Prompt for registration info
        System.out.println("Enter your first name: ");
        String name = input.nextLine().trim();
        String email;
        while (true) {
            System.out.println("Enter your email: ");
            email = input.nextLine();

            // Check email format
            if (!email.contains("@")) {
                System.out.println("Invalid email.");
                continue;
            }

            // Check if email already exists
            Document existingEmail = clients.find(new Document("email", email)).first();
            if (existingEmail != null) System.out.println("Email already exists.");
            else break;
        }
        String username;
        while (true) {
            System.out.println("Enter a username: ");
            username = input.nextLine();
            Document existingUser = clients.find(new Document("username", username)).first();
            if (existingUser != null) System.out.println("Username already exists.");
            else break;
        }
        String password1;
        while (true) {
            System.out.println("Enter password (must be at least 8 characters): ");
            password1 = input.nextLine();
            System.out.println("Re-enter password: ");
            String password2 = input.nextLine();
            if (password1.equals(password2) && password1.length() >= 8) break;
            else System.out.println("Passwords must be the same and at least 8 characters long. ");
        }
        // Save for future implementation
        // System.out.println("Receive monthly email updates? (y/n)");
        // boolean monthlyEmails = input.nextLine().trim().equalsIgnoreCase("y");

        
        // Create document for new user
        Document client = new Document("username", username);
        client.append("password", BCrypt.hashpw(password1, BCrypt.gensalt()));
        client.append("name", name);
        client.append("email", email);
        // Save for later implementation
        //client.append("monthlyEmails", monthlyEmails);
        client.append("createdAt", LocalDate.now().toString());
        
        // insert new user
        clients.insertOne(client);

        return client;
    }

    /**
     * This method inserts new holdings into the client's portfolio, updates current holdings if it already exists,
     * updates portfolio stats, and updates transaction history.
     * @param client is the user's document
     * @return bool tracking success
     */
    private static boolean insert(Document client) {
        // Get client's portfolio, transactions, and userId
        Document portfolio = getPortfolio(client);
        List<Document> transactionHistory = getTransactionHistory(client);
        Document transactionsDoc = getTransactions(client);
        ObjectId userId = client.getObjectId("_id");

        // Connect to collections
        MongoCollection<Document> portfolios = database.getCollection("portfolios");
        MongoCollection<Document> transactions = database.getCollection("transactions");

        // Prompt for new stock
        String ticker;
        while (true) {
            System.out.println("Enter the stock's ticker: ");
            ticker = input.nextLine().trim().toUpperCase();
            if (ticker.length() < 1 || ticker.length() > 5) System.out.println("Invalid ticker.");
            else break;
        }
        int shares;
        while (true) {
            System.out.println("Enter the number of shares to be sold: ");
            shares = Integer.parseInt(input.nextLine());
            if (shares <= 0) System.out.println("Invalid number of shares.");
            else break;
        }
        // Lookup current stock price from API
        StockQuote quote = StockQuote.getQuote(ticker);
        if (quote == null) {
            return false;
        }
        double price = quote.getPrice();

        // Check for pre-existing stock in portfolio
        boolean updated = false;
        @SuppressWarnings("unchecked") List<Document> holdings = (List<Document>) portfolio.get("holdings");
        for (Document doc : holdings) {
            if (doc.getString("ticker").equals(ticker)) {
                // Update stock
                double currentPrice = doc.getDouble("avgPrice");
                int currentShares = doc.getInteger("shares");
                int newShares = currentShares + shares;
                double newPrice = ((currentPrice * currentShares) + (shares * price)) / (double) newShares;
                // Round to two decimal places
                newPrice = Math.round(newPrice * 100.0) / 100.0;
                doc.put("shares", newShares);
                doc.put("price", newPrice);

                // Save updates to holding
                portfolios.replaceOne(new Document("userId", userId), portfolio);

                updated = true;
                break;
            }
        }
        // If no pre-existing stock
        if (!updated) {
            // Create document for new stock
            Document newStock = new Document("ticker", ticker);
            newStock.append("shares", shares);
            newStock.append("avgPrice", price);

            // Insert new stock and save it
            holdings.add(newStock);
            portfolios.replaceOne(new Document("userId", userId), portfolio);
        }

        // Update portfolio stats
        updateStats(portfolio);

        // Create new transaction document
        Document transaction = new Document("type", "BUY");
        transaction.append("ticker", ticker);
        transaction.append("shares", shares);
        transaction.append("price", price);
        transaction.append("timestamp", LocalDateTime.now().toString());

        // Update transaction history and save
        transactionHistory.add(transaction);
        transactions.replaceOne(new Document("userId", userId), transactionsDoc);

        // return success
        return true;
    }

    /**
     * This method deletes shares of holdings, deletes holdings if all shares are sold,
     * updates client portfolio stats, and updates client transaction history.
     * @param client is the user's Document
     * @return bool tracking success
     */
    private static boolean delete(Document client) {
        // Get client's portfolio, transactions, and userId
        Document portfolio = getPortfolio(client);
        List<Document> transactionHistory = getTransactionHistory(client);
        Document transactionsDoc = getTransactions(client);
        ObjectId userId = client.getObjectId("_id");

        // Connect to collections
        MongoCollection<Document> portfolios = database.getCollection("portfolios");
        MongoCollection<Document> transactions = database.getCollection("transactions");

        // Prompt for stock
        String ticker;
        while (true) {
            System.out.println("Enter the stock's ticker: ");
            ticker = input.nextLine().trim().toUpperCase();
            if (ticker.length() < 1 || ticker.length() > 5) System.out.println("Invalid ticker.");
            else break;
        }
        int shares;
        while (true) {
            System.out.println("Enter the number of shares to be sold: ");
            shares = Integer.parseInt(input.nextLine());
            if (shares <= 0) System.out.println("Invalid number of shares.");
            else break;
        }
        // Lookup current stock price from API
        StockQuote quote = StockQuote.getQuote(ticker);
        if (quote == null) {
            return false;
        }
        double price = quote.getPrice();

        // Find stock in portfolio
        boolean found = false;
        @SuppressWarnings("unchecked") List<Document> holdings = (List<Document>) portfolio.get("holdings");
        Iterator<Document> iter = holdings.iterator();
        while (iter.hasNext()) {
            Document doc = iter.next();

            // Check for ticker match
            if (doc.getString("ticker").equals(ticker)) {
                // Update stock
                int currentShares = doc.getInteger("shares");
                // Ensure shares to sell is not > than current shares
                while (true) {
                    if (currentShares < shares) {
                        System.out.println("Can not sell more shares than what is owned.");
                        System.out.println("Please enter a new number of shares to sell (max: " + currentShares + "): ");
                        shares = Integer.parseInt(input.nextLine());
                    }
                    else break;
                }
                if (currentShares - shares == 0) iter.remove();
                else doc.put("shares", currentShares - shares);

                // Save updates to holding
                portfolios.replaceOne(new Document("userId", userId), portfolio);

                found = true;
                break;
            }
        }

        // Breaks out of method if stock isn't found
        if (!found) return false;

        // Update portfolio stats
        updateStats(portfolio);

        // Create new transaction document
        Document transaction = new Document("type", "SELL");
        transaction.append("ticker", ticker);
        transaction.append("shares", shares);
        transaction.append("price", price);
        transaction.append("timestamp", LocalDateTime.now().toString());

        // Update transaction history and save
        transactionHistory.add(transaction);
        transactions.replaceOne(new Document("userId", userId), transactionsDoc);

        // return success
        return true;
    }

    /**
     * Getter method to retrieve the client's portfolio document.
     * @param client is the client document to used to get linked portfolio
     * @return the client's portfolio document
     */
    private static Document getPortfolio(Document client) {
        // Get userId 
        ObjectId userId = client.getObjectId("_id");

        // Connect to the collection
        MongoCollection<Document> portfolios = database.getCollection("portfolios");

        // Find portfolio
        Document portfolio = portfolios.find(new Document("userId", userId)).first();

        // Create portfolio if it doesn't exist
        if (portfolio == null) {
            portfolio = new Document("userId", userId);
            portfolio.append("holdings", new ArrayList<Document>());
            portfolio.append("stats", new Document());
            portfolios.insertOne(portfolio);
            System.out.println("New portfolio created.");
        }

        return portfolio;
    }

    /**
     * Getter method to retrieve the list of client transactions.
     * @param client the client used to get the transaction list
     * @return the client's transaction list
     */
    private static List<Document> getTransactionHistory(Document client) {
        // Get client transactions 
        Document clientTransactions = getTransactions(client);

        // Suppress unchecked casting warning and return the ArrayList of transactions
        @SuppressWarnings("unchecked") List<Document> transactionHistory = (List<Document>) clientTransactions.get("transactions");
        return transactionHistory;
    }

    /**
     * Getter method to retrieve client transaction document.
     * @param client is the client used to get transaction document
     * @return the client's transaction document
     */
    private static Document getTransactions(Document client) {
        // Get userId 
        ObjectId userId = client.getObjectId("_id");

        // Connect to the collection
        MongoCollection<Document> transactions = database.getCollection("transactions");

        // Find the client's transactions
        Document clientTransactions = transactions.find(new Document("userId", userId)).first();

        // Create transaction history if it doesn't exist
        if (clientTransactions == null) {
            clientTransactions = new Document("userId", userId);
            clientTransactions.append("transactions", new ArrayList<Document>());
            transactions.insertOne(clientTransactions);
            System.out.println("New transaction history created.");
        }

        return clientTransactions;
    }

    /**
     * Prints the transactions of a client from a given length of time.
     * @param client the client to get the transactions from
     */
    private static void printTransactionHistory(Document client) {
        // Get client transaction history
        List<Document> transactionHistory = getTransactionHistory(client);

        while (true) {
            // Get user's choice
            System.out.println("Enter 'YTD' for year to date, 'range' for a range, or 'month' to get last month: ");
            String choice = input.nextLine().trim().toLowerCase();
            switch (choice) {
                case "month" -> {
                    // Prints if transaction is in the past month
                    System.out.println(getMonthlyTransactions(client));
                    return;
                }
                case "range" -> {
                    // Prints if transaction is in the range inclusively
                    System.out.println("Enter the start date (yyyy-mm-dd): ");
                    LocalDateTime start = LocalDate.parse(input.nextLine()).atStartOfDay();
                    System.out.println("Enter the end date (yyyy-mm-dd): ");
                    LocalDateTime end = LocalDate.parse(input.nextLine()).atTime(23, 59, 59);
                    for (Document transaction : transactionHistory) {
                        LocalDateTime transactionDate = LocalDateTime.parse(transaction.getString("timestamp"));
                        if (!transactionDate.isBefore(start) && !transactionDate.isAfter(end)) {
                            printTransaction(transaction);
                        }
                    }

                    return;
                }
                case "ytd" -> {
                    // Prints if transaction is within the year
                    LocalDateTime startOfYear = LocalDate.now().withDayOfYear(1).atStartOfDay();
                    for (Document transaction : transactionHistory) {
                        LocalDateTime transactionDate = LocalDateTime.parse(transaction.getString("timestamp"));
                        if (!transactionDate.isBefore(startOfYear)) {
                            printTransaction(transaction);
                        }
                    }
                    return;
                }
                case "all" -> {
                    // Prints all transactions
                    for (Document transaction : transactionHistory)
                        printTransaction(transaction);
                    return;
                }
                default -> {
                    System.out.println("Invalid option");
                }
            }
        }
    }

    /**
     * Method to lookup a quote for any stock in the Alpha Vantage API and print it.
     */
    private static void lookup() {
        // Prompt for stock
        String ticker;
        while (true) {
            System.out.println("Enter the stock's ticker: ");
            ticker = input.nextLine().trim().toUpperCase();
            if (ticker.length() < 1 || ticker.length() > 5) System.out.println("Invalid ticker.");
            else break;
        }

        // Create StockQuote object and print it
        StockQuote quote = StockQuote.getQuote(ticker);
        System.out.println(quote.toString());
    }

    /**
     * Function to build the monthly report to be emailed to the client.
     * @param client the client to use to fetch the data
     * @return returns a String of the report.
     */
    private static String buildMonthlyReport(Document client) {
        // Get user stats
        Document portfolio = getPortfolio(client);
        Document stats = (Document) portfolio.get("stats");

        // Build monthly report string
        StringBuilder report = new StringBuilder();
        //Build intro
        report.append("<html lang=\"en\">");
        report.append("<head><title>Monthly Portfolio Report</title></head></html>");
        report.append("<body>");
        report.append("Hello, " + client.getString("name"));
        // Build stats section
        report.append("<h1><u>Portfolio Summary</u></h1><br>");
        report.append("Stock Count: " + stats.getInteger("stockCount") + "<br>" );
        report.append("Total Shares: " + stats.getInteger("shareCount") + "<br>");
        double invested = stats.getDouble("totalInvested");
        report.append("Total Invested: $" + String.format("%.2f", invested) + "<br><br>");
        double portfolioValue = getPortfolioValue(client);
        double gainLoss = portfolioValue - stats.getDouble("totalInvested");
        double gainPercent = (gainLoss / invested) * 100.0;
        report.append("Current Portfolio Value: $" + String.format("%.2f", portfolioValue) + "<br>");
        report.append("Unrealized Gain/Loss: " + String.format("%.2f", gainLoss) + "(" + String.format("%.2f", gainPercent) + ")" + "<br><br>");
        // Build largest holding section
        report.append("<h1><u>Largest Holding</u></h1><br>");
        Triplet<StockQuote, Double, Integer> largestHolding = findLargestHolding(client);
        String ticker = largestHolding.getValue0().getSymbol();
        double value = largestHolding.getValue1();
        int shares = largestHolding.getValue2();
        report.append(ticker + "<br>" + shares + " shares<br>MarketValue: $" + String.format("%.2f", value) + "<br><br>");
        // Build transactions section
        report.append("<h1><u>Latest Transactions</u></h1><br>");
        report.append(getMonthlyTransactions(client));

        // Build outro
        report.append("<br>Report generated " + LocalDateTime.now());
        report.append("</body></html>");
        
        return report.toString();
    }

    /**
     * Sums all stock shares * current price and returns it.
     * @param client the client to use for data retrieval
     * @return returns the portfolio value as a double
     */
    private static double getPortfolioValue(Document client) {
        // Get holdings
        Document portfolio = getPortfolio(client);
        @SuppressWarnings("unchecked") List<Document> holdings = (List<Document>) portfolio.get("holdings");

        // Iterate through holdings to get tickers and get the current value of the stocks
        double portfolioValue = 0.0;
        for (Document stock : holdings) {
            String ticker = stock.getString("ticker");
            int shares = stock.getInteger("shares");

            // Lookup current stock price from API
            StockQuote quote = StockQuote.getQuote(ticker);
            double price = quote.getPrice();

            portfolioValue += price * shares;
        }

        return portfolioValue;
    }

    /**
     * Finds the client's largest holding based on current value * num of shares.
     * @param client the client to use for data
     * @return returns a tuple containing (StockQuote, stock value, shares owned) for the
     * client's largest holding
     */
    private static Triplet<StockQuote, Double, Integer> findLargestHolding(Document client) {
        // Get holdings
        Document portfolio = getPortfolio(client);
        @SuppressWarnings("unchecked") List<Document> holdings = (List<Document>) portfolio.get("holdings");

        // Set first holding to max and get the holding value
        StockQuote largest = StockQuote.getQuote(holdings.get(0).getString("ticker"));
        int shares = holdings.get(0).getInteger("shares");
        double maxValue = largest.getPrice() * shares;
        // Iterate through holdings to get largest holding
        for (Document stock : holdings) {
            String ticker = stock.getString("ticker");

            // Lookup current stock price from API
            StockQuote quote = StockQuote.getQuote(ticker);
            double holdingValue = quote.getPrice() * stock.getInteger("shares");

            // Check for new max
            if (holdingValue > maxValue) {
                largest = quote;
                maxValue = holdingValue;
                shares = stock.getInteger("shares");
            }
        }
        // Create tuple with the StockQuote, the value of the largest holding, and the number of shares owned
        Triplet<StockQuote, Double, Integer> largestHolding = Triplet.with(largest, maxValue, shares);
        return largestHolding;
    }

    /**
     * Helper method to get the five most recent transactions.
     * @param client the client to get the transactions from
     * @return return a String of the transactions
     */
    private static String getMonthlyTransactions(Document client) {
        // Get transaction histroy
        List<Document> transactions = getTransactionHistory(client);
    
        // Get previous month
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);

        // Build string based on transactions during the month
        StringBuilder result = new StringBuilder();
        for (Document transaction : transactions) {
            // Get transaction date
            LocalDateTime transactionDate = LocalDateTime.parse(transaction.getString("timestamp"));
            if (transactionDate.isAfter(oneMonthAgo))
                result.append(transaction.getString("timestamp")
                + " | "
                + transaction.getString("type")
                + " | "
                + transaction.getInteger("shares")
                + " shares of "
                + transaction.getString("ticker")
                + " at $"
                + transaction.getDouble("price") + "\n");
        }
        
        return result.toString();
    }

    /**
     * Helper method to send emails to the users using Jakarta Mail.
     * @param email the email to send to
     * @param subject the subject of the email
     * @param body the body of the email
     * @throws Exception
     */
    private static void sendEmail(String email, String subject, String body) {
        // Get authentication info
        final String senderEmail = dotenv.get("EMAIL_ADDRESS");
        final String appPassword = dotenv.get("EMAIL_PASSWORD");
        
        Properties props = new Properties();

        // Enable authentication
        props.put("mail.smtp.auth", "true");
        // Enable encryption
        props.put("mail.smtp.starttls.enable", "true");
        // Put Gmail server address
        props.put("mail.smtp.host", "smtp.gmail.com");
        // Put Gmail SMTP port 587
        props.put("mail.smtp.port", "587");

        // Connect to Gmail
        Session session = Session.getInstance(props,
            new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                        senderEmail,
                        appPassword
                    );
                }
            });

        // Form message
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject(subject);
            message.setContent(body, "text/html");

            Transport.send(message);
            System.out.println("Email successfully sent.");
        } catch (Exception e) {
            System.out.println("Email failed.");
        }
    }
    

    /**
     * Helper to print transactions.
     * @param transaction transaction Document to be printed
     */
    private static void printTransaction(Document transaction) {
        System.out.println(
            transaction.getString("timestamp")
            + " | "
            + transaction.getString("type")
            + " | "
            + transaction.getInteger("shares")
            + " shares of "
            + transaction.getString("ticker")
            + " at $"
            + transaction.getDouble("price")
        );
    }

    /**
     * Helper method to update the stats of a portfolio.
     * @param portfolio given portfolio to update
     */
    private static void updateStats(Document portfolio) {
        // Calculate stats
        @SuppressWarnings("unchecked") List<Document> holdings = (List<Document>) portfolio.get("holdings");
        int shareCount = 0;
        double totalInvested = 0.0;
        for (Document doc : holdings) {
            shareCount += doc.getInteger("shares");
            // Lookup current stock price from API
            StockQuote quote = StockQuote.getQuote(doc.getString("ticker"));
            if (quote == null) {
                return;
            }
            double price = quote.getPrice();
            totalInvested += price * (double) doc.getInteger("shares");
        }

        // Create stocks document
        Document stats = new Document();
        stats.append("stockCount", holdings.size());
        stats.append("shareCount", shareCount);
        stats.append("totalInvested", totalInvested);

        // Update the changes
        portfolio.put("stats", stats);
    }

    /**
     * List of commands to print for the user.
     */
    private static void printHelp() {
        System.out.println("Type 'buy' to add shares of a stock to your portfolio.");
        System.out.println("Type 'sell' to sell shares of a stock from your portfolio.");
        System.out.println("Type 'stats' to see your portfolio stats.");
        System.out.println("Type transactions to see portfolio transaction history.");
        System.out.println("Type 'lookup' to see lookup a stock.");
        System.out.println("Type 'email' to be emailed a Portfolio Report.");
        System.out.println("Type 'quit' to exit the stock tracker.");
    }


    // Global Vars
    /** connect to the database using the URI I got form MongoDB **/
    private static final Dotenv dotenv = Dotenv.load();
	private static final String uri = dotenv.get("MONGO_URI");
	private static MongoClient connection = MongoClients.create(uri);
    private static MongoDatabase database = connection.getDatabase("stocks");

    //Scanner 
    private static Scanner input = new Scanner(System.in);
}