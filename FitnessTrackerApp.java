import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class FitnessTrackerApp extends JFrame {
    static class User {
        Integer id;
        String username;
        String password;
        String role; 
        Double heightCm;
        Double weightKg;
        String healthNotes;

        User(Integer id, String username, String password, String role,
             Double heightCm, Double weightKg, String healthNotes) {
            this.id = id; this.username = username; this.password = password;
            this.role = role; this.heightCm = heightCm; this.weightKg = weightKg;
            this.healthNotes = healthNotes;
        }
    }

    static class Workout {
        Integer id;
        Integer userId;
        String date;
        int durationSeconds;
        double calories;
        String note;
        Workout(Integer id, Integer userId, String date, int durationSeconds, double calories, String note) {
            this.id = id; this.userId = userId; this.date = date;
            this.durationSeconds = durationSeconds; this.calories = calories; this.note = note;
        }
    }

    static class Instruction {
        Integer id; Integer userId; Integer adminId;
        String date; String text;
        Instruction(Integer id, Integer userId, Integer adminId, String date, String text) {
            this.id=id; this.userId=userId; this.adminId=adminId; this.date=date; this.text=text;
        }
    }

    static class DB {
        private static final String URL = "jdbc:sqlite:ftracker.db";
        DB() { init(); }
        private void init() {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                JOptionPane.showMessageDialog(null, "SQLite JDBC driver not found. Make sure jar is on classpath.", "DB Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            try (Connection c = DriverManager.getConnection(URL);
                 Statement st = c.createStatement()) {

                st.execute("PRAGMA foreign_keys = ON;");
                st.execute("CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "username TEXT UNIQUE NOT NULL, password TEXT NOT NULL, role TEXT NOT NULL, " +
                        "height_cm REAL, weight_kg REAL, health_notes TEXT)");

                st.execute("CREATE TABLE IF NOT EXISTS workouts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, date TEXT NOT NULL, " +
                        "duration_seconds INTEGER NOT NULL, calories REAL NOT NULL, note TEXT, " +
                        "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE)");

                st.execute("CREATE TABLE IF NOT EXISTS instructions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, admin_id INTEGER NOT NULL, date TEXT NOT NULL, text TEXT NOT NULL, " +
                        "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(admin_id) REFERENCES users(id) ON DELETE CASCADE)");

                PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ?");
                ps.setString(1, "admin");
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    PreparedStatement ins = c.prepareStatement("INSERT INTO users(username,password,role) VALUES(?,?,?)");
                    ins.setString(1, "admin");
                    ins.setString(2, "admin123"); 
                    ins.setString(3, "admin");
                    ins.executeUpdate();
                    ins.close();
                }
                rs.close(); ps.close();

            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "DB init error: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        }

        User getUserByUsername(String username) {
            try (Connection c = DriverManager.getConnection(URL)) {
                PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE username = ?");
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    User u = new User(rs.getInt("id"), rs.getString("username"), rs.getString("password"),
                            rs.getString("role"),
                            rs.getObject("height_cm") == null ? null : rs.getDouble("height_cm"),
                            rs.getObject("weight_kg") == null ? null : rs.getDouble("weight_kg"),
                            rs.getString("health_notes"));
                    rs.close(); ps.close(); return u;
                }
                rs.close(); ps.close();
            } catch (SQLException e) { e.printStackTrace(); }
            return null;
        }

        Integer createUser(String username, String password, String role) {
            try (Connection c = DriverManager.getConnection(URL)) {
                PreparedStatement ps = c.prepareStatement("INSERT INTO users(username,password,role) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, username); ps.setString(2, password); ps.setString(3, role);
                ps.executeUpdate();
                ResultSet rk = ps.getGeneratedKeys();
                if (rk.next()) { int id = rk.getInt(1); rk.close(); ps.close(); return id; }
            } catch (SQLException e) {}
            return null;
        }

        List<User> listUsers() {
            List<User> out = new ArrayList<>();
            try (Connection c = DriverManager.getConnection(URL);
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM users ORDER BY id DESC")) {
                while (rs.next()) {
                    out.add(new User(rs.getInt("id"), rs.getString("username"), rs.getString("password"),
                            rs.getString("role"),
                            rs.getObject("height_cm")==null ? null : rs.getDouble("height_cm"),
                            rs.getObject("weight_kg")==null ? null : rs.getDouble("weight_kg"),
                            rs.getString("health_notes")));
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return out;
        }

        boolean updateProfile(int userId, Double heightCm, Double weightKg, String notes) {
            try (Connection c = DriverManager.getConnection(URL)) {
                PreparedStatement ps = c.prepareStatement("UPDATE users SET height_cm = ?, weight_kg = ?, health_notes = ? WHERE id = ?");
                if (heightCm == null) ps.setNull(1, Types.REAL); else ps.setDouble(1, heightCm);
                if (weightKg == null) ps.setNull(2, Types.REAL); else ps.setDouble(2, weightKg);
                ps.setString(3, notes);
                ps.setInt(4, userId);
                boolean ok = ps.executeUpdate() > 0;
                ps.close(); return ok;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }

        Integer addWorkout(int userId, int durationSeconds, double calories, String note) {
            try (Connection c = DriverManager.getConnection(URL)) {
                PreparedStatement ps = c.prepareStatement("INSERT INTO workouts(user_id,date,duration_seconds,calories,note) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, userId); ps.setString(2, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()));
                ps.setInt(3, durationSeconds); ps.setDouble(4, calories); ps.setString(5, note);
                ps.executeUpdate();
                ResultSet rk = ps.getGeneratedKeys();
                if (rk.next()) { int id = rk.getInt(1); rk.close(); ps.close(); return id; }
            } catch (SQLException e) { e.printStackTrace(); }
            return null;
        }

        List<Workout> getWorkoutsForUser(int userId) {
            List<Workout> out = new ArrayList<>();
            try (Connection c = DriverManager.getConnection(URL);
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM workouts WHERE user_id = ? ORDER BY date DESC")) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    out.add(new Workout(rs.getInt("id"), rs.getInt("user_id"), rs.getString("date"),
                            rs.getInt("duration_seconds"), rs.getDouble("calories"), rs.getString("note")));
                }
                rs.close(); ps.close();
            } catch (SQLException e) { e.printStackTrace(); }
            return out;
        }

        Integer addInstruction(int userId, int adminId, String text) {
            try (Connection c = DriverManager.getConnection(URL);
                 PreparedStatement ps = c.prepareStatement("INSERT INTO instructions(user_id,admin_id,date,text) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId); ps.setInt(2, adminId); ps.setString(3, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())); ps.setString(4, text);
                ps.executeUpdate(); ResultSet rk = ps.getGeneratedKeys(); if (rk.next()) { int id = rk.getInt(1); rk.close(); ps.close(); return id; }
            } catch (SQLException e) { e.printStackTrace(); }
            return null;
        }

        List<Instruction> getInstructionsForUser(int userId) {
            List<Instruction> out = new ArrayList<>();
            try (Connection c = DriverManager.getConnection(URL);
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM instructions WHERE user_id = ? ORDER BY date DESC")) {
                ps.setInt(1, userId); ResultSet rs = ps.executeQuery();
                while (rs.next()) out.add(new Instruction(rs.getInt("id"), rs.getInt("user_id"), rs.getInt("admin_id"), rs.getString("date"), rs.getString("text")));
                rs.close(); ps.close();
            } catch (SQLException e) { e.printStackTrace(); }
            return out;
        }

    } 
    private final DB db = new DB();
    private User currentUser = null;

    private final JTextField loginUserField = new JTextField();
    private final JPasswordField loginPassField = new JPasswordField();

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    private final DefaultTableModel usersModel = new DefaultTableModel(new String[]{"ID","Username","Role","Height(cm)","Weight(kg)","Notes"},0);
    private final JTable usersTable = new JTable(usersModel);
    private final JTextArea adminInstructionArea = new JTextArea(4,40);

    private final JLabel userWelcome = new JLabel();
    private final JTextField heightField = new JTextField();
    private final JTextField weightField = new JTextField();
    private final JTextArea healthNotesArea = new JTextArea(3,30);
    private final DefaultTableModel workoutsModel = new DefaultTableModel(new String[]{"Date","Duration","Calories","Note"}, 0);
    private final JTable workoutsTable = new JTable(workoutsModel);
    private final DefaultTableModel instrModel = new DefaultTableModel(new String[]{"Date","From Admin","Instruction"},0);
    private final JTable instrTable = new JTable(instrModel);

    private LocalDateTime timerStart = null;
    private javax.swing.Timer uiTimer = null;
    private final JLabel timerLabel = new JLabel("00:00:00");
    private int accumulatedSeconds = 0;

    public FitnessTrackerApp() {
        super("Fitness Tracker - Admin & User");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        JPanel login = new JPanel(new GridLayout(2,3,6,6));
        login.add(new JLabel("Username:")); login.add(loginUserField); login.add(new JLabel(""));
        login.add(new JLabel("Password:")); login.add(loginPassField);
        JButton loginBtn = new JButton("Login");
        JButton regUserBtn = new JButton("Create User");
        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightBtns.add(regUserBtn); rightBtns.add(loginBtn);
        top.add(login, BorderLayout.WEST);
        top.add(rightBtns, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        cardPanel.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        cardPanel.add(new JLabel("<html><h2>Welcome — please log in (admin: admin/admin123)</h2></html>"), "WELCOME");
        cardPanel.add(createAdminPanel(), "ADMIN");
        cardPanel.add(createUserPanel(), "USER");
        add(cardPanel, BorderLayout.CENTER);
        cards.show(cardPanel, "WELCOME");

        loginBtn.addActionListener(e -> doLogin());
        regUserBtn.addActionListener(e -> createUserDialog());

        uiTimer = new javax.swing.Timer(1000, e -> updateTimerLabel());

        setVisible(true);
    }

    private JPanel createAdminPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));

        JPanel left = new JPanel(new BorderLayout(6,6));
        left.add(new JLabel("<html><b>Users</b></html>"), BorderLayout.NORTH);
        usersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        left.add(new JScrollPane(usersTable), BorderLayout.CENTER);

        JPanel userBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("Refresh");
        JButton viewProfileBtn = new JButton("View/Edit Profile");
        JButton sendInstrBtn = new JButton("Send Instruction");
        JButton deleteUserBtn = new JButton("Delete User");
        userBtns.add(refreshBtn); userBtns.add(viewProfileBtn); userBtns.add(sendInstrBtn); userBtns.add(deleteUserBtn);
        left.add(userBtns, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(6,6));
        right.add(new JLabel("<html><b>Compose instruction to selected user</b></html>"), BorderLayout.NORTH);
        adminInstructionArea.setLineWrap(true); adminInstructionArea.setWrapStyleWord(true);
        right.add(new JScrollPane(adminInstructionArea), BorderLayout.CENTER);

        JPanel instrBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        instrBtns.add(sendInstrBtn);
        right.add(instrBtns, BorderLayout.SOUTH);

        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.CENTER);

        refreshBtn.addActionListener(e -> loadUsers());
        viewProfileBtn.addActionListener(e -> editSelectedUserProfile());
        sendInstrBtn.addActionListener(e -> {
            int sel = usersTable.getSelectedRow();
            if (sel < 0) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
            int uid = Integer.parseInt(usersModel.getValueAt(sel,0).toString());
            String text = adminInstructionArea.getText().trim();
            if (text.isEmpty()) { JOptionPane.showMessageDialog(this,"Enter instruction text."); return; }
            db.addInstruction(uid, currentUser.id, text);
            JOptionPane.showMessageDialog(this, "Instruction sent.");
            adminInstructionArea.setText("");
        });
        deleteUserBtn.addActionListener(e -> deleteSelectedUser());
        loadUsers();

        return p;
    }

    private JPanel createUserPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));

        JPanel top = new JPanel(new BorderLayout(8,8));
        userWelcome.setFont(userWelcome.getFont().deriveFont(16f));
        top.add(userWelcome, BorderLayout.NORTH);

        JPanel profile = new JPanel(new GridLayout(2,4,6,6));
        profile.add(new JLabel("Height (cm):")); profile.add(heightField);
        profile.add(new JLabel("Weight (kg):")); profile.add(weightField);
        profile.add(new JLabel("Health notes (allergies / issues):")); profile.add(new JScrollPane(healthNotesArea));
        JButton saveProfileBtn = new JButton("Save Profile");
        profile.add(saveProfileBtn);
        top.add(profile, BorderLayout.CENTER);

        JPanel center = new JPanel(new GridLayout(1,2,8,8));
        JPanel left = new JPanel(new BorderLayout(6,6));
        left.add(new JLabel("<html><b>Your Workouts</b></html>"), BorderLayout.NORTH);
        left.add(new JScrollPane(workoutsTable), BorderLayout.CENTER);

        JPanel timerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton startBtn = new JButton("Start");
        JButton stopBtn = new JButton("Stop & Save");
        timerPanel.add(startBtn); timerPanel.add(stopBtn); timerPanel.add(timerLabel);
        left.add(timerPanel, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(6,6));
        right.add(new JLabel("<html><b>Trainer Instructions</b></html>"), BorderLayout.NORTH);
        right.add(new JScrollPane(instrTable), BorderLayout.CENTER);
        JButton refreshInstr = new JButton("Refresh Instructions");
        right.add(refreshInstr, BorderLayout.SOUTH);

        center.add(left); center.add(right);

        JPanel bottom = new JPanel(new BorderLayout(6,6));
        JTextArea recArea = new JTextArea(4,60);
        recArea.setEditable(false); recArea.setLineWrap(true); recArea.setWrapStyleWord(true);
        bottom.add(new JLabel("<html><b>Recommendations</b></html>"), BorderLayout.NORTH);
        bottom.add(new JScrollPane(recArea), BorderLayout.CENTER);
        JButton exportBtn = new JButton("Export Workouts CSV");
        bottom.add(exportBtn, BorderLayout.SOUTH);

        p.add(top, BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        saveProfileBtn.addActionListener(e -> {
            try {
                Double h = heightField.getText().trim().isEmpty() ? null : Double.parseDouble(heightField.getText().trim());
                Double w = weightField.getText().trim().isEmpty() ? null : Double.parseDouble(weightField.getText().trim());
                String notes = healthNotesArea.getText().trim();
                boolean ok = db.updateProfile(currentUser.id, h, w, notes);
                if (ok) { JOptionPane.showMessageDialog(this, "Profile saved."); loadUserData(currentUser.id); }
                else JOptionPane.showMessageDialog(this, "Failed to save profile.");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter valid numbers for height and weight.");
            }
        });

        startBtn.addActionListener(e -> startTimer(recArea));
        stopBtn.addActionListener(e -> stopTimerAndSave(recArea));
        refreshInstr.addActionListener(e -> loadInstructionsForCurrent());
        exportBtn.addActionListener(e -> exportWorkoutsCSV());

        return p;
    }

    private void doLogin() {
        String u = loginUserField.getText().trim();
        String p = new String(loginPassField.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter username and password."); return; }
        User found = db.getUserByUsername(u);
        if (found == null || !found.password.equals(p)) { JOptionPane.showMessageDialog(this, "Invalid credentials."); return; }
        currentUser = found;
        if ("admin".equals(found.role)) {
            JOptionPane.showMessageDialog(this, "Logged in as admin: " + found.username);
            loadUsers();
            cards.show(cardPanel, "ADMIN");
        } else {
            JOptionPane.showMessageDialog(this, "Logged in as user: " + found.username);
            loadUserData(found.id);
            cards.show(cardPanel, "USER");
        }
        loginUserField.setText(""); loginPassField.setText("");
    }

    private void createUserDialog() {
        JPanel p = new JPanel(new GridLayout(3,2,6,6));
        JTextField uname = new JTextField();
        JPasswordField upass = new JPasswordField();
        JComboBox<String> role = new JComboBox<>(new String[]{"user"});
        p.add(new JLabel("Username:")); p.add(uname);
        p.add(new JLabel("Password:")); p.add(upass);
        p.add(new JLabel("Role:")); p.add(role);
        int opt = JOptionPane.showConfirmDialog(this, p, "Create User", JOptionPane.OK_CANCEL_OPTION);
        if (opt != JOptionPane.OK_OPTION) return;
        String u = uname.getText().trim(); String pass = new String(upass.getPassword()).trim();
        if (u.isEmpty() || pass.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter username & password."); return; }
        Integer id = db.createUser(u, pass, "user");
        if (id == null) JOptionPane.showMessageDialog(this, "Unable to create user (maybe username exists).");
        else JOptionPane.showMessageDialog(this, "User created: " + u + " (id=" + id + ")");
    }

    private void loadUsers() {
        usersModel.setRowCount(0);
        for (User u : db.listUsers()) {
            usersModel.addRow(new Object[]{u.id, u.username, u.role, u.heightCm==null?"":u.heightCm, u.weightKg==null?"":u.weightKg, u.healthNotes==null?"":u.healthNotes});
        }
    }

    private void editSelectedUserProfile() {
        int sel = usersTable.getSelectedRow();
        if (sel < 0) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
        int uid = Integer.parseInt(usersModel.getValueAt(sel,0).toString());
        User u = db.getUserByUsername(usersModel.getValueAt(sel,1).toString());
        if (u==null) { JOptionPane.showMessageDialog(this, "User not found."); return; }
        JPanel p = new JPanel(new GridLayout(3,2,6,6));
        JTextField h = new JTextField(u.heightCm==null?"":u.heightCm.toString());
        JTextField w = new JTextField(u.weightKg==null?"":u.weightKg.toString());
        JTextArea notes = new JTextArea(u.healthNotes==null?"":u.healthNotes,3,30);
        p.add(new JLabel("Height cm:")); p.add(h);
        p.add(new JLabel("Weight kg:")); p.add(w);
        p.add(new JLabel("Health notes:")); p.add(new JScrollPane(notes));
        int opt = JOptionPane.showConfirmDialog(this, p, "Edit Profile for " + u.username, JOptionPane.OK_CANCEL_OPTION);
        if (opt != JOptionPane.OK_OPTION) return;
        try {
            Double hh = h.getText().trim().isEmpty()?null:Double.parseDouble(h.getText().trim());
            Double ww = w.getText().trim().isEmpty()?null:Double.parseDouble(w.getText().trim());
            db.updateProfile(uid, hh, ww, notes.getText().trim());
            loadUsers();
            JOptionPane.showMessageDialog(this, "Profile updated.");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter valid numbers.");
        }
    }

    private void deleteSelectedUser() {
        int sel = usersTable.getSelectedRow();
        if (sel < 0) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
        int uid = Integer.parseInt(usersModel.getValueAt(sel,0).toString());
        int confirm = JOptionPane.showConfirmDialog(this, "Delete user id=" + uid + " ? This will remove their data.", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try (Connection c = DriverManager.getConnection(DB.URL)) {
            PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?");
            ps.setInt(1, uid); ps.executeUpdate(); ps.close();
            loadUsers();
            JOptionPane.showMessageDialog(this, "User deleted.");
        } catch (SQLException e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, "Delete failed: " + e.getMessage()); }
    }

    private void loadUserData(int userId) {
        User u = null;
        try (Connection c = DriverManager.getConnection(DB.URL)) {
            PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE id = ?");
            ps.setInt(1, userId); ResultSet rs = ps.executeQuery();
            if (rs.next()) u = new User(rs.getInt("id"), rs.getString("username"), rs.getString("password"), rs.getString("role"),
                    rs.getObject("height_cm")==null?null:rs.getDouble("height_cm"),
                    rs.getObject("weight_kg")==null?null:rs.getDouble("weight_kg"),
                    rs.getString("health_notes"));
            rs.close(); ps.close();
        } catch (SQLException e) { e.printStackTrace(); }
        if (u==null) { JOptionPane.showMessageDialog(this, "Couldn't load your profile."); return; }
        currentUser = u;
        userWelcome.setText("Hello, " + u.username + " (ID: " + u.id + ")");
        heightField.setText(u.heightCm==null?"":u.heightCm.toString());
        weightField.setText(u.weightKg==null?"":u.weightKg.toString());
        healthNotesArea.setText(u.healthNotes==null?"":u.healthNotes);        refreshWorkouts();
        loadInstructionsForCurrent();
        computeAndShowRecommendations();
    }

    private void refreshWorkouts() {
        workoutsModel.setRowCount(0);
        for (Workout w : db.getWorkoutsForUser(currentUser.id)) {
            workoutsModel.addRow(new Object[]{ w.date, formatDuration(w.durationSeconds), String.format("%.1f", w.calories), w.note==null?"":w.note });
        }
    }

    private void loadInstructionsForCurrent() {
        instrModel.setRowCount(0);
        List<Instruction> list = db.getInstructionsForUser(currentUser.id);
        for (Instruction ins : list) {
            String adminName = "admin";
            try (Connection c = DriverManager.getConnection(DB.URL)) {
                PreparedStatement ps = c.prepareStatement("SELECT username FROM users WHERE id = ?");
                ps.setInt(1, ins.adminId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) adminName = rs.getString(1);
                rs.close(); ps.close();
            } catch (SQLException e) { e.printStackTrace(); }
            instrModel.addRow(new Object[]{ ins.date, adminName, ins.text });
        }
    }

    private void computeAndShowRecommendations() {
        Double h = currentUser.heightCm;
        Double w = currentUser.weightKg;
        String notes = currentUser.healthNotes==null?"":currentUser.healthNotes.toLowerCase();
        StringBuilder sb = new StringBuilder();
        if (h==null || w==null) {
            sb.append("Set your height and weight to get personalized recommendations.\n");
        } else {
            double m = h / 100.0;
            double bmi = w / (m*m);
            sb.append(String.format("Your BMI: %.1f\n", bmi));
            if (bmi < 18.5) sb.append("Underweight: focus on gentle strength training and calorie-dense nutritious meals.\n");
            else if (bmi < 25) sb.append("Normal weight: good job. Mix cardio, strength, and mobility.\n");
            else if (bmi < 30) sb.append("Overweight: recommend regular moderate cardio and resistance training. Watch diet.\n");
            else sb.append("Obese: focus on low-impact cardio (walking, cycling) and consult a healthcare professional before intense exercise.\n");
        }
        if (!notes.isEmpty()) {
            sb.append("\nHealth notes: ").append(currentUser.healthNotes).append("\n");
            sb.append("Because of health notes, avoid high-intensity without clearance; favor low-impact and supervised workouts.\n");
        }
        JTextArea ta = new JTextArea(sb.toString(), 10, 40);
        ta.setEditable(false); ta.setWrapStyleWord(true); ta.setLineWrap(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Recommendations", JOptionPane.INFORMATION_MESSAGE);
    }

    private void startTimer(JTextArea recArea) {
        if (timerStart != null) { JOptionPane.showMessageDialog(this, "Timer already running."); return; }
        timerStart = LocalDateTime.now();
        uiTimer.start();
        timerLabel.setText("00:00:00");
        recArea.setText("Timer running... press Stop & Save when finished.");
    }

    private void updateTimerLabel() {
        if (timerStart == null) return;
        Duration d = Duration.between(timerStart, LocalDateTime.now());
        long s = d.getSeconds();
        timerLabel.setText(formatDuration((int)s));
    }

    private void stopTimerAndSave(JTextArea recArea) {
        if (timerStart == null) { JOptionPane.showMessageDialog(this, "Timer is not running."); return; }
        uiTimer.stop();
        Duration d = Duration.between(timerStart, LocalDateTime.now());
        int seconds = (int)d.getSeconds();
        timerStart = null;
        timerLabel.setText("00:00:00");
        String[] options = new String[]{"Low (MET 3.5 - walking)", "Moderate (MET 6 - jogging)", "High (MET 8 - running)", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, "Choose intensity to estimate calories burned:", "Calories", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        if (choice < 0 || choice == 3) return;
        double met = (choice==0?3.5: choice==1?6.0:8.0);
        double weightKg = currentUser.weightKg == null ? askWeightFallback() : currentUser.weightKg;
        if (weightKg <= 0) { JOptionPane.showMessageDialog(this, "No valid weight available to estimate calories."); return; }
        double hours = seconds / 3600.0;
        double calories = met * weightKg * hours;
        String note = JOptionPane.showInputDialog(this, "Optional note for this workout (e.g., 'morning run'):");
        Integer wid = db.addWorkout(currentUser.id, seconds, calories, note==null?"":note);
        if (wid != null) {
            JOptionPane.showMessageDialog(this, String.format("Saved workout: %s (%s) — estimated %.1f kcal", formatDuration(seconds), (choice==0?"Low":choice==1?"Moderate":"High"), calories));
            refreshWorkouts();
        } else JOptionPane.showMessageDialog(this, "Failed to save workout.");
        computeAndShowRecommendations();
    }

    private double askWeightFallback() {
        String s = JOptionPane.showInputDialog(this, "Enter your weight in kg for calorie estimate:");
        try { return Double.parseDouble(s); } catch (Exception e) { return -1; }
    }

    private String formatDuration(int seconds) {
        int h = seconds/3600; int m = (seconds%3600)/60; int s = seconds%60;
        return String.format("%02d:%02d:%02d", h,m,s);
    }

    private void exportWorkoutsCSV() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Workouts CSV");
        fc.setSelectedFile(new File("workouts_" + currentUser.username + ".csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try (BufferedWriter bw = Files.newBufferedWriter(f.toPath())) {
            bw.write("date,duration_seconds,calories,note");
            bw.newLine();
            for (Workout w : db.getWorkoutsForUser(currentUser.id)) {
                bw.write(String.format("\"%s\",%d,%.2f,\"%s\"", w.date, w.durationSeconds, w.calories, w.note==null?"":w.note));
                bw.newLine();
            }
            JOptionPane.showMessageDialog(this, "Exported to " + f.getAbsolutePath());
        } catch (IOException e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage()); }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new FitnessTrackerApp();
        });
    }
}
