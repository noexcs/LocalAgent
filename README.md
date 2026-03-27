# LocalAgent - AI-Powered Android Assistant with Termux Integration

[![Android](https://img.shields.io/badge/Android-13+-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple.svg)](https://kotlinlang.org/)
[![Koog](https://img.shields.io/badge/koog-0.7.3-blue)](https://docs.koog.ai/)
[![Compose](https://img.shields.io/badge/Compose-1.6.0-blue.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

An intelligent Android assistant powered by Koog AI Agent Framework that integrates with Termux to execute shell commands, manage files, and automate tasks through natural language conversations.

## ✨ Features

### 🤖 AI-Powered Chat Interface
- **Natural Language Control**: Interact with your device using conversational AI
- **DeepSeek Integration**: Powered by DeepSeek LLM for intelligent responses
- **Contextual Memory**: Maintains conversation history and persistent memory

### 🔧 Termux Command Execution
- **Background Execution**: Run commands silently and capture output
- **Foreground Execution**: Open interactive terminal sessions
- **Complete Result Handling**: Capture stdout, stderr, and exit codes
- **File Operations**: Read/write files through AI agent tools

### ⏰ Task Scheduling
- **Scheduled Tasks**: Automate command execution at specific times
- **Flexible Frequencies**: Support for daily, weekly, and weekday schedules
- **Boot Persistence**: Automatically reschedule tasks after device reboot
- **Notification Support**: Display task execution status

## 🛠️ Tech Stack

- **Language**: Kotlin 2.3.10
- **UI Framework**: Jetpack Compose + Material 3
- **AI Framework**: Koog Agents 0.7.3

## 📋 Prerequisites

### System Requirements
- **Minimum SDK**: 33 (Android 13)
- **Target SDK**: 36 (Android 15)
- **Compile SDK**: 36

### Required Apps (Install on Device)
1. **Termux** - Terminal emulator (F-Droid or Play Store)
2. **Termux:API** - API integration package

### Build Tools
- Android Studio Hedgehog or later
- Gradle 8.13.2
- JDK 17

## 🚀 Getting Started

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/localagent.git
cd localagent
```

### 2. Setup Termux (On Android Device)

#### Install Termux and Termux:API
Download both from **the same source** (both from F-Droid OR both from Play Store):
- [Termux from F-Droid](https://f-droid.org/packages/com.termux/)
- [Termux:API from F-Droid](https://f-droid.org/packages/com.termux.api/)

#### Configure Termux
Open Termux and run:
```bash
# Update packages
pkg update && pkg upgrade

# Install termux-api
pkg install termux-api

# Grant storage access
termux-setup-storage

# Enable external app integration
echo "allow-external-apps=true" >> ~/.termux/termux.properties
```

#### Grant Permissions
On your Android device:
1. Go to **Settings** → **Apps** → **Termux** → **Permissions**
2. Grant all permissions (Storage, Display over other apps, etc.)
3. Disable battery optimization for Termux

### 4. Build and Install
```bash
./gradlew assembleDebug
```

Install the APK on your device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or use Android Studio's Run button.

## 🎯 Usage Guide

### First Launch
1. Open LocalAgent app
2. Grant `RUN_COMMAND` permission when prompted
3. Navigate to Settings and configure your DeepSeek API key
4. Optionally customize the system prompt

### Chat Interface
1. Type a message in the chat input (e.g., "List all files in home directory")
2. The AI agent will analyze your request
3. It will use appropriate tools (Termux commands, file operations, etc.)
4. View the response with formatted output

### Example Commands
Try these examples:
```
- "Show me the current directory structure"
- "Create a file named test.txt with hello world content"
- "What's my device's IP address?"
- "Run a ping test to google.com"
- "Show me running processes"
- "Backup my important files to /sdcard"
```

### Scheduling Tasks
1. Navigate to Scheduled Tasks from the chat screen
2. Create a new task with:
   - Command to execute
   - Frequency (Daily, Weekly, Weekdays)
   - Time of execution
3. Enable the task
4. The app will automatically execute it at the scheduled time

### Managing Memory
The AI maintains two types of memory:
- **Conversation History**: Chat messages within each session
- **Persistent Memory**: Long-term knowledge updated via the `UpdateMemoryTool`

Example:
```
"Remember that I work as a developer and prefer Python scripts"
```

## 🔐 Security Considerations

⚠️ **Important**: This app grants powerful command execution capabilities. Follow these best practices:

1. **Validate Inputs**: Always review AI-suggested commands before execution
2. **Limited Access**: Only grant necessary permissions to Termux
3. **Sandbox Environment**: Commands run within Termux's sandboxed environment
4. **Audit Logs**: Review command history regularly
5. **API Key Safety**: Never commit your API key to version control

## ⚙️ Configuration

### App Permissions
The app requires the following permissions (declared in `AndroidManifest.xml`):
- `INTERNET` - API calls to DeepSeek
- `ACCESS_NETWORK_STATE` - Network connectivity checks
- `WRITE_EXTERNAL_STORAGE` - File operations (Android ≤ 12)
- `com.termux.permission.RUN_COMMAND` - Termux integration
- `SCHEDULE_EXACT_ALARM` - Task scheduling
- `POST_NOTIFICATIONS` - Task notifications
- `RECEIVE_BOOT_COMPLETED` - Auto-start on boot
- `FOREGROUND_SERVICE_*` - Background task execution

### Customization

#### System Prompt
Customize the AI's behavior in Settings:
```
You are a security-focused Android assistant. Always warn users before 
executing potentially dangerous commands.
```

#### Tool Behavior
Modify tool implementations in `agent/tools/` to:
- Add command whitelisting
- Implement additional safety checks
- Extend functionality

## 🐛 Troubleshooting

### Common Issues

#### "Package not found" Error
**Solution**: Ensure Termux is installed and the `<queries>` tag is present in AndroidManifest.xml

#### Commands Don't Execute
**Solutions**:
- Verify `allow-external-apps=true` in `~/.termux/termux.properties`
- Grant RUN_COMMAND permission to LocalAgent
- Check that Termux has necessary permissions
- Disable battery optimization for Termux

#### Storage Permission Denied
**Solutions**:
- Grant storage permission to Termux
- Run `termux-setup-storage` in Termux
- For Android 11+, use "Allow management of all files"

#### API Calls Fail
**Solutions**:
- Verify your DeepSeek API key is valid
- Check internet connectivity
- Review OkHttp logs for detailed errors


## 📚 Additional Documentation

- [Termux RUN_COMMAND Intent Wiki](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Koog Agents Documentation](https://github.com/JetBrains/koog)

## 🙏 Acknowledgments

- [Termux](https://github.com/termux/termux-app) - Terminal emulator for Android
- [Koog Agents](https://github.com/JetBrains/koog) - AI Agent framework by JetBrains

## 📬 Contact

- **Project Link**: [GitHub Repository](https://github.com/noexcs/LocalAgent)
- **Issue Tracker**: [GitHub Issues](https://github.com/noexcs/LocalAgent/issues)


