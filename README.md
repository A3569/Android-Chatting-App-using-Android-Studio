# Chatapp

## Introduction
Chatapp is a real-time messaging Android application using Kotlin to implement that enables users to communicate securely with their contacts. The app supports user registration, authentication, one-on-one messaging, profile management, and customizable settings.

### Key Features
- User authentication (login, registration, OTP verification)
- Real-time messaging with contacts
- User profile management
- Chat history with search functionality
- Settings customization
- Contact synchronization

### Getting Started
1. Launch the app and register a new account or log in
2. Grant necessary permissions when prompted (contacts, storage)
3. Start chatting by tapping the "+" button to select a contact
4. Access settings and profile management through the options menu

## Design Rationale

### Architectural Decisions
- **Activity-Based Navigation**: The app primarily uses Activities for different screens (LoginActivity, ChatActivity, etc.) to maintain clear separation of concerns and simplify the navigation flow.
- **Firebase Integration**: Firebase Authentication, Realtime Database, and Storage provide a robust backend for user management, real-time messaging, and media storage.
- **ViewBinding**: Implemented throughout the app to improve type safety and eliminate findViewById() calls, reducing potential null pointer exceptions.

### UI Design Choices
- **Custom Themes**: Different themes for specific activities (SettingsTheme, ProfileTheme, etc.) to provide visual distinction between app sections.
- **RecyclerView Implementation**: Used for efficient rendering of chats, messages, and contacts lists with custom adapters and ViewHolders.
- **SwipeToDelete**: Implemented with ItemTouchHelper for intuitive chat deletion with confirmation dialogs.

### Data Management
- **SharedPreferences**: Used for storing user preferences (notifications, privacy settings) with both local and cloud synchronization.
- **Firebase Realtime Database**: Structured with separate nodes for users, chats, and messages to optimize data access patterns.
- **Error Handling**: Comprehensive exception handling and error reporting throughout the app with user-friendly messages.

## Novel Features

### Enhanced Chat Management
- **Smart Chat Filtering**: The search functionality intelligently filters chats based on both message content and username matches.
- **Swipe Actions**: Intuitive swipe-to-delete functionality with visual feedback and undo capabilities.
- **Offline Support**: Basic offline functionality to view cached chats when network connectivity is unavailable.

### User Experience Optimizations
- **Optimized Rendering**: Custom RenderingHelper utility that configures hardware acceleration for smooth scrolling and animations.
- **Fault Tolerance**: Graceful recovery mechanisms for network issues and Firebase operation failures.
- **Context-Aware UI**: Adaptive UI elements that change based on the user's interaction history and preferences.

## Challenges and Future Improvements

### Development Challenges
- **Real-time Synchronization**: Ensuring consistent state between multiple devices required careful planning of database structure and listener management.
- **Resource Optimization**: Balancing real-time updates with battery and data usage considerations.
- **Permission Management**: Implementing proper permission handling across different Android versions while maintaining a smooth user experience.

### Future Improvements
- **Group Chat Support**: Extend the messaging functionality to support multi-user conversations.
- **End-to-End Encryption**: Implement additional security measures for message content.
- **Rich Media Messaging**: Enhance the chat experience with reactions, stickers, and improved media sharing capabilities.
- **Notification Channels**: Implement more granular notification controls with Android's notification channels.
- **Offline-First Architecture**: Further improve the offline experience with local caching and background synchronization.
## Testing

### Instrumented Tests
The app includes a comprehensive suite of instrumented tests to verify crucial application functionality on actual Android devices or emulators. These tests use the Espresso framework to simulate user interactions and validate UI elements.

Key test cases include:
- Verifying correct application context setup
- Testing activity launch and UI element visibility
- Validating navigation flows between activities (e.g., Login to Register)
- Confirming authentication requirements for protected screens
- Testing user presence functionality

### Testing Environment
- **Default Testing Platform**: API level 34 on the Android Emulator (AVD)
- **Development Environment**: Android Studio IDE
- **Testing Framework**: JUnit4 with Espresso for UI testing
- **Test Execution Flow**: Each test includes proper setup/teardown procedures to ensure Firebase authentication state is reset between tests
- **Testing Phone Number**: +441111111111, +443333333333
- **Testing OTP**: 111111

### Running Tests

#### Setting Up the Android Emulator (AVD) with API 34
1. Open Android Studio and select "More Actions" > "Virtual Device Manager" (or go to Tools > Device Manager)
2. Click "Create Device" to open the Virtual Device Configuration dialog
3. Select a phone device (e.g., Pixel 6) and click "Next"
4. Download and select the system image for API 34 (Android 14):
   - Click the "Download" link next to "UpsideDownCake" (API 34)
   - Once downloaded, select it and click "Next"
5. Configure the AVD settings:
   - Verify the AVD Name (e.g., "Pixel_6_API_34")
   - Adjust any settings if needed (RAM, storage, etc.)
   - Click "Finish" to create the emulator

#### Starting the Emulator and Running Tests
1. Start the emulator by clicking the play button (▶️) in the Device Manager
2. Wait for the emulator to fully boot up (until you see the Android home screen)
3. To run the instrumented tests:
   - In the Project view, navigate to `app/src/androidTest/java/com/example/chatapp/InstrumentedTest.kt`
   - Right-click on the file and select "Run 'InstrumentedTest'"
   - Alternatively, right-click on individual test methods to run specific tests

#### Running Tests via Gradle
You can also run tests via the command line:
1. Open a terminal window
2. Navigate to your project directory
3. Run: `./gradlew connectedAndroidTest`
