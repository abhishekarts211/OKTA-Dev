package com.usermig.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONObject;

public class CreateUser {

    private static final String PROVISIONED = null; // You may want to handle this constant better if used later

    public static void main(String[] args) {

        try {
            // Load the Oracle JDBC driver
            Class.forName("oracle.jdbc.driver.OracleDriver");

            // Establish a connection to the Oracle database using DriverManager
            Connection con = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:xe", "Admin", "**************");

            // Create a statement to execute SQL queries
            Statement st = con.createStatement();

            // SQL query to select user data from the Oracle database
            String sql = "select * from <Table name> where LOGIN = 'userid'";
            ResultSet rs = st.executeQuery(sql);

            // Iterate through each row of the result set
            while (rs.next()) {
                // Call the Run() method to send data to Okta using the user's data
                CreateUser.Run(
                    rs.getString("LOGIN"),       // Fetch the LOGIN column
                    rs.getString("FIRSTNAME"),   // Fetch the FIRSTNAME column
                    rs.getString("LASTNAME"),    // Fetch the LASTNAME column
                    rs.getString("EMAIL"),       // Fetch the EMAIL column
                    rs.getString("PHONENUMBER")  // Fetch the PHONENUMBER column
                );

                // Print the user's data to the console (for logging/debugging purposes)
                System.out.println(
                    rs.getString(1) + " " +
                    rs.getString(2) + " " +
                    rs.getString(3) + " " +
                    rs.getString(4) + " " +
                    rs.getString(5)
                );
            }

            // Close the database connection
            con.close();
        } catch (Exception e) {
            // Handle and print any exceptions encountered during database interaction
            System.out.println(e);
        }
    }

    // Method to send user data to Okta via HTTPS API call
    public static void Run(String login, String firstName, String lastName, String email, String mobilePhone) throws IOException {

        // URL for Okta API endpoint (you should configure this properly with your environment details)
        String urlForOkta = "https://dev-<Environment ID>.okta.com";

        // Initialize the status to failure by default
        String status = "Failure";
        OutputStream out = null;

        // Load the properties file for Okta configuration (like URL and tokens)
        File f1 = new File(System.getProperty("user.dir") + "/" + "Resource/setup.properties");

        // Create a BufferedReader to read from the properties file
        BufferedReader br = new BufferedReader(new FileReader(f1));
        Properties pro = new Properties();
        pro.load(br); // Load the properties into the Properties object

        try {
            // Get the API URL and token from the properties file
            String https_url = pro.getProperty("https_url");
            URL url = new URL(https_url);

            // Open a secure HTTPS connection to Okta
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

            // Set the request headers
            con.addRequestProperty("Content-Type", "application/json");  // Content type is JSON
            String token = pro.getProperty("token");  // Get the token from the properties
            con.addRequestProperty("Authorization", token);  // Authorization header with the token
            con.setRequestMethod("POST");  // POST method to create a user in Okta
            con.setRequestProperty("User-Agent", "Mozilla/5.0");  // Standard User-Agent header
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");  // Accept language
            con.setDoOutput(true);  // Allow the connection to send output (i.e., POST data)

            // Get the output stream for sending data
            out = con.getOutputStream();

            // Create JSON objects to structure the user data for Okta API
            JSONObject jsonNestedData = new JSONObject();  // Inner object holding user profile details
            JSONObject jsonData = new JSONObject();        // Outer object wrapping the profile

            // Populate the JSON object with user data
            jsonNestedData.put("login", login);
            jsonNestedData.put("firstName", firstName);
            jsonNestedData.put("lastName", lastName);
            jsonNestedData.put("email", email);
            jsonNestedData.put("mobilePhone", mobilePhone);

            // Add the nested user profile data to the outer JSON object
            jsonData.accumulate("profile", jsonNestedData);

            // Convert the JSON data to a byte array for sending over the connection
            byte[] postData = (jsonData.toString()).getBytes("UTF-8");

            // Print the user data (for logging/debugging purposes)
            System.out.println(jsonNestedData);

            // Write the byte array to the output stream to send the API request
            out.write(postData, 0, postData.length);

            // Read the API response from Okta
            try (BufferedReader br1 = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();  // To build the response string
                String responseLine = null;

                // Read each line of the response
                while ((responseLine = br1.readLine()) != null) {
                    // If the response contains a "PROVISIONED" status, print it
                    if (responseLine.contains("\"status\":\"PROVISIONED\"")) {
                        System.out.println(responseLine);
                    }
                    response.append(responseLine.trim());  // Append the response
                }

                // Print the entire response to the console
                System.out.println(response.toString());
            }

        } catch (Exception e) {
            // Print any exceptions during the API interaction
            System.out.println("Exception in controller: " + e);
            e.printStackTrace();
        } finally {
            if (out != null) {
                // Ensure the output stream is closed to release system resources
                out.close();
            }
        }
    }

}
