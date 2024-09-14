package com.usermig.util;

import java.io.BufferedReader; // To read text from an input stream efficiently
import java.io.File; // To represent file or directory paths
import java.io.FileReader; // To read the contents of a file character-by-character
import java.io.IOException; // For handling input/output exceptions
import java.io.InputStreamReader; // To convert byte streams into character streams
import java.io.OutputStream; // To write bytes to an output destination
import java.net.URL; // To represent a URL and interact with web-based resources
import java.sql.Connection; // To manage a connection with a database
import java.sql.DriverManager; // To manage a list of database drivers and establish connections
import java.sql.ResultSet; // To hold and manipulate the results of a database query
import java.sql.Statement; // To execute SQL queries and update statements
import java.util.ArrayList; // To use a resizable array implementation of the List interface
import java.util.List; // To represent an ordered collection of elements
import java.util.Properties; // To manage key-value pairs, typically for configuration data
import java.util.concurrent.Callable; // To define tasks that return results and handle exceptions
import java.util.concurrent.ExecutorService; // To manage a pool of threads for concurrent task execution
import java.util.concurrent.Executors; // To create and manage thread pools
import java.util.concurrent.Future; // To represent the result of an asynchronous computation
import javax.net.ssl.HttpsURLConnection; // To handle secure (HTTPS) HTTP connections
import org.json.JSONObject; // To create and manipulate JSON data structures


public class CreateUser {

    // Thread pool size - this can be adjusted based on system capacity
    private static final int THREAD_POOL_SIZE = 10;
    
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE); // Create a thread pool

        try {
            // Load Oracle JDBC driver
            Class.forName("oracle.jdbc.driver.OracleDriver");

            // Establish a connection to the Oracle database
            Connection con = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:xe", "Admin", "**************");
            Statement st = con.createStatement();

            // Query to fetch user data
            String sql = "SELECT * FROM <Table name> WHERE LOGIN IS NOT NULL";  // Adjust this query as per your requirement
            ResultSet rs = st.executeQuery(sql);

            // Batch size to process users in each thread
            int batchSize = 1000;  // Adjust batch size based on performance
            List<Callable<String>> tasks = new ArrayList<>();
            
            List<User> userBatch = new ArrayList<>();
            int counter = 0;

            // Iterate through the result set and collect user data
            while (rs.next()) {
                User user = new User(
                        rs.getString("LOGIN"),
                        rs.getString("FIRSTNAME"),
                        rs.getString("LASTNAME"),
                        rs.getString("EMAIL"),
                        rs.getString("PHONENUMBER")
                );

                userBatch.add(user);
                counter++;

                // When we have enough users for a batch, create a new task
                if (counter % batchSize == 0) {
                    tasks.add(new UserMigrationTask(new ArrayList<>(userBatch)));
                    userBatch.clear(); // Clear batch for next set of users
                }
            }

            // Add any remaining users if the last batch was incomplete
            if (!userBatch.isEmpty()) {
                tasks.add(new UserMigrationTask(new ArrayList<>(userBatch)));
            }

            // Submit all tasks to be processed by the thread pool
            List<Future<String>> results = executor.invokeAll(tasks);

            // Process results (optional, based on logging or monitoring needs)
            for (Future<String> result : results) {
                System.out.println(result.get());  // Print the status of each batch
            }

            con.close();  // Close the database connection

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Shut down the thread pool to release resources
            executor.shutdown();
        }
    }
}

// User class to hold user information
class User {
    String login;
    String firstName;
    String lastName;
    String email;
    String mobilePhone;

    public User(String login, String firstName, String lastName, String email, String mobilePhone) {
        this.login = login;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.mobilePhone = mobilePhone;
    }
}

// Task to migrate a batch of users to Okta
class UserMigrationTask implements Callable<String> {

    private List<User> users;

    public UserMigrationTask(List<User> users) {
        this.users = users;
    }

    @Override
    public String call() {
        // Migrate each user in the batch
        for (User user : users) {
            try {
                CreateUser.migrateUserToOkta(user);
            } catch (IOException e) {
                System.err.println("Error migrating user: " + user.login);
                e.printStackTrace();
            }
        }
        return "Batch migration completed. Users processed: " + users.size();
    }
}

// Method to migrate a single user to Okta
class CreateUser {

    public static void migrateUserToOkta(User user) throws IOException {
        String urlForOkta = "https://dev-<Environment ID>.okta.com";  // Replace with your Okta domain
        OutputStream out = null;

        File f1 = new File(System.getProperty("user.dir") + "/" + "Resource/setup.properties");
        BufferedReader br = new BufferedReader(new FileReader(f1));
        Properties pro = new Properties();
        pro.load(br);

        try {
            String https_url = pro.getProperty("https_url");
            URL url = new URL(https_url);

            // Set up the HTTPS connection to Okta
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.addRequestProperty("Content-Type", "application/json");
            String token = pro.getProperty("token");
            con.addRequestProperty("Authorization", "Bearer " + token);
            con.setRequestMethod("POST");
            con.setDoOutput(true);

            // Create JSON payload for the user
            JSONObject jsonNestedData = new JSONObject();
            JSONObject jsonData = new JSONObject();
            jsonNestedData.put("login", user.login);
            jsonNestedData.put("firstName", user.firstName);
            jsonNestedData.put("lastName", user.lastName);
            jsonNestedData.put("email", user.email);
            jsonNestedData.put("mobilePhone", user.mobilePhone);

            jsonData.accumulate("profile", jsonNestedData);
            byte[] postData = (jsonData.toString()).getBytes("UTF-8");

            // Send the user data to Okta
            out = con.getOutputStream();
            out.write(postData, 0, postData.length);

            // Read the response from Okta
            try (BufferedReader br1 = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br1.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("User " + user.login + " migrated: " + response.toString());
            }

        } catch (Exception e) {
            System.out.println("Exception in user migration for user: " + user.login);
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}
