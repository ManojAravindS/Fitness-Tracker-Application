📌 Fitness Tracker Application – Java Swing & SQLite
    A desktop-based Fitness Tracker Application built using Java Swing, SQLite, and JDBC, designed to 
help users manage their workouts, track calories, monitor physical progress, and receive trainer/admin recommendations.
The system also includes a complete Admin Panel for user management and instruction handling.

🚀 Features

👤 User Features
User login & authentication
Update profile (height, weight, health notes)
Start/stop workout timer
Automatic calorie calculation based on MET value
View workout history
Receive trainer/admin instructions
Export workouts to CSV
Real-time recommendations based on BMI

🛠 Admin Features
Admin login
View all users
Edit any user’s profile
Send instructions to users
Delete users
Monitor all user records
Manage system data

🗂 Tech Stack
Layer                    Technology
Language               Java (Swing GUI)
Database               SQLite (JDBC)
Architecture           MVC-inspired Modular Structure
File Operations        CSV Export
Libraries              sqlite-jdbc, Java Swing components

📦 Project Structure
FitnessTrackerApp/
│
├── FitnessTrackerApp.java         # Main application file
├── ftracker.db                    # SQLite database (auto-generated)
├── README.md                      # Project documentation
└── /resources                     # (optional) icons, screenshots




🔑 Default Credentials
Role      Username      Password
Admin      admin        admin123

Users can be created through the "Create User" button on the login interface.

🧠 How Calories Are Calculated
The system uses the standard MET formula:
Calories Burned = MET × Weight (kg) × Duration (hours)

Users select:
->Low Intensity
->Moderate
->High

The appropriate MET value is used:
->Low → 3.5
->Moderate → 6.0
->High → 8.0

⚙️ How to Run

1. Compile
javac -cp ".;sqlite-jdbc.jar" FitnessTrackerApp.java (Optional, the code is already compiled, the user will only need to run the code)

3. Run
java -cp ".;sqlite-jdbc.jar" FitnessTrackerApp

Database
The app automatically creates ftracker.db if it doesn’t exist.

🌟 Key Functional Modules
✔ Login & Authentication
Secure user access handled via SQLite database lookups.
✔ User Profile Management
Allows updating physical details and health notes.
✔ Workout Tracking
Start/stop timer with automatic calorie estimation.
✔ Trainer Instructions (Admin → User)
Admin sends personalized fitness instructions.
✔ CSV Export
Users can export workout logs.
✔ Admin Dashboard
Complete control over users, profiles, and system data.

🔮 Future Enhancements
Integration with wearables (Fitbit, Mi Band, etc.)
AI-based smart health recommendations
Graphical charts for progress tracking
Dark mode UI
Cloud sync
Mobile version (Android)

🤝 Contributing
Pull requests are welcome!
For major changes, please open an issue to discuss what you'd like to improve.

Just tell me!
