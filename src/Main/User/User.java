package Main.User;

import Main.Window;
import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import imgui.type.ImString;
import org.bson.Document;
import org.bson.types.ObjectId;
import Security.KeystoreMap;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class User extends Document {
    private long creationTime;
    private String userName, password, email, bio;
    private static final KeystoreMap usersKeyStore;
    private static final List<User> users = new ArrayList<>();
    private static User currentUser;
    private static final LogInLayout logInLayout = new LogInLayout();
    private static final SignUpLayout signUpLayout = new SignUpLayout();
    private static final UserMenuLayout userMenuLayout = new UserMenuLayout();
    private static final SettingsLayout settingsLayout = new SettingsLayout();
    private static volatile boolean connection = false;
    private static final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    // Connection check thread
    static {
        Thread connectionThread = new Thread(() -> {
            while (!GLFW.glfwWindowShouldClose(Window.getCurrentWindow().getWindowHandle())) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("8.8.8.8", 53), 3000);
                    connection = true;
                } catch (IOException e) {
                    connection = false;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        connectionThread.setDaemon(true);
        connectionThread.setName("ConnectionMonitor");
        connectionThread.start();
    }

    // Initialize keystore and load users asynchronously
    static {
        try {
            usersKeyStore = new KeystoreMap("privateData.jks","*V}4C!AeA@P0UCg,ZBW.c:%GEb1JEDKD_Ab~hhD>.o]j21?>aQyA*@!8Mw52*LGyak^c,jdsTWFGDUgC)BA0Xh-zp-yfPwjYV:hF");
            System.out.println("usersKeyStore.keySet() = " + usersKeyStore.keySet());

            // Load stored users asynchronously - wait for connection first
            CompletableFuture.runAsync(() -> {
                // Wait for initial connection check
                int attempts = 0;
                while (!checkForConnection() && attempts < 20) {
                    try {
                        Thread.sleep(250);
                        attempts++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (checkForConnection()) {
                    loadStoredUsers();
                    System.out.println("=== Users loaded. Total count: " + users.size() + " ===");
                    for (User u : users) {
                        System.out.println("  - " + u.userName + " (email: " + u.email + ")");
                    }
                } else {
                    System.out.println("=== No connection available, users not loaded ===");
                }
            }, executorService).thenRun(() -> {
                // Show login page after users are loaded
                String lastUser = usersKeyStore.get("__________lastuser__________");
                System.out.println("Last user: " + lastUser);

                if (lastUser != null && !lastUser.equals("__________nullable__________") && !lastUser.isEmpty() && checkForConnection()) {
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            MongoClient client = getMongoClient();
                            MongoDatabase database = client.getDatabase("SnowMemo");
                            MongoCollection<Document> collection = database.getCollection("Users");
                            Document query = new Document("user.userName",
                                    new Document("$regex", "^" + Pattern.quote(lastUser) + "$")
                                            .append("$options", "i"));
                            return collection.find(query).first();
                        } catch (Exception e) {
                            System.err.println("Error checking last user: " + e.getMessage());
                            return null;
                        }
                    }, executorService).thenAccept(user -> {
                        if (user != null) {
                            logInLayout.logInto(lastUser);
                            logInLayout.setVisible(true);
                            System.out.println("Login page shown with LAST user: " + lastUser);
                        } else {
                            System.out.println("Last user not found in database");
                        }
                    });
                } else {
                    System.out.println("Login page shown (no last user)");
                }
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads all stored users from the keystore into the users list
     * DOES NOT use .contains() - manually checks usernames
     */
    private static void loadStoredUsers() {
        try {
            java.util.Set<String> storedUsernames = usersKeyStore.keySet();

            if (!checkForConnection()) {
                System.out.println("No connection - skipping user loading (requires online verification)");
                return;
            }

            // Only load users when online - verify each user exists in database
            MongoClient client = getMongoClient();
            MongoDatabase database = client.getDatabase("SnowMemo");
            MongoCollection<Document> collection = database.getCollection("Users");

            if (storedUsernames != null && !storedUsernames.isEmpty()) {
                for (String username : storedUsernames) {
                    String password = usersKeyStore.get(username);
                    if (password != null && !password.isEmpty() && !username.equals("__________lastuser__________")) {
                        // Query database to verify user exists
                        Document query = new Document("user.userName",
                                new Document("$regex", "^" + Pattern.quote(username) + "$")
                                        .append("$options", "i"));

                        Document user = collection.find(query).first();

                        if (user == null) {
                            // User in keystore but not in database - remove from keystore
                            usersKeyStore.remove(username);
                            System.out.println("Removed invalid user from keystore (not found in DB): " + username);
                            continue;
                        }

                        // User exists in database - load their data
                        Document userDoc = (Document) user.get("user");
                        String email = userDoc.getString("email");
                        Long creationTime = userDoc.getLong("creationTime");
                        String bio = userDoc.getString("bio");

                        // Handle null creationTime
                        if (creationTime == null) {
                            creationTime = System.currentTimeMillis();
                        }

                        boolean exists = false;
                        synchronized (users) {
                            for (User existingUser : users) {
                                if (existingUser.userName.toLowerCase(Locale.ROOT).equals(username.toLowerCase())) {
                                    exists = true;
                                    System.out.println("User already in list, skipping: " + username);
                                    break;
                                }
                            }

                            if (!exists) {
                                User storedUser = new User(username, password, email != null ? email : "", creationTime);
                                storedUser.bio = bio != null ? bio : "";
                                users.add(storedUser);
                                System.out.println("Loaded stored user from DB: " + username + " (email: " + email + ", created: " + creationTime + ")");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading stored users: " + e.getMessage());
        }
    }

    /**
     * Constructor with creationTime
     */
    public User(String userName, String password, String email, long creationTime) {
        this.userName = userName;
        this.password = password;
        this.email = email;
        this.creationTime = creationTime;

        // Create the nested user document with proper structure
        Document userDoc = new Document();
        userDoc.put("userName", userName);
        userDoc.put("password", password);
        userDoc.put("email", email);
        userDoc.put("creationTime", creationTime);
        userDoc.put("memo", new Document());
        userDoc.put("bio", "");
        put("user", userDoc);
    }

    /**
     * Constructor without creationTime - sets to current time
     */
    public User(String userName, String password, String email) {
        this(userName, password, email, System.currentTimeMillis());
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static List<User> getUsers() {
        return users;
    }

    /**
     * Login method - async with callback
     */
    public static CompletableFuture<Boolean> logInAsync(String userName, String pass) {
        return CompletableFuture.supplyAsync(() -> {
            if (!checkForConnection()) {
                System.err.println("No internet connection");
                return false;
            }

            if (!checkPassWord(userName, pass)) {
                System.err.println("Invalid password");
                return false;
            }

            try {
                MongoClient client = getMongoClient();
                MongoDatabase database = client.getDatabase("SnowMemo");
                MongoCollection<Document> collection = database.getCollection("Users");

                Document query = new Document("user.userName",
                        new Document("$regex", "^" + Pattern.quote(userName) + "$")
                                .append("$options", "i"));

                Document user = collection.find(query).first();

                if (user != null && user.containsKey("user")) {
                    Document userDoc = (Document) user.get("user");

                    // Manually check if user already exists by username
                    User existingUser = null;
                    synchronized (users) {
                        for (User u : users) {
                            if (u.userName.toLowerCase(Locale.ROOT).equals(userName.toLowerCase())) {
                                existingUser = u;
                                break;
                            }
                        }

                        if (existingUser != null) {
                            // Update existing user data
                            existingUser.password = pass;
                            existingUser.email = userDoc.getString("email") != null ? userDoc.getString("email") : "";
                            existingUser.bio = userDoc.getString("bio") != null ? userDoc.getString("bio") : "";
                            Long creationTime = userDoc.getLong("creationTime");
                            existingUser.creationTime = creationTime != null ? creationTime : System.currentTimeMillis();
                            currentUser = existingUser;
                            System.out.println("Logged in with existing user: " + userName);
                        } else {
                            // Create new user entry
                            String email = userDoc.getString("email");
                            Long creationTime = userDoc.getLong("creationTime");

                            if (creationTime == null) {
                                creationTime = System.currentTimeMillis();
                            }

                            User updatingUser = new User(userName, pass, email != null ? email : "", creationTime);
                            updatingUser.bio = userDoc.getString("bio") != null ? userDoc.getString("bio") : "";
                            users.add(updatingUser);
                            currentUser = updatingUser;
                            System.out.println("Created new user entry: " + userName);
                        }
                    }

                    // Store user credentials in keystore
                    try {
                        usersKeyStore.put(userName, pass);
                        usersKeyStore.put("__________lastuser__________", userName);
                        System.out.println("User credentials stored in keystore");
                    } catch (Exception e) {
                        System.err.println("Error storing credentials in keystore: " + e.getMessage());
                    }

                    return true;
                }
            } catch (Exception e) {
                System.err.println("Error during login: " + e.getMessage());
            }
            return false;
        }, executorService);
    }

    /**
     * Synchronous login method for compatibility
     */
    public static void logIn(String userName, String pass) {
        logInAsync(userName, pass).join();
    }

    public static boolean checkForUser(String username) {
        if (!checkForConnection()) {
            return false;
        }

        try {
            MongoClient client = getMongoClient();
            MongoDatabase database = client.getDatabase("SnowMemo");
            MongoCollection<Document> collection = database.getCollection("Users");

            Document query = new Document("user.userName",
                    new Document("$regex", "^" + Pattern.quote(username) + "$")
                            .append("$options", "i"));

            Document user = collection.find(query).first();

            return user == null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void syncData() {
        // TODO: Implement data synchronization
    }

    public static boolean checkForConnection() {
        return connection;
    }

    private static MongoClient mongoClient = null;

    private static synchronized MongoClient getMongoClient() {
        if (mongoClient == null) {
            String connectionString = "mongodb+srv://pohjunzhematthew:GmP3824wVr8BV9Cp@snowmemo.kyxexhg.mongodb.net/?retryWrites=true&w=majority&appName=SnowMemo";
            ServerApi serverApi = ServerApi.builder()
                    .version(ServerApiVersion.V1)
                    .build();
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .serverApi(serverApi)
                    .build();
            mongoClient = MongoClients.create(settings);
        }
        return mongoClient;
    }

    public static boolean checkPassWord(String username, String pass) {
        if (!checkForConnection()) {
            return false;
        }

        try {
            MongoClient client = getMongoClient();
            MongoDatabase database = client.getDatabase("SnowMemo");
            MongoCollection<Document> collection = database.getCollection("Users");

            Document query = new Document("user.userName",
                    new Document("$regex", "^" + Pattern.quote(username) + "$")
                            .append("$options", "i"));

            Document user = collection.find(query).first();

            if (user != null && user.containsKey("user")) {
                Document userDoc = (Document) user.get("user");
                return pass.equals(userDoc.getString("password"));
            }

            return false;
        } catch (MongoException e) {
            throw new RuntimeException(e);
        }
    }

    public static LogInLayout getLogInLayout() {
        return logInLayout;
    }

    public static SignUpLayout getSignUpLayout() {
        return signUpLayout;
    }

    public static UserMenuLayout getUserMenuLayout() {
        return userMenuLayout;
    }

    public String getUsername() {
        return userName;
    }

    public static void logOut() {
        currentUser = null;
        usersKeyStore.put("__________lastuser__________","__________nullable__________");
    }

    /**
     * Sign up method - async
     */
    public static CompletableFuture<Boolean> signUpAsync(String name, String email, String password) {
        return CompletableFuture.supplyAsync(() -> {
            if (!checkForConnection()) {
                System.err.println("No internet connection - cannot sign up");
                return false;
            }

            if (!checkForUser(name)) {
                System.err.println("Username already taken");
                return false;
            }

            try {
                MongoClient client = getMongoClient();
                MongoDatabase database = client.getDatabase("SnowMemo");
                MongoCollection<Document> collection = database.getCollection("Users");

                // Create user with current timestamp
                User user = new User(name, password, email, System.currentTimeMillis());
                collection.insertOne(user);
                System.out.println("User inserted into database: " + name);

                synchronized (users) {
                    boolean exists = false;
                    for (User existingUser : users) {
                        if (existingUser.userName.toLowerCase(Locale.ROOT).equals(name.toLowerCase())) {
                            exists = true;
                            existingUser.password = password;
                            existingUser.email = email;
                            existingUser.creationTime = user.creationTime;
                            currentUser = existingUser;
                            System.out.println("Updated existing user during signup: " + name);
                            break;
                        }
                    }

                    if (!exists) {
                        users.add(user);
                        currentUser = user;
                        System.out.println("Added new user to local list during signup: " + name);
                    }
                }

                // Store user credentials in keystore
                try {
                    usersKeyStore.put(name, password);
                    usersKeyStore.put("__________lastuser__________", name);
                    System.out.println("User credentials stored in keystore during signup");
                } catch (Exception e) {
                    System.err.println("Error storing credentials in keystore: " + e.getMessage());
                }

                return true;
            } catch (Exception e) {
                System.err.println("Error during signup: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }, executorService);
    }

    /**
     * Synchronous signup for compatibility
     */
    public static void signUp(String name, String email, String password) {
        signUpAsync(name, email, password).join();
    }

    /**
     * Deletes the current user's account from the database - async
     */
    public static CompletableFuture<Boolean> deleteAccountAsync() {
        if (!checkForConnection() || currentUser == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                MongoClient client = getMongoClient();
                MongoDatabase database = client.getDatabase("SnowMemo");
                MongoCollection<Document> collection = database.getCollection("Users");

                Document query = new Document("user.userName",
                        new Document("$regex", "^" + Pattern.quote(currentUser.userName) + "$")
                                .append("$options", "i"));

                collection.deleteOne(query);

                try {
                    usersKeyStore.remove(currentUser.userName);
                    System.out.println("User credentials removed from keystore");
                } catch (Exception e) {
                    System.err.println("Error removing credentials from keystore: " + e.getMessage());
                }

                synchronized (users) {
                    users.remove(currentUser);
                }
                currentUser = null;

                return true;
            } catch (MongoException e) {
                System.err.println("Error deleting account: " + e.getMessage());
                return false;
            }
        }, executorService);
    }

    public static boolean deleteAccount() {
        return deleteAccountAsync().join();
    }

    /**
     * Changes the current user's password - async
     */
    public static CompletableFuture<Boolean> changePasswordAsync(String oldPassword, String newPassword) {
        if (!checkForConnection() || currentUser == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (!checkPassWord(currentUser.userName, oldPassword)) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                MongoClient client = getMongoClient();
                MongoDatabase database = client.getDatabase("SnowMemo");
                MongoCollection<Document> collection = database.getCollection("Users");

                Document query = new Document("user.userName",
                        new Document("$regex", "^" + Pattern.quote(currentUser.userName) + "$")
                                .append("$options", "i"));

                Document update = new Document("$set",
                        new Document("user.password", newPassword));

                collection.updateOne(query, update);

                currentUser.password = newPassword;

                try {
                    usersKeyStore.put(currentUser.userName, newPassword);
                    System.out.println("Password updated in keystore");
                } catch (Exception e) {
                    System.err.println("Error updating password in keystore: " + e.getMessage());
                }

                return true;
            } catch (MongoException e) {
                System.err.println("Error changing password: " + e.getMessage());
                return false;
            }
        }, executorService);
    }

    public static boolean changePassword(String oldPassword, String newPassword) {
        return changePasswordAsync(oldPassword, newPassword).join();
    }

    /**
     * Changes the current user's username - async
     */
    public static CompletableFuture<Boolean> changeUsernameAsync(String newUsername, String password) {
        if (!checkForConnection() || currentUser == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (!checkPassWord(currentUser.userName, password)) {
            return CompletableFuture.completedFuture(false);
        }

        if (!checkForUser(newUsername)) {
            System.err.println("Username already taken");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                MongoClient client = getMongoClient();
                MongoDatabase database = client.getDatabase("SnowMemo");
                MongoCollection<Document> collection = database.getCollection("Users");

                Document query = new Document("user.userName",
                        new Document("$regex", "^" + Pattern.quote(currentUser.userName) + "$")
                                .append("$options", "i"));

                Document update = new Document("$set",
                        new Document("user.userName", newUsername));

                collection.updateOne(query, update);

                try {
                    String oldUsername = currentUser.userName;
                    usersKeyStore.remove(oldUsername);
                    usersKeyStore.put(newUsername, password);
                    usersKeyStore.put("__________lastuser__________", newUsername);
                    System.out.println("Username updated in keystore");
                } catch (Exception e) {
                    System.err.println("Error updating username in keystore: " + e.getMessage());
                }

                currentUser.userName = newUsername;

                return true;
            } catch (MongoException e) {
                System.err.println("Error changing username: " + e.getMessage());
                return false;
            }
        }, executorService);
    }

    public static boolean changeUsername(String newUsername, String password) {
        return changeUsernameAsync(newUsername, password).join();
    }

    /**
     * Changes the current user's email - async
     */
    public static CompletableFuture<Boolean> changeEmailAsync(String newEmail, String password) {
        if (!checkForConnection() || currentUser == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (!checkPassWord(currentUser.userName, password)) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                MongoClient client = getMongoClient();
                MongoDatabase database = client.getDatabase("SnowMemo");
                MongoCollection<Document> collection = database.getCollection("Users");

                Document query = new Document("user.userName",
                        new Document("$regex", "^" + Pattern.quote(currentUser.userName) + "$")
                                .append("$options", "i"));

                Document update = new Document("$set",
                        new Document("user.email", newEmail));

                collection.updateOne(query, update);

                currentUser.email = newEmail;

                return true;
            } catch (MongoException e) {
                System.err.println("Error changing email: " + e.getMessage());
                return false;
            }
        }, executorService);
    }

    public static boolean changeEmail(String newEmail, String password) {
        return changeEmailAsync(newEmail, password).join();
    }

    /**
     * Changes the current user's bio - async
     */
    public static CompletableFuture<Boolean> changeBioAsync(String newBio) {
        if (!checkForConnection() || currentUser == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                MongoClient client = getMongoClient();
                MongoDatabase database = client.getDatabase("SnowMemo");
                MongoCollection<Document> collection = database.getCollection("Users");

                Document query = new Document("user.userName",
                        new Document("$regex", "^" + Pattern.quote(currentUser.userName) + "$")
                                .append("$options", "i"));

                Document update = new Document("$set",
                        new Document("user.bio", newBio));

                collection.updateOne(query, update);

                currentUser.bio = newBio;

                return true;
            } catch (MongoException e) {
                System.err.println("Error changing bio: " + e.getMessage());
                return false;
            }
        }, executorService);
    }

    public static boolean changeBio(String newBio) {
        return changeBioAsync(newBio).join();
    }

    public String getBio() {
        return bio != null ? bio : "";
    }

    public String getEmail() {
        return email != null ? email : "";
    }

    public static String getStoredPassword(String username) {
        try {
            return usersKeyStore.get(username);
        } catch (Exception e) {
            System.err.println("Error retrieving password from keystore: " + e.getMessage());
            return null;
        }
    }

    public static boolean hasStoredCredentials(String username) {
        try {
            return usersKeyStore.containsKey(username);
        } catch (Exception e) {
            System.err.println("Error checking keystore: " + e.getMessage());
            return false;
        }
    }

    public static java.util.Set<String> getStoredUsernames() {
        try {
            return usersKeyStore.keySet();
        } catch (Exception e) {
            System.err.println("Error getting usernames from keystore: " + e.getMessage());
            return new java.util.HashSet<>();
        }
    }

    public static boolean autoLogin(String username) {
        String storedPassword = getStoredPassword(username);
        if (storedPassword != null) {
            logIn(username, storedPassword);
            return currentUser != null;
        }
        return false;
    }

    public static SettingsLayout getSettingsLayout(){
        return settingsLayout;
    }

    public String getPassword() {
        return password;
    }

    public long getCreationTime() {
        return creationTime;
    }
}